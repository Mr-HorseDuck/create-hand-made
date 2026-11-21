package com.alben.createhandmade.compat.jei.category.sequenced;

import com.alben.createhandmade.recipe.HandMadeAssembly;
import com.alben.createhandmade.recipe.HandMadeAssemblyRecipe;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * 序列组装 JEI 步骤的「本模组」子类别：在步骤列里画一个归属手工工具的图标。
 *
 * <p>Create 的序列组装类别为每一步画一个 25px 宽的小列（{@link SequencedAssemblySubCategory}），
 * 内置实现画的是压床 / 注液器 / 机械锯 / 部署器的动画模型。我们的步骤由手搓工具完成，
 * 没有对应机器，所以这里画工具物品本身，直观表达「这一步要拿这个工具手搓」。</p>
 *
 * <p><b>客户端专用</b>：只在 JEI 建索引与绘制时（客户端）被加载，
 * 由 {@link HandMadeAssemblyRecipe#getJEISubCategory()} 返回的 supplier 构造。</p>
 *
 * <p>★ 移植自 1.21.1 分支（0.7.0-beta）；1.20.1 的 {@code PoseStack} 用法与 1.21 一致。</p>
 */
public class HandMadeAssemblySubCategory extends SequencedAssemblySubCategory {

    public HandMadeAssemblySubCategory() {
        super(25);
    }

    @Override
    public void draw(SequencedRecipe<?> recipe, GuiGraphics graphics, double mouseX, double mouseY, int index) {
        if (!(recipe.getAsAssemblyRecipe() instanceof HandMadeAssemblyRecipe handmade)) {
            return;
        }
        ItemLike toolItem = HandMadeAssembly.toolItem(handmade.getTool());
        if (toolItem == null) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // 与 Create 内置子类保持同一水平带（它们 translate(-5~-7, 50) 后缩放画动画）
        pose.translate(4, 50, 0);
        graphics.renderItem(new ItemStack(toolItem), 0, 0);
        pose.popPose();
    }
}