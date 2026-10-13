package com.alben.createhandmade.recipe;

import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * 应用家族的 L3 独占配方类，服务指杆（ToolType.POINTER）。
 *
 * 继承 ProcessingRecipe（而非 ItemApplicationRecipe），因为
 * ItemApplicationRecipe 的构造参数是 AllRecipeTypes 枚举而非 IRecipeTypeInfo，
 * 无法子类化出自定义 type。
 *
 * keepHeldItem 通过 readAdditional / writeAdditional 钩子读写，
 * 由 Create 的 ProcessingRecipeSerializer 自动调用，无需 serializer 特殊处理。
 * JSON 字段名沿用 Create 的 "keepHeldItem"（驼峰），与 1.21 的 "keep_held_item" 不同。
 */
public class HandMadeApplicationRecipe extends ProcessingRecipe<RecipeWrapper>
        implements HandMadeToolRecipeLike {

    @Nullable
    private ToolType tool;

    private boolean keepHeldItem;

    public HandMadeApplicationRecipe(ProcessingRecipeParams params) {
        super(HandMadeRecipeTypes.TOOL_RECIPE_INFO, params);
    }

    /**
     * 2 槽判定：槽 0 = 被处理的目标物品（置物台/传送带上的），槽 1 = 手持物品/工具。
     */
    @Override
    public boolean matches(RecipeWrapper inv, Level level) {
        if (ingredients.size() < 2 || inv.getContainerSize() < 2) {
            return false;
        }
        return ingredients.get(0).test(inv.getItem(0)) && ingredients.get(1).test(inv.getItem(1));
    }

    @Override protected int getMaxInputCount() { return 2; }
    @Override protected int getMaxOutputCount() { return 4; }
    @Override protected boolean canSpecifyDuration() { return false; }

    public boolean shouldKeepHeldItem() { return keepHeldItem; }
    public void setKeepHeldItem(boolean keepHeldItem) { this.keepHeldItem = keepHeldItem; }

    // ================= keepHeldItem 序列化（Create 钩子） =================

    @Override
    public void readAdditional(JsonObject json) {
        keepHeldItem = GsonHelper.getAsBoolean(json, "keepHeldItem", false);
    }

    @Override
    public void writeAdditional(JsonObject json) {
        if (keepHeldItem) {
            json.addProperty("keepHeldItem", true);
        }
    }

    @Override
    public void readAdditional(FriendlyByteBuf buffer) {
        keepHeldItem = buffer.readBoolean();
    }

    @Override
    public void writeAdditional(FriendlyByteBuf buffer) {
        buffer.writeBoolean(keepHeldItem);
    }

    // ================= HandMadeToolRecipeLike =================

    @Nullable @Override public ToolType getTool() { return tool; }
    @Override public void setTool(@Nullable ToolType tool) { this.tool = tool; }
}