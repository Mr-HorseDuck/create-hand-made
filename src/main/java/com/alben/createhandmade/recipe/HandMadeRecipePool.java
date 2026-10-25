package com.alben.createhandmade.recipe;

import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.content.fluids.transfer.FillingRecipe;
import com.zurrtum.create.content.kinetics.crusher.CrushingRecipe;
import com.zurrtum.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.zurrtum.create.content.kinetics.millstone.MillingRecipe;
import com.zurrtum.create.content.kinetics.mixer.PotionRecipe;
import com.zurrtum.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.zurrtum.create.content.kinetics.press.PressingRecipe;
import com.zurrtum.create.content.kinetics.saw.CuttingRecipe;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import com.zurrtum.create.infrastructure.config.AllConfigs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 手工工具的「基础配方池」——游戏内工具与 JEI 类别的统一配方来源。
 *
 * <p>只负责收集候选列表，不做任何 input 匹配。</p>
 */
public final class HandMadeRecipePool {

    private HandMadeRecipePool() {
    }

    public static List<RecipeHolder<?>> getBaseRecipes(HandMadeTool tool, Level level) {
        List<RecipeHolder<?>> base = level == null
                ? new ArrayList<>()
                : switch (tool) {
                    case PRESS_HAMMER_BASIN -> collectPressHammerBasin(level);
                    case PRESS_HAMMER_AUTO_SQUARE -> collectPressHammerAutoSquare(level);
                    case PRESS_HAMMER_DEPOT -> collectPressHammerDepot(level);
                    case MORTAR -> collectMortar(level);
                    case CRUSHER_MORTAR -> collectCrusherMortar(level);
                    case HAND_SAW -> collectHandSaw(level);
                    case HAND_SAW_STONECUTTING -> collectHandSawStonecutting(level);
                    case STIRRING_STAFF -> collectStirringStaff(level);
                    case STIRRING_STAFF_AUTO_SHAPELESS -> collectStirringStaffAutoShapeless(level);
                    case STIRRING_STAFF_AUTO_BREWING -> collectStirringStaffAutoBrewing(level);
                    case POINTER -> collectPointer(level);
                    case INFUSION_GUN -> collectInfusionGun(level);
                };

        if (level != null) {
            collectExclusiveRecipes(tool, level, base);
        }

        base = HandMadeRecipeRegistry.appendExclusive(tool, base);
        base.removeIf(holder -> HandMadeRecipeFilters.isDisabled(tool, holder.id().identifier()));
        return base;
    }

    @SafeVarargs
    public static List<RecipeHolder<?>> mergeInGlobalOrder(Level level, List<RecipeHolder<?>>... lists) {
        Set<RecipeHolder<?>> subset = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<RecipeHolder<?>> list : lists) {
            if (list != null) {
                subset.addAll(list);
            }
        }

