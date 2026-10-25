package com.alben.createhandmade.client;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.item.HandSawItem;
import com.alben.createhandmade.item.HandSawRecipeCache;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 手锯 HUD：主手手锯 + 副手有原料时，在屏幕中央偏上画一排「能切出什么」的产物网格。
 *
 * <h3>数据从哪来（26.1 与 1.21.1 最大的差别）</h3>
 * <p>旧版直接查客户端配方表；26.1 配方表只在服务端，所以改成读服务端推来的
 * {@link HandSawRecipeCache}（见 {@code HandSawItem#syncHudData}）。缓存里没有当前副手物品的数据时
 * 干脆不画 —— HUD 是纯展示，晚一两 tick 出现完全无所谓。</p>
 *
 * <h3>注册方式</h3>
 * <p>NeoForge 的 {@code RenderGuiEvent.Post} 在 Fabric 侧对应
 * {@code HudElementRegistry.addLast}（{@link HudElement#extractRenderState} 取代了
 * 1.21 的 {@code GuiGraphics} 即时绘制；26.1 已经没有 {@code GuiGraphics} 这个类了）。</p>
 *
 * <h3>布局（沿用旧版实测值）</h3>
 * <ul>
 *   <li>槽 18×18（原版 {@code container/slot} 贴图），间距 4 ⇒ 步长 22；</li>
 *   <li>每行最多 9 个、最多 4 行 = 36 个格位，整体水平居中；</li>
 *   <li><b>最下面一行</b>固定在屏幕高度的 1/3 处（多行时往上长）；</li>
 *   <li>配方数 &gt; 36 时不再截断，而是让 36 个格位变成一扇<b>滑动窗口</b>，
 *       两端用 {@code +N} 指示还有多少被挡住；</li>
 *   <li>选中项画一圈金橙色外框。</li>
 * </ul>
 */
public final class HandSawHudRenderer implements HudElement {

    /** 原版槽位贴图（18×18，含 1px 边框）。26.1 起是 GUI 图集里的一张 sprite。 */
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 4;
    /** 物品在槽内的内边距（(18-16)/2 = 1）。 */
    private static final int ITEM_INSET = 1;

    /** 槽间距步长（一个槽占的横向/纵向空间）。行距与列距共用它。 */
    private static final int SLOT_STEP = SLOT_SIZE + SLOT_GAP;

    private static final int MAX_COLS = 9;
    private static final int MAX_ROWS = 4;
    /** 网格总容量（也是滑动窗口的格位总数：4 行 × 9 列）。 */
    private static final int MAX_SLOTS = MAX_COLS * MAX_ROWS;

    /** 选中槽位的高亮颜色（金橙色，ARGB）。 */
    private static final int HIGHLIGHT_COLOR = 0xFFFFD966;

    /**
     * 滑动窗口指示符（"+N"）的文字颜色：灰色。
     *
     * <p>用不带 alpha 的 24 位写法 —— 原版画数量角标传的也是 {@code 16777215}（同样 0xRRGGBB），
     * 字体批次渲染不使用颜色的 alpha 通道。</p>
     */
    private static final int MORE_TEXT_COLOR = 0xAAAAAA;

    /** 指示符文字相对所在格位槽顶的垂直偏移，与槽内物品图标垂直居中。 */
    private static final int MORE_TEXT_Y_OFFSET = 5;

    /** 指示符的语言键（值是 "+%s"）。 */
    private static final String MORE_LANG_KEY = "hud.create_hand_made.hand_saw.more";

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 只第一人称显示
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return;

        // 主手必须拿手锯
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof HandSawItem)) return;

        // 副手必须有物品
        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return;

        // 服务端推来的「这件副手物品能切出什么」；没同步到 / 没配方就不画
        if (!HandSawRecipeCache.matches(off)) return;
        List<ItemStack> outputs = HandSawRecipeCache.outputs();
        if (outputs.isEmpty()) return;

        // 读取当前选中索引
        int index = mainHand.getOrDefault(ModDataComponents.HAND_SAW_RECIPE_INDEX, 0);
        if (index < 0 || index >= outputs.size()) index = 0;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int total = outputs.size();

        // ★ 滑动窗口：配方数超过 MAX_SLOTS 时不再截断，而是让 36 个格位变成一扇滑动的窗。
        //   total ≤ 36 时退化成"窗口 = 全部配方、两端都没有指示符"。
        //
        //   两遍计算：先假设只有一端溢出（35 槽）推出指示符个数，再用"实际的指示符个数"
        //   重算窗口。total > 36 时第一遍必然至少算出 1 个指示符，所以第二遍的窗口只会是
        //   34 或 35 槽（两端都溢出 / 只有一端溢出）——不会退化成 36 槽那种装不下的情况。
        int start;
        int visibleSlots;
        if (total <= MAX_SLOTS) {
            start = 0;
            visibleSlots = total;
        } else {
            int guessedSlots = MAX_SLOTS - 1;                       // 35：先假设只有一端溢出
            int guessedStart = clamp(index - (guessedSlots - 1) / 2, 0,
                    Math.max(0, total - guessedSlots));
            int guessedIndicators = (guessedStart > 0 ? 1 : 0)
                    + (guessedStart + guessedSlots < total ? 1 : 0);

            visibleSlots = MAX_SLOTS - guessedIndicators;           // 35 或 34
            start = clamp(index - (visibleSlots - 1) / 2, 0, Math.max(0, total - visibleSlots));
        }
        int end = start + visibleSlots;

        // 两端的指示符：格位 0 = "前面还藏着 start 个"，格位 MAX_SLOTS−1 = "后面还藏着 total − end 个"
        int beforeCount = start > 0 ? 1 : 0;
        int afterCount = end < total ? 1 : 0;

        // 窗口内真正要画的槽数
        int slotsToDraw = Math.min(end - start, MAX_SLOTS - beforeCount - afterCount);

        // 行/列基准：按"实际占用的列数"居中，所有行都从这个左边距开始排（所以各槽横向对齐）。
        int rows = (slotsToDraw + MAX_COLS - 1) / MAX_COLS;
        int cols = Math.min(MAX_COLS, slotsToDraw);
        int gridWidth = cols * SLOT_SIZE + (cols - 1) * SLOT_GAP;
        int leftX = (screenWidth - gridWidth) / 2;

        // 最下面一行的 y。第 r 行在其上方 (rows−1−r) 个行距处。
        int baselineY = screenHeight / 3;

        Font font = mc.font;

        // 前指示符占格位 0
        if (beforeCount == 1) {
            drawIndicator(graphics, font, gridX(leftX, 0), gridY(baselineY, rows, 0), start);
        }

        // 中间格位：窗口内的槽按顺序铺开（选中项一定落在 [start, end) 内）
        for (int k = 0; k < slotsToDraw; k++) {
            int cell = beforeCount + k;
            int slot = start + k;
            renderSlot(graphics, font, outputs.get(slot),
                    gridX(leftX, cell), gridY(baselineY, rows, cell), slot == index);
        }

        // 后指示符占格位 MAX_SLOTS − 1
        if (afterCount == 1) {
            int cell = MAX_SLOTS - 1;
            drawIndicator(graphics, font, gridX(leftX, cell), gridY(baselineY, rows, cell), total - end);
        }
    }

    /** 把 {@code value} 夹到 {@code [min, max]}。 */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /** 格位索引（0..MAX_SLOTS−1）→ 屏幕 x。 */
    private static int gridX(int leftX, int cell) {
        return leftX + (cell % MAX_COLS) * SLOT_STEP;
    }

    /** 格位索引（0..MAX_SLOTS−1）→ 屏幕 y。第 r 行在 baselineY 上方 (rows−1−r) 个行距处。 */
    private static int gridY(int baselineY, int rows, int cell) {
        return baselineY - (rows - 1 - cell / MAX_COLS) * SLOT_STEP;
    }

    /**
     * 画一个滑动窗口指示符：{@code +N}（N = 该方向被窗口挡住的配方数）。
     *
     * <p>水平方向与所在格位的<b>槽中心</b>对齐（不是网格边界），所以文字比一个格位宽时
     * 会向左右均摊溢出，而不会挤压相邻格位。</p>
     */
    private static void drawIndicator(GuiGraphicsExtractor graphics, Font font, int cellX, int cellY,
                                      int hiddenCount) {
        String text = Component.translatable(MORE_LANG_KEY, hiddenCount).getString();
        int x = cellX + SLOT_SIZE / 2 - font.width(text) / 2;
        int y = cellY + MORE_TEXT_Y_OFFSET;
        graphics.text(font, text, x, y, MORE_TEXT_COLOR, true);
    }

    private static void renderSlot(GuiGraphicsExtractor graphics, Font font, ItemStack stack,
                                   int x, int y, boolean selected) {
        // 原版槽位贴图
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, y, SLOT_SIZE, SLOT_SIZE);

        // 物品图标
        int itemX = x + ITEM_INSET;
        int itemY = y + ITEM_INSET;
        graphics.item(stack, itemX, itemY);

        // ★ 数量角标（原版物品栏风格）：直接交给原版 API，坐标与 item() 完全一致。
        //   它只在 stack.getCount() != 1 时才画数量，与需求「只在 count > 1 时显示」一致
        //   （空产物在服务端收集时就被跳过了）。耐久条 / 冷却遮罩同样只在原版各自条件成立时才画，
        //   而配方产物都是新建的 ItemStack，所以常态下不会出现。
        graphics.itemDecorations(font, stack, itemX, itemY);

        // 选中槽位：画一圈金色高亮边框（贴在槽外沿 1px 处）
        if (selected) {
            graphics.outline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, HIGHLIGHT_COLOR);
        }
    }
}
