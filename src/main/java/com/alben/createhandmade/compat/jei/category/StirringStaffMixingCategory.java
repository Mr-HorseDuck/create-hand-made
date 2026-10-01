package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.category.BasinCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedBlazeBurner;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class StirringStaffMixingCategory extends BasinCategory {

    private final AnimatedBlazeBurner heater = new AnimatedBlazeBurner();
    private final MixingType type;

    public enum MixingType {
        MIXING, AUTO_SHAPELESS, AUTO_BREWING
    }

    // ================= 静态工厂 =================

    public static StirringStaffMixingCategory standard(Info<BasinRecipe> info) {
        return new StirringStaffMixingCategory(info, MixingType.MIXING);
    }

    public static StirringStaffMixingCategory autoShapeless(Info<BasinRecipe> info) {
        return new StirringStaffMixingCategory(info, MixingType.AUTO_SHAPELESS);
    }

    public static StirringStaffMixingCategory autoBrewing(Info<BasinRecipe> info) {
        return new StirringStaffMixingCategory(info, MixingType.AUTO_BREWING);
    }

    // ================= 构造 =================

    protected StirringStaffMixingCategory(Info<BasinRecipe> info, MixingType type) {
        // AUTO_SHAPELESS 不显示加热条（跟 Create 一致），其他两类显示
        super(info, type != MixingType.AUTO_SHAPELESS);
        this.type = type;
    }

    // setRecipe 不重写——AUTO_SHAPELESS / AUTO_BREWING 走 BasinCategory 通用布局

    // ================= draw =================

    @Override
    public void draw(BasinRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        // 三类都走 BasinCategory 通用绘制
        super.draw(recipe, slotsView, graphics, mouseX, mouseY);

        // 工作盆
        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(getBackground().getWidth() / 2f + 3, 34, 100);
        ms.mulPose(Axis.XP.rotationDegrees(-15.5f));
        ms.mulPose(Axis.YP.rotationDegrees(22.5f));

        GuiGameElement.of(AllBlocks.BASIN.getDefaultState())
                .lighting(AnimatedKinetics.DEFAULT_LIGHTING)
                .atLocal(0, 1.65, 0)
                .scale(23)
                .render(graphics);

        ms.popPose();

        // 烈焰燃烧器
        HeatCondition requiredHeat = recipe.getRequiredHeat();
        if (requiredHeat != HeatCondition.NONE) {
            heater.withHeat(requiredHeat.visualizeAsBlazeBurner())
                    .draw(graphics, getBackground().getWidth() / 2 + 3, 55);
        }

        // 搅拌杖：上下摇晃
        float partialTicks = AnimationTickHolder.getPartialTicks();
        int ticks = AnimationTickHolder.getTicks();
        float dy = (float) Math.sin((ticks + partialTicks) * 0.4f) * 3f;

        ItemStack staffStack = new ItemStack(ModItems.STIRRING_STAFF.get());
        GuiGameElement.of(staffStack)
                .<GuiGameElement.GuiRenderBuilder>at(
                        getBackground().getWidth() / 2f + 3,
                        15 + dy,
                        200)
                .scale(2)
                .render(graphics);
    }
}