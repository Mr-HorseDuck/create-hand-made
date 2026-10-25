package com.alben.createhandmade.recipe;

import org.jetbrains.annotations.Nullable;

/** L3 独占配方的公共接口：能读出「归属工具」。 */
public interface HandMadeToolRecipeLike {

    @Nullable
    HandMadeTool getTool();
}