        List<RecipeHolder<?>> result = new ArrayList<>(subset.size());
        RecipeManager manager = recipeManager(level);
        if (manager == null) {
            return result;
        }

        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (subset.contains(holder)) {
                result.add(holder);
            }
        }
        return result;
    }

    // ==================== 冲压锤 ====================

    private static List<RecipeHolder<?>> collectPressHammerBasin(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.COMPACTING)) {
            if (holder.value() instanceof BasinRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    private static List<RecipeHolder<?>> collectPressHammerAutoSquare(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        if (!AllConfigs.server().recipes.allowShapedSquareInPress.get()) {
            return result;
        }

        for (RecipeHolder<?> holder : recipesOfType(level, RecipeType.CRAFTING)) {
            if (!(holder.value() instanceof CraftingRecipe crafting)) continue;
            if (!MechanicalPressBlockEntity.canCompress(crafting)) continue;
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder)) continue;
            result.add(holder);
        }
        return result;
    }

    private static List<RecipeHolder<?>> collectPressHammerDepot(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.PRESSING)) {
            if (holder.value() instanceof PressingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    // ==================== 研钵 / 碾钵 ====================

    private static List<RecipeHolder<?>> collectMortar(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.MILLING)) {
            if (holder.value() instanceof MillingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    private static List<RecipeHolder<?>> collectCrusherMortar(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.CRUSHING)) {
            if (holder.value() instanceof CrushingRecipe) {
                result.add(holder);
            }
        }
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.MILLING)) {
            if (holder.value() instanceof MillingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    // ==================== 手锯 ====================

    private static List<RecipeHolder<?>> collectHandSaw(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.CUTTING)) {
            if (holder.value() instanceof CuttingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    private static List<RecipeHolder<?>> collectHandSawStonecutting(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        if (!createAllowsStonecutting()) {
            return result;
        }

        for (RecipeHolder<?> holder : recipesOfType(level, RecipeType.STONECUTTING)) {
            if (holder.value() instanceof StonecutterRecipe) {
                result.add(holder);
            }
        }

        for (RecipeHolder<?> holder : recipesOfType(level, HandMadeRecipeTypes.STONECUTTING_RECIPE.getType())) {
            if (holder.value() instanceof HandMadeStonecuttingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    private static boolean createAllowsStonecutting() {
        var server = AllConfigs.server();
        return server != null && server.recipes.allowStonecuttingOnSaw.get();
    }

    // ==================== 搅拌杖 ====================

    private static List<RecipeHolder<?>> collectStirringStaff(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.MIXING)) {
            if (holder.value() instanceof BasinRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    private static List<RecipeHolder<?>> collectStirringStaffAutoShapeless(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        if (!AllConfigs.server().recipes.allowShapelessInMixer.get()) {
            return result;
        }

        for (RecipeHolder<?> holder : recipesOfType(level, RecipeType.CRAFTING)) {
            if (!(holder.value() instanceof CraftingRecipe crafting)) continue;
            if (crafting instanceof ShapedRecipe) continue;
            if (crafting.placementInfo().ingredients().size() <= 1) continue;
            if (MechanicalPressBlockEntity.canCompress(crafting)) continue;
            if (AllRecipeTypes.shouldIgnoreInAutomation(holder)) continue;
            result.add(holder);
        }
        return result;
    }

    private static List<RecipeHolder<?>> collectStirringStaffAutoBrewing(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        if (!AllConfigs.server().recipes.allowBrewingInMixer.get()) {
            return result;
        }

        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.POTION)) {
            if (holder.value() instanceof PotionRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    // ==================== 指杆 / 灌注枪 ====================

    private static List<RecipeHolder<?>> collectPointer(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();

        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.DEPLOYING)) {
            if (holder.value() instanceof ItemApplicationRecipe) {
                result.add(holder);
            }
        }
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.ITEM_APPLICATION)) {
            if (holder.value() instanceof ItemApplicationRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    private static List<RecipeHolder<?>> collectInfusionGun(Level level) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOfType(level, AllRecipeTypes.FILLING)) {
            if (holder.value() instanceof FillingRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    // ==================== L3 ====================

    private static void collectExclusiveRecipes(HandMadeTool tool, Level level, List<RecipeHolder<?>> out) {
        if (!supportsExclusiveRecipes(tool)) {
            return;
        }

        for (RecipeHolder<?> holder : recipesOfType(level, HandMadeRecipeTypes.TOOL_RECIPE.getType())) {
            if (!(holder.value() instanceof HandMadeToolRecipeLike exclusive)) {
                continue;
            }
            if (!acceptsExclusiveRecipe(tool, exclusive.getTool())) {
                continue;
            }
            out.add(holder);
        }
    }

    private static boolean supportsExclusiveRecipes(HandMadeTool tool) {
        return tool == HandMadeTool.PRESS_HAMMER_BASIN
                || tool == HandMadeTool.STIRRING_STAFF
                || tool == HandMadeTool.MORTAR
                || tool == HandMadeTool.CRUSHER_MORTAR
                || tool == HandMadeTool.PRESS_HAMMER_DEPOT
                || tool == HandMadeTool.HAND_SAW
                || tool == HandMadeTool.INFUSION_GUN
                || tool == HandMadeTool.POINTER;
    }

    private static boolean acceptsExclusiveRecipe(HandMadeTool tool, @Nullable HandMadeTool owner) {
        if (owner == null) {
            return false;
        }
        if (owner == tool) {
            return true;
        }
        return tool == HandMadeTool.CRUSHER_MORTAR && owner == HandMadeTool.MORTAR;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<?>> recipesOfType(Level level, RecipeType<?> type) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        RecipeManager manager = recipeManager(level);
        if (manager == null) {
            return result;
        }
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (holder.value().getType() == type) {
                result.add(holder);
            }
        }
        return result;
    }

    /** 26.1 起配方只在服务端可用；客户端拿不到全量配方。 */
    @Nullable
    private static RecipeManager recipeManager(@Nullable Level level) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        MinecraftServer server = level.getServer();
        return server == null ? null : server.getRecipeManager();
    }
}
