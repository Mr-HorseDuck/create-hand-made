package com.alben.createhandmade.recipe.type;

import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * 应用家族的 L3 独占配方，服务指杆（ToolType.POINTER）。
 *
 * 继承 ItemApplicationRecipe —— 2 槽 matches、keepHeldItem、shouldKeepHeldItem
 * 全部沿用父类（父类通过 readAdditional / writeAdditional 钩子自动序列化 keepHeldItem）。
 *
 * 构造必须传一个 AllRecipeTypes 枚举（父类唯一 public 构造要求），
 * 传 ITEM_APPLICATION（唯一合理值）；getType() 覆写为独立 type。
 */
public class HandMadeApplicationRecipe extends ItemApplicationRecipe
        implements HandMadeToolRecipeLike {

    @Nullable
    private ToolType tool;

    public HandMadeApplicationRecipe(ProcessingRecipeParams params) {
        super(AllRecipeTypes.ITEM_APPLICATION, params);
    }

    @Override
    public RecipeType<?> getType() {
        return HandMadeRecipeTypes.TOOL_RECIPE.get();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HandMadeRecipeTypes.TOOL_SERIALIZER.get();
    }

    @Nullable @Override public ToolType getTool() { return tool; }
    @Override public void setTool(@Nullable ToolType tool) { this.tool = tool; }
}