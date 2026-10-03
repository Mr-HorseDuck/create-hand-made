package com.alben.createhandmade.kubejs;

import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.ToolType;
import dev.latvian.mods.kubejs.event.EventJS;                    // ★ 改成 EventJS
import net.minecraft.resources.ResourceLocation;

public class HandMadeToolFilterKubeEvent extends EventJS {       // ★ 改成 extends

    public HandMadeToolFilterKubeEvent() {}

    /** 精确禁用某个工具下的一条配方 */
    public void disable(String toolId, String recipeId) {
        ToolType tool = parseTool(toolId);
        HandMadeRecipePool.addKubeJSFilter(tool, new ResourceLocation(recipeId));
    }

    /** 禁用某个工具下、某个 mod namespace 的全部配方 */
    public void disableByMod(String toolId, String modId) {
        ToolType tool = parseTool(toolId);
        HandMadeRecipePool.addKubeJSFilterByMod(tool, modId);
    }

    private static ToolType parseTool(String toolId) {
        ToolType tool = ToolType.byId(toolId);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool id: " + toolId);
        }
        return tool;
    }
}