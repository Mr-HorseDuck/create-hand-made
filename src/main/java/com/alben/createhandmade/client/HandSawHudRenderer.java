package com.alben.createhandmade.client;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.item.HandSawItem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = CreateHandMade.MODID, value = Dist.CLIENT)
public class HandSawHudRenderer {

    /** 原版槽位贴图（18×18，含 1px 边框） */
    private static final ResourceLocation SLOT_SPRITE =
        new ResourceLocation(CreateHandMade.MODID, "textures/gui/slot.png");

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 4;
    private static final int ITEM_INSET = 1;

    /** 选中槽位的高亮颜色（金橙色，ARGB） */
    private static final int HIGHLIGHT_COLOR = 0xFFFFD966;

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
        List<Recipe<?>> recipes = HandSawItem.getCuttingRecipes(level, off);
        if (recipes.isEmpty()) return;

        // 收集产物（跳过空结果）
        List<ItemStack> outputs = new ArrayList<>();
        for (Recipe<?> recipe : recipes) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.isEmpty()) outputs.add(result);
        }
        if (outputs.isEmpty()) return;

        // 读取当前选中索引（NBT）
        int index = mainHand.getOrCreateTag().getInt(HandSawItem.NBT_RECIPE_INDEX);
        if (index < 0 || index >= outputs.size()) index = 0;

        // 计算位置：水平居中，垂直方向 1/3 处
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int count = outputs.size();
        int totalWidth = count * SLOT_SIZE + (count - 1) * SLOT_GAP;
        int startX = (screenWidth - totalWidth) / 2;
        int startY = screenHeight / 3;

        // 绘制每个槽
        for (int i = 0; i < count; i++) {
            int x = startX + i * (SLOT_SIZE + SLOT_GAP);
            boolean selected = (i == index);
            renderSlot(graphics, outputs.get(i), x, startY, selected);
        }
    }

    private static void renderSlot(GuiGraphics graphics, ItemStack stack,
                                   int x, int y, boolean selected) {
        // 原版槽位贴图（1.20.1 传统 blit）
        graphics.blit(SLOT_SPRITE, x, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);

        // 物品图标
        graphics.renderItem(stack, x + ITEM_INSET, y + ITEM_INSET);

        // 选中槽位：画一圈金色高亮边框
        if (selected) {
            drawOutline(graphics, x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, HIGHLIGHT_COLOR);
        }
    }

    /** 用四次 fill 模拟 1px 边框 */
    private static void drawOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }
}