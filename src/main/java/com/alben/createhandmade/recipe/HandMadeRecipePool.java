package com.alben.createhandmade.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手搓配方池 —— L2 过滤层。
 *
 * <pre>
 *   L1 = 工具默认继承的 Create 配方
 *   L2 = 过滤层（数据包 + KubeJS 两个来源）
 *   L3 = 独占配方，已迁移到独立 RecipeType（create_hand_made:tool_recipe 等），
 *        不再经过本类
 * </pre>
 */
public class HandMadeRecipePool {

    // ================= L2: 过滤（数据包来源） =================

    private static final Map<ToolType, Set<ResourceLocation>> FILTER_MAP = new ConcurrentHashMap<>();

    // ================= L2: 过滤（KubeJS 来源） =================

    private static final Map<ToolType, Set<ResourceLocation>> KUBEJS_FILTER_MAP = new ConcurrentHashMap<>();
    private static final Map<ToolType, Set<String>> KUBEJS_MOD_FILTER_MAP = new ConcurrentHashMap<>();

    // ================= 生命周期 =================

    public static void reset() {
        FILTER_MAP.clear();
        KUBEJS_FILTER_MAP.clear();
        KUBEJS_MOD_FILTER_MAP.clear();
    }

    // ================= L2: 数据包添加过滤 =================

    public static void addFilter(ToolType tool, Collection<ResourceLocation> ids) {
        FILTER_MAP.computeIfAbsent(tool, t -> ConcurrentHashMap.newKeySet()).addAll(ids);
    }

    // ================= L2: KubeJS 添加过滤 =================

    /** 清空 KubeJS 过滤表（脚本重载时调用） */
    public static void clearKubeJSFilters() {
        KUBEJS_FILTER_MAP.clear();
        KUBEJS_MOD_FILTER_MAP.clear();
    }

    /** 添加一条 KubeJS 过滤（精确配方 ID） */
    public static void addKubeJSFilter(ToolType tool, ResourceLocation recipeId) {
        KUBEJS_FILTER_MAP.computeIfAbsent(tool, t -> ConcurrentHashMap.newKeySet()).add(recipeId);
    }

    /** 添加一条 KubeJS 过滤（整个 namespace） */
    public static void addKubeJSFilterByMod(ToolType tool, String modId) {
        KUBEJS_MOD_FILTER_MAP.computeIfAbsent(tool, t -> ConcurrentHashMap.newKeySet()).add(modId);
    }

    // ================= L2: 应用过滤 =================

    /**
     * 对 L1 结果应用 L2 过滤（数据包 + KubeJS 双来源）
     */
    public static List<Recipe<?>> applyFilter(ToolType tool, Level level, List<? extends Recipe<?>> recipes) {
        Set<ResourceLocation> dataFilter = FILTER_MAP.get(tool);
        Set<ResourceLocation> kubeFilter = KUBEJS_FILTER_MAP.get(tool);
        Set<String> kubeModFilter = KUBEJS_MOD_FILTER_MAP.get(tool);

        boolean hasDataFilter = dataFilter != null && !dataFilter.isEmpty();
        boolean hasKubeFilter = kubeFilter != null && !kubeFilter.isEmpty();
        boolean hasKubeModFilter = kubeModFilter != null && !kubeModFilter.isEmpty();

        // 三种过滤都没有 → 直接返回
        if (!hasDataFilter && !hasKubeFilter && !hasKubeModFilter) {
            return new ArrayList<>(recipes);
        }

        Map<Recipe<?>, ResourceLocation> idMap = new IdentityHashMap<>();
        RecipeManager manager = level.getRecipeManager();
        manager.getRecipeIds().forEach(id ->
                manager.byKey(id).ifPresent(r -> idMap.put(r, id)));

        List<Recipe<?>> result = new ArrayList<>();
        for (Recipe<?> r : recipes) {
            ResourceLocation id = idMap.get(r);
            if (id != null) {
                // 数据包过滤
                if (hasDataFilter && dataFilter.contains(id)) continue;
                // KubeJS 精确过滤
                if (hasKubeFilter && kubeFilter.contains(id)) continue;
                // KubeJS 按 namespace 过滤
                if (hasKubeModFilter && kubeModFilter.contains(id.getNamespace())) continue;
            }
            result.add(r);
        }
        return result;
    }
}