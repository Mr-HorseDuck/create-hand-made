package com.alben.createhandmade.recipe.serializer;

import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.recipe.type.*;
import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * create_hand_made:tool_recipe 的 serializer。
 *
 * 按 JSON 里的 tool 字段，把解析分派到对应家族的 ProcessingRecipeSerializer。
 * 委托完成后写入 tool 归属。
 */
public class HandMadeToolRecipeSerializer
        implements RecipeSerializer<ProcessingRecipe<RecipeWrapper>> {

    private static final String TOOL_FIELD = "tool";

    /** tool → 家族 delegate */
    private static final Map<ToolType, ProcessingRecipeSerializer<?>> DELEGATES = new HashMap<>();

    static {
        DELEGATES.put(ToolType.HAND_SAW,
                new ProcessingRecipeSerializer<>(HandMadeCuttingRecipe::new));
        DELEGATES.put(ToolType.PRESS_HAMMER_DEPOT,
                new ProcessingRecipeSerializer<>(HandMadePressingRecipe::new));
        // 碾磨家族：研钵 + 碾钵共用同一个配方类
        DELEGATES.put(ToolType.MORTAR,
                new ProcessingRecipeSerializer<>(HandMadeCrushingRecipe::new));
        DELEGATES.put(ToolType.CRUSHER_MORTAR,
                new ProcessingRecipeSerializer<>(HandMadeCrushingRecipe::new));
        // 注液家族：灌注枪
        DELEGATES.put(ToolType.INFUSION_GUN,
                new ProcessingRecipeSerializer<>(HandMadeFillingRecipe::new));
        // 应用家族：指杆
        DELEGATES.put(ToolType.POINTER,
                new ProcessingRecipeSerializer<>(HandMadeApplicationRecipe::new));
        // ★ 工作盆家族：冲压锤 · 工作盆 + 搅拌杖
        DELEGATES.put(ToolType.PRESS_HAMMER_BASIN,
                new ProcessingRecipeSerializer<>(HandMadeToolRecipe::new));
        DELEGATES.put(ToolType.STIRRING_STAFF,
                new ProcessingRecipeSerializer<>(HandMadeToolRecipe::new));
    }

    @SuppressWarnings("unchecked")
    private static ProcessingRecipeSerializer<ProcessingRecipe<RecipeWrapper>> delegateFor(ToolType tool) {
        return (ProcessingRecipeSerializer<ProcessingRecipe<RecipeWrapper>>) DELEGATES.get(tool);
    }

    @Override
    public ProcessingRecipe<RecipeWrapper> fromJson(ResourceLocation id, JsonObject json) {
        ToolType tool = readToolOrThrow(json);

        ProcessingRecipeSerializer<ProcessingRecipe<RecipeWrapper>> delegate = delegateFor(tool);
        if (delegate == null) {
            throw new IllegalArgumentException(
                    "Tool '" + tool.id + "' does not support exclusive recipes yet");
        }

        ProcessingRecipe<RecipeWrapper> recipe = delegate.fromJson(id, json);
        ((HandMadeToolRecipeLike) recipe).setTool(tool);

        // ★ 注液家族必须有至少一个流体输入
        //   Create 的 validate 只检查上限，不检查下限——没有流体输入的配方能加载成功，
        //   然后在工具侧 getRequiredFluid() 抛 IllegalStateException，运行时崩。
        //   所以这里在加载期直接拒绝。
        if (tool == ToolType.INFUSION_GUN && recipe.getFluidIngredients().isEmpty()) {
            throw new IllegalArgumentException(
                    "Filling L3 recipe '" + id + "' must declare at least one fluid ingredient");
        }

        return recipe;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, ProcessingRecipe<RecipeWrapper> recipe) {
        ToolType tool = ((HandMadeToolRecipeLike) recipe).getTool();
        buf.writeEnum(tool != null ? tool : ToolType.HAND_SAW);

        ProcessingRecipeSerializer<ProcessingRecipe<RecipeWrapper>> delegate = delegateFor(tool);
        if (delegate != null) {
            delegate.toNetwork(buf, recipe);
        }
    }

    @Override
    public ProcessingRecipe<RecipeWrapper> fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        ToolType tool = buf.readEnum(ToolType.class);

        ProcessingRecipeSerializer<ProcessingRecipe<RecipeWrapper>> delegate = delegateFor(tool);
        if (delegate == null) {
            throw new IllegalStateException(
                    "Received exclusive recipe for unsupported tool: " + tool);
        }

        ProcessingRecipe<RecipeWrapper> recipe = delegate.fromNetwork(id, buf);
        ((HandMadeToolRecipeLike) recipe).setTool(tool);
        return recipe;
    }

    private static ToolType readToolOrThrow(JsonObject json) {
        String toolId = GsonHelper.getAsString(json, TOOL_FIELD);
        ToolType tool = ToolType.byId(toolId);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: '" + toolId + "'");
        }
        return tool;
    }
}