package com.alben.createhandmade.recipe;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配方过滤层 L2 —— 「禁用某个工具的某条配方」的注册表。
 *
 * <p>只影响本模组工具的读取，被禁用的配方仍完整留在 RecipeManager 里。</p>
 */
public final class HandMadeRecipeFilters {

    private static final Map<HandMadeTool, Set<Identifier>> DATAPACK_BY_ID = new ConcurrentHashMap<>();
    private static final Map<HandMadeTool, Set<String>> DATAPACK_BY_MOD = new ConcurrentHashMap<>();

    private HandMadeRecipeFilters() {
    }

    /** 挂上数据包重载监听。 */
    public static void register() {
        HandMadeRecipeFilterEvents.register();
    }

    public static void clearDataPack() {
        DATAPACK_BY_ID.clear();
        DATAPACK_BY_MOD.clear();
    }

    public static void disableFromDataPack(HandMadeTool tool, Identifier recipeId) {
        DATAPACK_BY_ID.computeIfAbsent(tool, key -> ConcurrentHashMap.newKeySet()).add(recipeId);
    }

    public static void disableByModFromDataPack(HandMadeTool tool, String modId) {
        DATAPACK_BY_MOD.computeIfAbsent(tool, key -> ConcurrentHashMap.newKeySet()).add(modId);
    }

    public static boolean isDisabled(HandMadeTool tool, Identifier recipeId) {
        Set<Identifier> ids = DATAPACK_BY_ID.get(tool);
        if (ids != null && ids.contains(recipeId)) {
            return true;
        }
        Set<String> mods = DATAPACK_BY_MOD.get(tool);
        return mods != null && mods.contains(recipeId.getNamespace());
    }
}
