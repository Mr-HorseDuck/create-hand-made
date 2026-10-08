package com.alben.createhandmade.client;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.item.HandSawItem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
public class HandSawHudRenderer {

    /** 原版槽位贴图（18×18，含 1px 边框） */
    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/slot");

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 4;
    private static final int ITEM_INSET = 1;   // 物品在槽内的内边距（(18-16)/2 = 1）

    /** 槽间距步长（一个槽占的横向/纵向空间）。行距与列距共用它。 */
    private static final int SLOT_STEP = SLOT_SIZE + SLOT_GAP;   // 22

    /** 每行最多几个槽。 */
    private static final int MAX_COLS = 9;

    /** 最多几行。 */
    private static final int MAX_ROWS = 4;

    /** 网格总容量（也是滑动窗口的格位总数：4 行 × 9 列）。 */
    private static final int MAX_SLOTS = MAX_COLS * MAX_ROWS;    // 36

    /** 选中槽位的高亮颜色（金橙色，ARGB） */
    private static final int HIGHLIGHT_COLOR = 0xFFFFD966;

    /**
     * 滑动窗口指示符（"+N"）的文字颜色：灰色。
     *
     * <p>用不带 alpha 的 24 位写法 —— 原版 {@code GuiGraphics#renderItemDecorations} 画数量角标
     * 传的就是 {@code 16777215}（同样是 0xRRGGBB），字体批次渲染不使用颜色的 alpha 通道。</p>
     */
    private static final int MORE_TEXT_COLOR = 0xAAAAAA;

    /** 指示符文字相对所在格位槽顶的垂直偏移，与槽内物品图标垂直居中（图标 1..17，字形 5..13）。 */
    private static final int MORE_TEXT_Y_OFFSET = 5;

    /** 指示符的语言键（值是 "+%s"，见 assets/create_hand_made/lang/{en_us,zh_cn}.json）。 */
    private static final String MORE_LANG_KEY = "hud.create_hand_made.hand_saw.more";

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
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

        // 查切削配方
        Level level = player.level();
        List<RecipeHolder<? extends Recipe<?>>> recipes = HandSawItem.getCuttingRecipes(level, off);
        if (recipes.isEmpty()) return;

        // 收集产物（跳过空结果）
        List<ItemStack> outputs = new ArrayList<>();
        for (RecipeHolder<? extends Recipe<?>> holder : recipes) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (!result.isEmpty()) outputs.add(result);
        }
        if (outputs.isEmpty()) return;

        // 读取当前选中索引
        int index = mainHand.getOrDefault(ModDataComponents.HAND_SAW_RECIPE_INDEX.get(), 0);
        if (index < 0 || index >= outputs.size()) index = 0;

        // 计算位置：多行网格，整体水平居中；垂直方向上"最下面一行"固定在屏幕 1/3 处
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int total = outputs.size();

        // ★ 滑动窗口：配方数超过 MAX_SLOTS 时不再截断，而是让 36 个格位变成一扇滑动的窗。
        //   total ≤ 36 时退化成"窗口 = 全部配方、两端都没有指示符"，与上一版逐像素一致。
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

        // 窗口内真正要画的槽数。两遍计算已经保证它等于 36 − 指示符数，这里再取一次 min 只是防御。
        int slotsToDraw = Math.min(end - start, MAX_SLOTS - beforeCount - afterCount);

        // 行/列基准：按"实际占用的列数"居中，所有行都从这个左边距开始排（所以各槽横向对齐）。
        //   - total ≤ 36 且单行（total ≤ 9）：cols = total → gridWidth 与改造前的公式逐字相同，
        //     单行的水平位置完全不变；
        //   - 多行（total ≥ 10）：cols = 9 → 9×18 + 8×4 = 194px；
        //   - 滑动窗口：slotsToDraw ≥ 34 → cols = 9 → 同样是 194px。
        int rows = (slotsToDraw + MAX_COLS - 1) / MAX_COLS;
        int cols = Math.min(MAX_COLS, slotsToDraw);
        int gridWidth = cols * SLOT_SIZE + (cols - 1) * SLOT_GAP;
        int leftX = (screenWidth - gridWidth) / 2;

        // 最下面一行的 y（= 改造前单行的 y）。第 r 行在其上方 (rows−1−r) 个行距处。
        int baselineY = screenHeight / 3;

        // 前指示符占格位 0
        if (beforeCount == 1) {
            drawIndicator(graphics, mc.font, gridX(leftX, 0), gridY(baselineY, rows, 0), start);
        }

        // 中间格位：窗口内的槽按顺序铺开（选中项一定落在 [start, end) 内）
        for (int k = 0; k < slotsToDraw; k++) {
            int cell = beforeCount + k;
            int slot = start + k;
            renderSlot(graphics, mc.font, outputs.get(slot),
                    gridX(leftX, cell), gridY(baselineY, rows, cell), slot == index);
        }

        // 后指示符占格位 MAX_SLOTS − 1
        if (afterCount == 1) {
            int cell = MAX_SLOTS - 1;
            drawIndicator(graphics, mc.font, gridX(leftX, cell), gridY(baselineY, rows, cell), total - end);
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
     * 会向左右均摊溢出，而不会挤压相邻格位（{@code drawString} 不裁剪）。</p>
     */
    private static void drawIndicator(GuiGraphics graphics, Font font, int cellX, int cellY, int hiddenCount) {
        String text = Component.translatable(MORE_LANG_KEY, hiddenCount).getString();
        int x = cellX + SLOT_SIZE / 2 - font.width(text) / 2;
        int y = cellY + MORE_TEXT_Y_OFFSET;
        graphics.drawString(font, text, x, y, MORE_TEXT_COLOR, true);
    }

    private static void renderSlot(GuiGraphics graphics, Font font, ItemStack stack,
                                   int x, int y, boolean selected) {
        // 原版槽位贴图
        graphics.blitSprite(SLOT_SPRITE, x, y, SLOT_SIZE, SLOT_SIZE);

        // 物品图标
        int itemX = x + ITEM_INSET;
        int itemY = y + ITEM_INSET;
        graphics.renderItem(stack, itemX, itemY);

        // ★ 数量角标（原版物品栏风格）：直接交给原版 API，坐标与 renderItem 完全一致。
        //   GuiGraphics#renderItemDecorations 只在 stack.getCount() != 1 时才画数量
        //   （GuiGraphics.java:1337-1341：位置 x + 17 - font.width(s)、y + 9，白色 + 阴影，
        //   z 抬到 +200），与需求「只在 count > 1 时显示」完全一致 —— 空产物已在上面
        //   收集产物时被跳过，所以这里不需要再判一次。
        //   它同时会处理耐久条与物品冷却遮罩，两者都只在原版各自的条件成立时才画：
        //   耐久条要求 stack.isBarVisible()（= isDamageableItem() && damage > 0，
        //   Item.java:188-190 / ItemStack.java:444-446），而配方产物都是新建的 ItemStack
        //   （damage = 0），所以常态下不会出现。刻意不加"只画数量"的裁剪 —— 那等于自己
        //   重写一遍原版逻辑，反而会丢掉第三方 ItemDecorator。
        graphics.renderItemDecorations(font, stack, itemX, itemY);

        // 选中槽位：画一圈金色高亮边框（贴在槽外沿 1px 处）
        if (selected) {
            graphics.renderOutline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, HIGHLIGHT_COLOR);
        }
    }
}