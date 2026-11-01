package com.alben.createhandmade.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import com.alben.createhandmade.recipe.type.HandMadeStonecuttingRecipe;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import javax.annotation.Nullable;

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

        // ================= 1.21 对齐：getBaseRecipes =================

    /** 按 RecipeType 全量拉取，不调 matches。1.20.1 返回裸 Recipe。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Recipe<?>> recipesOfType(Level level, RecipeType<?> type) {
        return new ArrayList<>(level.getRecipeManager().getAllRecipesFor((RecipeType) type));
    }

    /** 该工具是否有自己的 L3 独占配方类（与 serializer 的分派表同步）。 */
    private static boolean supportsExclusiveRecipes(ToolType tool) {
        return tool == ToolType.PRESS_HAMMER_BASIN
                || tool == ToolType.STIRRING_STAFF
                || tool == ToolType.MORTAR
                || tool == ToolType.CRUSHER_MORTAR
                || tool == ToolType.PRESS_HAMMER_DEPOT
                || tool == ToolType.HAND_SAW
                || tool == ToolType.INFUSION_GUN
                || tool == ToolType.POINTER;
    }

    /** 某条独占配方（归属 owner）是否应该进入 tool 的候选集。 */
    private static boolean acceptsExclusiveRecipe(ToolType tool, @Nullable ToolType owner) {
        if (owner == null) return false;
        if (owner == tool) return true;
        // 碾钵 = 研钵的升级版：L1 层它能读 MILLING，L3 层同理
        return tool == ToolType.CRUSHER_MORTAR && owner == ToolType.MORTAR;
    }

    /** 从 TOOL_RECIPE 全量收 L3，按 tool 字段过滤。 */
    private static void collectExclusiveRecipes(ToolType tool, Level level, List<Recipe<?>> out) {
        if (!supportsExclusiveRecipes(tool)) return;

        for (Recipe<?> r : recipesOfType(level, HandMadeRecipeTypes.TOOL_RECIPE.get())) {
            if (!(r instanceof HandMadeToolRecipeLike like)) continue;
            if (!acceptsExclusiveRecipe(tool, like.getTool())) continue;
            out.add(r);
        }
    }

    /** 跟随 Create 的 allowStonecuttingOnSaw 配置。 */
    private static boolean allowsStonecutting() {
        try {
            var server = com.simibubi.create.infrastructure.config.AllConfigs.server();
            if (server == null) return true;
            return server.recipes.allowStonecuttingOnSaw.get();
        } catch (Throwable t) {
            return true;
        }
    }
    // ================= L2: 应用过滤 =================
    /**
     * 某工具类型下所有基础配方。
     *
     * <p>语义和 1.21 一致：独立 L3 type（stonecutting）由对应 case 收，
     * 通用 L3 type（tool_recipe）在 switch 之后统一收。</p>
     *
     * <p>1.20.1 的 {@link ToolType} 没有 AUTO_SQUARE / AUTO_SHAPELESS / AUTO_BREWING，
     * 这三个自动类别走 {@code PRESS_HAMMER_BASIN} / {@code STIRRING_STAFF} 的 L2 过滤，
     * 由 JEI 各自类别单独收集，不在本方法的返回范围内。</p>
     */
    public static List<Recipe<?>> getBaseRecipes(ToolType tool, Level level) {
        if (level == null) return List.of();

        List<Recipe<?>> result = new ArrayList<>();
        switch (tool) {
            case HAND_SAW -> {
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.CUTTING.getType())) {
                    if (r instanceof CuttingRecipe) result.add(r);
                }
            }
            case HAND_SAW_STONECUTTING -> {
                // 门控：跟随 Create 的 allowStonecuttingOnSaw
                if (allowsStonecutting()) {
                    for (Recipe<?> r : recipesOfType(level, RecipeType.STONECUTTING)) {
                        if (r instanceof StonecutterRecipe) result.add(r);
                    }
                    for (Recipe<?> r : recipesOfType(level, HandMadeRecipeTypes.STONECUTTING_RECIPE.get())) {
                        if (r instanceof HandMadeStonecuttingRecipe) result.add(r);
                    }
                }
            }
            case PRESS_HAMMER_DEPOT -> {
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.PRESSING.getType())) {
                    if (r instanceof PressingRecipe) result.add(r);
                }
            }
            case PRESS_HAMMER_BASIN -> {
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.COMPACTING.getType())) {
                    if (r instanceof BasinRecipe) result.add(r);
                }
            }
            case INFUSION_GUN -> {
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.FILLING.getType())) {
                    if (r instanceof FillingRecipe) result.add(r);
                }
            }
            case MORTAR -> {
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.MILLING.getType())) {
                    if (r instanceof MillingRecipe) result.add(r);
                }
            }
            case CRUSHER_MORTAR -> {
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.CRUSHING.getType())) {
                    if (r instanceof CrushingRecipe) result.add(r);
                }
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.MILLING.getType())) {
                    if (r instanceof MillingRecipe) result.add(r);
                }
            }
            case STIRRING_STAFF -> {
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.MIXING.getType())) {
                    if (r instanceof BasinRecipe) result.add(r);
                }
            }
            case POINTER -> {
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.DEPLOYING.getType())) {
                    if (r instanceof ItemApplicationRecipe) result.add(r);
                }
                for (Recipe<?> r : recipesOfType(level, AllRecipeTypes.ITEM_APPLICATION.getType())) {
                    if (r instanceof ItemApplicationRecipe) result.add(r);
                }
            }
            case BELLOWS -> {
                // 风箱无 L1，只有 L3
            }
        }

        // 通用 L3：switch 之后统一收
        collectExclusiveRecipes(tool, level, result);

        return result;
    }

    
    /** 某配方是否被数据包 / KubeJS 的过滤器对本工具禁用。 */
    public static boolean isRecipeDisabled(ToolType tool, ResourceLocation recipeId) {
        Set<ResourceLocation> dataFilter = FILTER_MAP.get(tool);
        if (dataFilter != null && dataFilter.contains(recipeId)) return true;

        Set<ResourceLocation> kubeFilter = KUBEJS_FILTER_MAP.get(tool);
        if (kubeFilter != null && kubeFilter.contains(recipeId)) return true;

        Set<String> kubeModFilter = KUBEJS_MOD_FILTER_MAP.get(tool);
        if (kubeModFilter != null && kubeModFilter.contains(recipeId.getNamespace())) return true;

        return false;
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
}