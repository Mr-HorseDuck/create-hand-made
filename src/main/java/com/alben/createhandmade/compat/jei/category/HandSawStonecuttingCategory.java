package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.registry.ModItems;
import com.simibubi.create.compat.jei.category.BlockCuttingCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * JEI 类别：手锯 · 切石。
 *
 * <p>子类化 Create 的 {@link BlockCuttingCategory}，继承它"同输入折叠 + 15 槽铺开"
 * 的全部展示逻辑（不重写 {@code setRecipe}），只把界面里的机械锯动画换成手锯图标，
 * 让本类别与 {@link HandSawCategory} 的观感一致。</p>
 *
 * <p>箭头与阴影沿用父类坐标（父类布局是 177×70、输入槽在左上 (5,5)、产物网格占
 * x 78..172 / y 10..66）。手锯图标位置在父类机械锯动画坐标（33, 37）基础上，
 * 上移半个物品、左移 1/4 个物品（scale(2) 后物品占 32px）。</p>
 */
@ParametersAreNonnullByDefault
public class HandSawStonecuttingCategory extends BlockCuttingCategory {

    /** 振动幅度（像素）—— 与 HandSawCategory 保持一致。 */
    private static final float SHAKE_AMPLITUDE = 2.5f;

    /** 振动频率（tick 周期越小越快）—— 与 HandSawCategory 保持一致。 */
    private static final float SHAKE_SPEED = 0.4f;

    /** 手锯图标位置（相对父类机械锯动画坐标 (33, 37) 的偏移） */
    private static final float SAW_X = 25f;
    private static final float SAW_Y = 21f;

    public HandSawStonecuttingCategory(Info<CondensedBlockCuttingRecipe> info) {
        super(info);
    }

    // setRecipe 不重写 —— 15 槽折叠 + 5×3 摆放全部走 BlockCuttingCategory

    @Override
    public void draw(CondensedBlockCuttingRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        // 箭头与阴影沿用父类坐标
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 31, 6);
        AllGuiTextures.JEI_SHADOW.render(graphics, 33 - 17, 37 + 13);

        // ★ 沿 XY 斜角（45°）方向的微小振动
        float partialTicks = AnimationTickHolder.getPartialTicks();
        int ticks = AnimationTickHolder.getTicks();
        float shake = (float) Math.sin((ticks + partialTicks) * SHAKE_SPEED) * SHAKE_AMPLITUDE;

        // 45° 单位向量 = (cos45, sin45) = (0.7071, 0.7071)
        float dx = shake * -0.7071f;
        float dy = shake * 0.7071f;

        ItemStack sawStack = new ItemStack(ModItems.HAND_SAW.get());
        GuiGameElement.of(sawStack)
                .<GuiGameElement.GuiRenderBuilder>at(SAW_X + dx, SAW_Y + dy, 0)
                .scale(2)
                .render(graphics);
    }
}