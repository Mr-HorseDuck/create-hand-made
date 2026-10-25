package com.alben.createhandmade.recipe;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Locale;

/**
 * 本模组自己的 {@link RecipeType} / {@link RecipeSerializer} 注册表（L3 独占层的地基）。
 *
 * <p>独占配方活在独立的 RecipeType 下，Create 的机器查不到，只有本模组工具能读到。</p>
 */
public enum HandMadeRecipeTypes {

    /** 手工工具独占配方（六个家族共用）。 */
    TOOL_RECIPE,

    /** 手锯 · 切石独占配方。 */
    STONECUTTING_RECIPE,

    /** 风箱独占配方。 */
    BELLOWS_RECIPE;

    private final Identifier id;
    private RecipeSerializer<?> serializer;
    private RecipeType<?> type;

    HandMadeRecipeTypes() {
        this.id = Identifier.fromNamespaceAndPath(CreateHandMade.MODID, name().toLowerCase(Locale.ROOT));
    }

    public Identifier getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializer;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <I extends net.minecraft.world.item.crafting.RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
        return (RecipeType<R>) type;
    }

    /** 在 mod 初始化时调用，注册全部 type + serializer。 */
    public static void register() {
        for (HandMadeRecipeTypes entry : values()) {
            // ★ 26.1 的 RecipeType.register(String) 只会注册到 minecraft: 命名空间，
            //   自定义命名空间必须自己 new 一个并走 Registry.register。
            RecipeType<?> created = new RecipeType<Recipe<?>>() {
                @Override
                public String toString() {
                    return entry.id.toString();
                }
            };
            entry.type = Registry.register(BuiltInRegistries.RECIPE_TYPE, entry.id, created);
        }

        bindSerializers();
    }

    private static void bindSerializers() {
        TOOL_RECIPE.serializer = HandMadeRecipeSerializers.toolRecipe();
        STONECUTTING_RECIPE.serializer = HandMadeRecipeSerializers.stonecutting();
        BELLOWS_RECIPE.serializer = HandMadeRecipeSerializers.bellows();

        for (HandMadeRecipeTypes entry : values()) {
            Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, entry.id, entry.serializer);
        }
    }
}
