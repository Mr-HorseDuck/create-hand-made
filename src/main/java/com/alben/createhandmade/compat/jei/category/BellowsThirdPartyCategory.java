package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.type.HandMadeBellowsRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * 风箱 · 第三方兼容：展示 fan_type 属于第三方模组的 bellows_recipe。
 *
 * <p>1.20.1 / 1.21 默认都没有第三方 bellows_recipe 数据包，
 * 类别默认是空的，由整合包作者写数据包填充。</p>
 *
 * <p>左侧显示风箱 + 该配方的 fan_type 对应的所有介质。</p>
 */
public class BellowsThirdPartyCategory
        extends CreateRecipeCategory<HandMadeBellowsRecipe> {

    public BellowsThirdPartyCategory(Info<HandMadeBellowsRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HandMadeBellowsRecipe recipe,
                          IFocusGroup focuses) {
        // 输入
        if (!recipe.getIngredients().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 16, 24)
                    .addIngredients(recipe.getIngredients().get(0));
        }

        // 输出
        ItemStack result = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
        if (!result.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 100, 24)
                    .addItemStack(result);
        }

        // 介质：按 fan_type 从注册表查
        ResourceLocation fanType = recipe.getFanType();
        if (fanType != null) {
            List<ItemStack> media = BellowsMediaRegistry.getMediaFor(fanType);
            if (!media.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.CATALYST, 16, 52)
                        .addItemStacks(media);
            }
        }
    }

    @Override
    public void draw(HandMadeBellowsRecipe recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        ResourceLocation fanType = recipe.getFanType();
        if (fanType == null) return;
        graphics.drawString(Minecraft.getInstance().font,
                fanType.toString(), 4, 4, 0x808080);
    }

    // ================= 配方收集 =================

    /** 收集所有 fan_type 属于第三方模组的 bellows_recipe。 */
    public static List<HandMadeBellowsRecipe> collectThirdPartyRecipes() {
        List<HandMadeBellowsRecipe> result = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (Recipe<?> r : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.BELLOWS_RECIPE.get())) {
            if (!(r instanceof HandMadeBellowsRecipe recipe)) continue;
            ResourceLocation fanType = recipe.getFanType();
            if (fanType == null) continue;
            if ("create".equals(fanType.getNamespace())) continue;  // 排除 Create 内置
            result.add(recipe);
        }
        return result;
    }
}
