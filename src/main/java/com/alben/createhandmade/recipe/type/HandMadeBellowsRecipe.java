package com.alben.createhandmade.recipe.type;

import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * 风箱的 L3 独占配方类，服务 create_hand_made:bellows_recipe 类型。
 *
 * fan_type 用 ResourceLocation，支持任意第三方鼓风类型。
 * 必须是带命名空间的完整 id（如 create:blasting、dndesires:sanding）。
 */
public class HandMadeBellowsRecipe extends ProcessingRecipe<RecipeWrapper> {

    @Nullable
    private ResourceLocation fanType;

    public HandMadeBellowsRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.BELLOWS_RECIPE_INFO, params);
    }

    @Override
    public boolean matches(RecipeWrapper inv, Level level) {
        if (ingredients.isEmpty() || inv.getContainerSize() < 1) return false;
        if (inv.isEmpty()) return false;
        return ingredients.get(0).test(inv.getItem(0));
    }

    @Override protected int getMaxInputCount() { return 1; }
    @Override protected int getMaxOutputCount() { return 12; }
    @Override protected boolean canSpecifyDuration() { return false; }
    @Override protected int getMaxFluidInputCount() { return 0; }

    @Nullable public ResourceLocation getFanType() { return fanType; }
    public void setFanType(@Nullable ResourceLocation fanType) { this.fanType = fanType; }
}