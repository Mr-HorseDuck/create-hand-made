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
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class HandPressBasinCategory extends BasinCategory {

    private static final float SHAKE_AMPLITUDE = 6f;
    private static final float SHAKE_SPEED = 0.15f;

    private final AnimatedBlazeBurner heater = new AnimatedBlazeBurner();
    private final PackingType type;

    public enum PackingType {
        COMPACTING, AUTO_SQUARE
    }

    // ================= 静态工厂 =================

    public static HandPressBasinCategory standard(Info<BasinRecipe> info) {
        return new HandPressBasinCategory(info, PackingType.COMPACTING);
    }

    public static HandPressBasinCategory autoSquare(Info<BasinRecipe> info) {
        return new HandPressBasinCategory(info, PackingType.AUTO_SQUARE);
    }

    // ================= 构造 =================

    protected HandPressBasinCategory(Info<BasinRecipe> info, PackingType type) {
        // COMPACTING 需要加热条，AUTO_SQUARE 不需要
        super(info, type == PackingType.COMPACTING);
        this.type = type;
    }

    // ================= setRecipe =================

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BasinRecipe recipe, IFocusGroup focuses) {
        if (type == PackingType.COMPACTING) {
            // 打包：走 BasinCategory 通用布局（含加热槽）
            super.setRecipe(builder, recipe, focuses);
            return;
        }

        // AUTO_SQUARE：Create 原版 4/9 合 1 布局
        int i = 0;
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int size = ingredients.size();
        int rows = size == 4 ? 2 : 3;
        while (i < size) {
            Ingredient ingredient = ingredients.get(i);
            builder
                    .addSlot(RecipeIngredientRole.INPUT,
                            (rows == 2 ? 27 : 18) + (i % rows) * 19,
                            51 - (i / rows) * 19)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addIngredients(ingredient);
            i++;
        }

        builder
                .addSlot(RecipeIngredientRole.OUTPUT, 142, 51)
                .setBackground(getRenderedSlot(), -1, -1)
                .addItemStack(getResultItem(recipe));
    }

    // ================= draw =================

    @Override
    public void draw(BasinRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        if (type == PackingType.COMPACTING) {
            // 打包：走 BasinCategory 通用绘制（阴影 + 箭头 + 加热条）
            super.draw(recipe, slotsView, graphics, mouseX, mouseY);
        } else {
            // AUTO_SQUARE：只有箭头 + 阴影
            AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 136, 32);
            AllGuiTextures.JEI_SHADOW.render(graphics, 81, 68);
        }

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

        // 烈焰燃烧器（配方需要加热时才画）
        HeatCondition requiredHeat = recipe.getRequiredHeat();
        if (requiredHeat != HeatCondition.NONE) {
            heater.withHeat(requiredHeat.visualizeAsBlazeBurner())
                    .draw(graphics, getBackground().getWidth() / 2 + 3, 55);
        }

        // 锤子：上下砸击动画
        float partialTicks = AnimationTickHolder.getPartialTicks();
        int ticks = AnimationTickHolder.getTicks();
        float impact = (float) Math.abs(Math.sin((ticks + partialTicks) * SHAKE_SPEED));
        float dy = impact * SHAKE_AMPLITUDE;

        ItemStack hammerStack = new ItemStack(ModItems.PRESS_HAMMER.get());
        GuiGameElement.of(hammerStack)
                .<GuiGameElement.GuiRenderBuilder>at(
                        getBackground().getWidth() / 2f + 3,
                        15 + dy,
                        200)
                .scale(2)
                .render(graphics);
    }
}