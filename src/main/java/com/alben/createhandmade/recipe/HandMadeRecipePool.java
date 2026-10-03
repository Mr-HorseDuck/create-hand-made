package com.alben.createhandmade.recipe;

import com.alben.createhandmade.CreateHandMade;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手搓配方池 —— 三层模型
 *
 * <pre>
 *   L1 = 工具默认继承的 Create 配方
 *   L2 = 过滤层（数据包 + KubeJS 两个来源）
 *   L3 = 独占配方（数据包）
 * </pre>
 */
public class HandMadeRecipePool {

    // ================= L2: 过滤（数据包来源） =================

    private static final Map<ToolType, Set<ResourceLocation>> FILTER_MAP = new ConcurrentHashMap<>();

    // ================= L2: 过滤（KubeJS 来源） =================

    private static final Map<ToolType, Set<ResourceLocation>> KUBEJS_FILTER_MAP = new ConcurrentHashMap<>();
    private static final Map<ToolType, Set<String>> KUBEJS_MOD_FILTER_MAP = new ConcurrentHashMap<>();

    // ================= L3: 独占 =================

    private static final Map<ToolType, List<CustomEntry>> CUSTOM_MAP = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Recipe<?>> CUSTOM_CACHE = new ConcurrentHashMap<>();

    public record CustomEntry(ResourceLocation id, JsonObject json) {}

    // ================= 生命周期 =================

    public static void reset() {
        FILTER_MAP.clear();
        KUBEJS_FILTER_MAP.clear();
        KUBEJS_MOD_FILTER_MAP.clear();
        CUSTOM_MAP.clear();
        CUSTOM_CACHE.clear();
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

    // ================= L3: 添加独占配方 =================

    public static void addCustom(ToolType tool, ResourceLocation id, JsonObject json) {
        CUSTOM_MAP.computeIfAbsent(tool, t -> new ArrayList<>()).add(new CustomEntry(id, json));
        CUSTOM_CACHE.remove(id);
    }

    // ================= 工具查询 =================

    /**
     * 返回匹配指定 ItemStack 的 L3 独占配方（单物品场景）
     */
    public static List<Recipe<?>> getCustomRecipes(ToolType tool, Level level, ItemStack input) {
        List<CustomEntry> entries = CUSTOM_MAP.get(tool);
        if (entries == null || entries.isEmpty()) return List.of();

        RecipeWrapper wrapper = wrapInput(input);
        List<Recipe<?>> result = new ArrayList<>();

        for (CustomEntry entry : entries) {
            Recipe<?> r = getOrParse(entry);
            if (r == null) continue;
            if (!matchesUnsafe(r, wrapper, level)) continue;
            result.add(r);
        }
        return result;
    }

    /**
     * ★ 返回该工具的所有 L3 独占配方（不做 ItemStack 匹配）
     * 适用于工作盆等多物品场景，由调用方自己做匹配（如 BasinRecipe.match）
     */
    public static List<Recipe<?>> getAllCustomRecipes(ToolType tool) {
        List<CustomEntry> entries = CUSTOM_MAP.get(tool);
        if (entries == null || entries.isEmpty()) return List.of();

        List<Recipe<?>> result = new ArrayList<>();
        for (CustomEntry entry : entries) {
            Recipe<?> r = getOrParse(entry);
            if (r != null) result.add(r);
        }
        return result;
    }

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

    // ================= 内部 =================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean matchesUnsafe(Recipe<?> r, RecipeWrapper wrapper, Level level) {
        return ((Recipe) r).matches(wrapper, level);
    }

    /**
     * ★ 1.20.1：绕过 RecipeManager.fromJson 的 IContext 参数，
     *   直接用 RecipeSerializer.fromJson(id, json) 解析。
     */
    @Nullable
    private static Recipe<?> getOrParse(CustomEntry entry) {
        Recipe<?> cached = CUSTOM_CACHE.get(entry.id());
        if (cached != null) return cached;

        try {
            String typeStr = GsonHelper.getAsString(entry.json(), "type");
            ResourceLocation typeId = new ResourceLocation(typeStr);
            RecipeSerializer<?> serializer = BuiltInRegistries.RECIPE_SERIALIZER.get(typeId);

            if (serializer == null) {
                CreateHandMade.LOGGER.warn("Unknown recipe serializer: {}", typeId);
                return null;
            }

            Recipe<?> parsed = serializer.fromJson(entry.id(), entry.json());
            if (parsed != null) {
                CUSTOM_CACHE.put(entry.id(), parsed);
            }
            return parsed;
        } catch (Exception ex) {
            CreateHandMade.LOGGER.error("Failed to parse custom recipe {}", entry.id(), ex);
            return null;
        }
    }

    private static RecipeWrapper wrapInput(ItemStack input) {
        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, input.copyWithCount(1));
        return new RecipeWrapper(handler);
    }
}