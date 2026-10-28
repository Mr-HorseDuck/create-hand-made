package com.alben.createhandmade.recipe;

import javax.annotation.Nullable;

/**
 * L3 独占配方的公共视图。
 * Serializer 需要在不知道具体家族类的前提下读写 tool 归属。
 */
public interface HandMadeToolRecipeLike {

    @Nullable
    ToolType getTool();

    void setTool(@Nullable ToolType tool);
}