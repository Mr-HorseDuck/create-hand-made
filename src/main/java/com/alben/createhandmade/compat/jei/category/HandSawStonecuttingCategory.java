package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.item.ModItems;
import com.simibubi.create.compat.jei.category.BlockCuttingCategory;
import com.simibubi.create.compat.jei.category.BlockCuttingCategory.CondensedBlockCuttingRecipe;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * JEI 类别：手锯 · 切石（T7 批次 3）。
 *
 * <p><b>为什么子类化 Create 的 {@link BlockCuttingCategory} 而不是自己写：</b>切石配方的
 * 数量天然很大（原版 250 条，dev 环境连同 Create 的运行时兼容配方池里共有 577 条 L1），
 * 单个输入最多 16 条；Create 的块切类别已经解决了"一个输入多条产物"的展示问题 ——
 * 它通过 {@code BlockCuttingCategory.condenseRecipes(...)} 把<b>同输入的配方折成一条</b>，
 * 再用 {@code CondensedBlockCuttingRecipe.getCondensedOutputs()} 把产物铺进最多 15 个槽
 * （5×3；第 16 个起并入前面的槽作为同槽多物品）。这些逻辑全部继承，本类<b>不重写
 * {@code setRecipe}</b>，因此不会与 Create 的实现产生漂移。</p>
 *
 * <p><b>本类只做一件事：把界面里的机械锯动画换成手锯。</b>父类的
 * {@code draw} 画的是 {@code AnimatedSaw}（动力锯的 3D 动画），与 Create 自己的
 * {@code create:block_cutting} 类别完全一样；本模组的其它类别都是"工具物品 + 晃动"的观感
 * （见 {@link HandSawCategory}），所以这里只换图标，让两个类别一眼可辨。</p>
 *
 * <p><b>坐标为什么用父类那一套（而不是照抄 {@link HandSawCategory} 的）：</b>两个类别的
 * 布局不同 —— {@code HandSawCategory} 是 177×55、输入槽在 (27,29)、产物在右侧一行；本类别继承
 * 的是 {@link BlockCuttingCategory} 的布局：177×70、输入槽在左上 (5,5)、产物网格占
 * x 78..172 / y 10..66。照抄前者的箭头/图标坐标会导致箭头与流向不符，且手锯图标
 * （按 {@code 背景宽/2 − 16} 算出来是 x≈72，scale 2 后向右延伸到 ≈104）会压在产物网格上。
 * 所以这里保留父类的箭头与阴影坐标，只把 {@code saw.draw(graphics, 33, 37)} 换成手锯图标。</p>
 *
 * <p><b>门控与 L2 自动继承：</b>配方来源是 {@code HandMadeRecipePool}（见
 * {@code CreateHandMadeJEI.collectHandSawStonecuttingRecipes}），而池第一步就是 Create 配置
 * {@code allowStonecuttingOnSaw} 的门、最后一步是 L2 过滤 —— 配置关掉时收集器返回空列表，
 * JEI 不显示没有配方的类别，所以这里不需要额外的 {@code enableWhen}。</p>
 */
@ParametersAreNonnullByDefault
public class HandSawStonecuttingCategory extends BlockCuttingCategory {

    /** 振动幅度（像素）—— 与 {@link HandSawCategory} 保持一致。 */
    private static final float SHAKE_AMPLITUDE = 2.5f;

    /** 振动频率（tick 周期越小越快）—— 与 {@link HandSawCategory} 保持一致。 */
    private static final float SHAKE_SPEED = 0.4f;

    /** 手锯图标位置：在父类机械锯动画坐标（33, 37）的基础上，
     *  上移半个物品、左移 1/4 个物品（scale(2) 后物品占 32px）。 */
    private static final float SAW_X = 25f;   // 33 - 8（= 1/4 × 32）
    private static final float SAW_Y = 21f;   // 37 - 16（= 1/2 × 32）

    public HandSawStonecuttingCategory(Info<CondensedBlockCuttingRecipe> info) {
        super(info);
    }

    // setRecipe 不重写 —— 15 槽折叠 + 5×3 摆放全部走 BlockCuttingCategory

    @Override
    public void draw(CondensedBlockCuttingRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        // 箭头与阴影沿用父类坐标（它们是按 177×70 + 左上输入槽的布局摆的）
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 31, 6);
        AllGuiTextures.JEI_SHADOW.render(graphics, 33 - 17, 37 + 13);

        // ★ 沿 XY 斜角（45°）方向的微小振动（照抄 HandSawCategory.draw）
        float partialTicks = AnimationTickHolder.getPartialTicks();
        int ticks = AnimationTickHolder.getTicks();
        // 一个 sin 值同时驱动 X 和 Y，保证沿 45° 直线振动
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
