package com.alben.createhandmade.recipe;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.recipe.serializer.HandMadeBellowsRecipeSerializer;
import com.alben.createhandmade.recipe.serializer.HandMadeStonecuttingRecipeSerializer;
import com.alben.createhandmade.recipe.serializer.HandMadeToolRecipeSerializer;
import com.alben.createhandmade.recipe.serializer.HandMadeSequencedAssemblyRecipeSerializer;
import com.alben.createhandmade.recipe.type.HandMadeRecipes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组自己的 RecipeType / RecipeSerializer 注册表（L3 独占层）。
 */
public class HandMadeRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, CreateHandMade.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateHandMade.MODID);

    // ==================== stonecutting ====================

    public static final RegistryObject<RecipeType<HandMadeRecipes.Stonecutting>> STONECUTTING_RECIPE =
            RECIPE_TYPES.register("stonecutting_recipe", () -> RecipeType.simple(
                    new ResourceLocation(CreateHandMade.MODID, "stonecutting_recipe")));

    public static final RegistryObject<RecipeSerializer<HandMadeRecipes.Stonecutting>> STONECUTTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("stonecutting_recipe",
                    HandMadeStonecuttingRecipeSerializer::new);

    // ==================== tool_recipe（通用 L3 独占） ====================

    /**
     * create_hand_made:tool_recipe —— 通用 L3 独占类型。
     *
     * 泛型升到 ProcessingRecipe<RecipeWrapper>，同时容纳所有家族：
     * HandMadeRecipes.Cutting / HandMadeRecipes.Pressing / ...
     * 按 JSON 的 tool 字段分派。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final RegistryObject<RecipeType<ProcessingRecipe<RecipeWrapper>>> TOOL_RECIPE =
            RECIPE_TYPES.register("tool_recipe", () -> (RecipeType) RecipeType.simple(
                    new ResourceLocation(CreateHandMade.MODID, "tool_recipe")));

    /** 按 tool 字段分派到对应家族 serializer 的自定义实现。 */
    public static final RegistryObject<RecipeSerializer<ProcessingRecipe<RecipeWrapper>>> TOOL_SERIALIZER =
            RECIPE_SERIALIZERS.register("tool_recipe",
                    HandMadeToolRecipeSerializer::new);

    /** IRecipeTypeInfo 包装，供 ProcessingRecipe 构造使用。 */
    public static final IRecipeTypeInfo TOOL_RECIPE_INFO = new IRecipeTypeInfo() {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation(CreateHandMade.MODID, "tool_recipe");
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends RecipeSerializer<?>> T getSerializer() {
            return (T) TOOL_SERIALIZER.get();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends RecipeType<?>> T getType() {
            return (T) TOOL_RECIPE.get();
        }
    };

    // ==================== bellows（风箱独立 type） ====================

    public static final RegistryObject<RecipeType<HandMadeRecipes.Bellows>> BELLOWS_RECIPE =
        RECIPE_TYPES.register("bellows_recipe", () -> RecipeType.simple(
                new ResourceLocation(CreateHandMade.MODID, "bellows_recipe")));

    public static final RegistryObject<RecipeSerializer<HandMadeRecipes.Bellows>> BELLOWS_SERIALIZER =
        RECIPE_SERIALIZERS.register("bellows_recipe",
                HandMadeBellowsRecipeSerializer::new);

    public static final IRecipeTypeInfo BELLOWS_RECIPE_INFO = new IRecipeTypeInfo() {
    @Override
    public ResourceLocation getId() {
        return new ResourceLocation(CreateHandMade.MODID, "bellows_recipe");
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) BELLOWS_SERIALIZER.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends RecipeType<?>> T getType() {
        return (T) BELLOWS_RECIPE.get();
    }
    };

    // ==================== hand_sequenced_assembly（手搓专属序列组装） ====================

    /** L3 通用类型的完整 id：序列组装的步骤必须是这个 type，加载期用它做校验。 */
    public static final String TOOL_RECIPE_ID = CreateHandMade.MODID + ":tool_recipe";

    /**
     * create_hand_made:hand_sequenced_assembly —— 手搓专属序列组装。
     *
     * <p>与 Create 的 {@code create:sequenced_assembly} 是<b>两个独立类型</b>：
     * 原版机器（部署器 / 压床 / 机械锯 / 注液器）只查 Create 自己的桶，看不到本类型；
     * 本类型只有本模组的手搓工具会查询并推进（见
     * {@link HandMadeSequencedAssemblyRecipe#getStepRecipe}）。</p>
     *
     * <p>JSON 字段与原版一致，{@code type} 换成 {@code create_hand_made:hand_sequenced_assembly} 即可；
     * 但 {@code sequence} 里的每一步必须是本模组的 L3 配方（{@code create_hand_made:tool_recipe}），
     * serializer 会在加载期校验。</p>
     */
    public static final RegistryObject<RecipeType<HandMadeSequencedAssemblyRecipe>> HAND_SEQUENCED_ASSEMBLY =
            RECIPE_TYPES.register("hand_sequenced_assembly", () -> RecipeType.simple(
                    new ResourceLocation(CreateHandMade.MODID, "hand_sequenced_assembly")));

    public static final RegistryObject<RecipeSerializer<HandMadeSequencedAssemblyRecipe>> HAND_SEQUENCED_ASSEMBLY_SERIALIZER =
            RECIPE_SERIALIZERS.register("hand_sequenced_assembly",
                    HandMadeSequencedAssemblyRecipeSerializer::new);

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}