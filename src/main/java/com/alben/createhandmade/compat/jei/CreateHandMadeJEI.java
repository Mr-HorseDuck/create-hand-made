package com.alben.createhandmade.compat.jei;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.compat.jei.category.BellowsCookingCategory;
import com.alben.createhandmade.compat.jei.category.BellowsHauntingCategory;
import com.alben.createhandmade.compat.jei.category.BellowsSplashingCategory;
import com.alben.createhandmade.compat.jei.category.CrusherMortarCrushingCategory;
import com.alben.createhandmade.compat.jei.category.HandInfusionGunCategory;
import com.alben.createhandmade.compat.jei.category.HandPressBasinCategory;
import com.alben.createhandmade.compat.jei.category.HandPressDepotCategory;
import com.alben.createhandmade.compat.jei.category.HandSawCategory;
import com.alben.createhandmade.compat.jei.category.MortarMillingCategory;
import com.alben.createhandmade.compat.jei.category.PointerApplicationCategory;
import com.alben.createhandmade.compat.jei.category.StirringStaffMixingCategory;
import com.alben.createhandmade.item.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.compat.jei.CreateJEI;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.SpoutCategory;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.infrastructure.config.AllConfigs;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class CreateHandMadeJEI implements IModPlugin {

    private static final ResourceLocation UID =
            new ResourceLocation(CreateHandMade.MODID, "jei_plugin");

    private final List<CreateRecipeCategory<?>> allCategories = new ArrayList<>();
    private IIngredientManager ingredientManager;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        allCategories.clear();

        // ==================== 手锯切削 ====================
        CreateRecipeCategory.Info<CuttingRecipe> handSawInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_SAW_CUTTING,
                Component.translatable("jei.create_hand_made.hand_saw"),
                new EmptyBackground(177, 55),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.HAND_SAW.get()),
                        () -> new ItemStack(Items.OAK_LOG)),
                CreateHandMadeJEI::collectHandSawRecipes,
                List.of(() -> new ItemStack(ModItems.HAND_SAW.get()))
        );
        allCategories.add(new HandSawCategory(handSawInfo));

        // ==================== 冲压锤 · 置物台 ====================
        CreateRecipeCategory.Info<PressingRecipe> handPressDepotInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_PRESS_DEPOT,
                Component.translatable("jei.create_hand_made.hand_press_depot"),
                new EmptyBackground(177, 55),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.PRESS_HAMMER.get()),
                        () -> new ItemStack(AllItems.IRON_SHEET.get())),
                CreateHandMadeJEI::collectPressingRecipes,
                List.of(() -> new ItemStack(ModItems.PRESS_HAMMER.get()))
        );
        allCategories.add(new HandPressDepotCategory(handPressDepotInfo));

        // ==================== 冲压锤 · 工作盆（打包） ====================
        CreateRecipeCategory.Info<BasinRecipe> handPressBasinInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_PRESS_BASIN,
                Component.translatable("jei.create_hand_made.hand_press_basin"),
                new EmptyBackground(177, 103),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.PRESS_HAMMER.get()),
                        () -> new ItemStack(AllBlocks.BASIN.get())),
                CreateHandMadeJEI::collectCompactingRecipes,
                List.of(() -> new ItemStack(ModItems.PRESS_HAMMER.get()))
        );
        allCategories.add(HandPressBasinCategory.standard(handPressBasinInfo));

        // ==================== 冲压锤 · 工作盆（自动摆放） ====================
        CreateRecipeCategory.Info<BasinRecipe> handPressBasinAutoSquareInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_PRESS_BASIN_AUTO_SQUARE,
                Component.translatable("jei.create_hand_made.hand_press_basin_auto_square"),
                new EmptyBackground(177, 85),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.PRESS_HAMMER.get()),
                        () -> new ItemStack(Items.CRAFTING_TABLE)),
                CreateHandMadeJEI::collectAutoSquareRecipes,
                List.of(() -> new ItemStack(ModItems.PRESS_HAMMER.get()))
        );
        allCategories.add(HandPressBasinCategory.autoSquare(handPressBasinAutoSquareInfo));

        // ==================== 灌注枪 · 注液 ====================
        CreateRecipeCategory.Info<FillingRecipe> handInfusionGunInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.HAND_INFUSION_GUN,
                Component.translatable("jei.create_hand_made.hand_infusion_gun"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.INFUSION_GUN.get()),
                        () -> new ItemStack(Items.WATER_BUCKET)),
                this::collectInfusionGunRecipes,
                List.of(() -> new ItemStack(ModItems.INFUSION_GUN.get()))
        );
        allCategories.add(new HandInfusionGunCategory(handInfusionGunInfo));

        // ==================== 研钵 · 研磨 ====================
        CreateRecipeCategory.Info<MillingRecipe> mortarMillingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.MORTAR_MILLING,
                Component.translatable("jei.create_hand_made.mortar_milling"),
                new EmptyBackground(177, 55),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.MORTAR.get()),
                        () -> new ItemStack(AllItems.WHEAT_FLOUR.get())),
                CreateHandMadeJEI::collectMillingRecipes,
                List.of(() -> new ItemStack(ModItems.MORTAR.get()))
        );
        allCategories.add(new MortarMillingCategory(mortarMillingInfo));

        // ==================== 碾钵 · 粉碎 ====================
        CreateRecipeCategory.Info<CrushingRecipe> crusherMortarCrushingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.CRUSHER_MORTAR_CRUSHING,
                Component.translatable("jei.create_hand_made.crusher_mortar_crushing"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.CRUSHER_MORTAR.get()),
                        () -> new ItemStack(AllItems.CRUSHED_GOLD.get())),
                CreateHandMadeJEI::collectCrushingRecipes,
                List.of(() -> new ItemStack(ModItems.CRUSHER_MORTAR.get()))
        );
        allCategories.add(new CrusherMortarCrushingCategory(crusherMortarCrushingInfo));

        // ==================== 搅拌杖 · 混合 ====================
        CreateRecipeCategory.Info<BasinRecipe> stirringStaffInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.STIRRING_STAFF_MIXING,
                Component.translatable("jei.create_hand_made.stirring_staff_mixing"),
                new EmptyBackground(177, 103),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.STIRRING_STAFF.get()),
                        () -> new ItemStack(AllBlocks.BASIN.get())),
                CreateHandMadeJEI::collectMixingRecipes,
                List.of(() -> new ItemStack(ModItems.STIRRING_STAFF.get()))
        );
        allCategories.add(StirringStaffMixingCategory.standard(stirringStaffInfo));

        // ==================== 搅拌杖 · 自动无序合成 ====================
        CreateRecipeCategory.Info<BasinRecipe> stirringStaffAutoShapelessInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.STIRRING_STAFF_AUTO_SHAPELESS,
                Component.translatable("jei.create_hand_made.stirring_staff_auto_shapeless"),
                new EmptyBackground(177, 85),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.STIRRING_STAFF.get()),
                        () -> new ItemStack(Items.CRAFTING_TABLE)),
                CreateHandMadeJEI::collectAutoShapelessRecipes,
                List.of(() -> new ItemStack(ModItems.STIRRING_STAFF.get()))
        );
        allCategories.add(StirringStaffMixingCategory.autoShapeless(stirringStaffAutoShapelessInfo));

        // ==================== 搅拌杖 · 自动酿造 ====================
        CreateRecipeCategory.Info<BasinRecipe> stirringStaffAutoBrewingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.STIRRING_STAFF_AUTO_BREWING,
                Component.translatable("jei.create_hand_made.stirring_staff_auto_brewing"),
                new EmptyBackground(177, 103),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.STIRRING_STAFF.get()),
                        () -> new ItemStack(Items.BREWING_STAND)),
                CreateHandMadeJEI::collectAutoBrewingRecipes,
                List.of(() -> new ItemStack(ModItems.STIRRING_STAFF.get()))
        );
        allCategories.add(StirringStaffMixingCategory.autoBrewing(stirringStaffAutoBrewingInfo));

        // ==================== 指杆 · 应用 ====================
        CreateRecipeCategory.Info<ItemApplicationRecipe> pointerApplicationInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.POINTER_APPLICATION,
                Component.translatable("jei.create_hand_made.pointer_application"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.POINTER.get()),
                        () -> new ItemStack(AllBlocks.DEPOT.get())),
                CreateHandMadeJEI::collectPointerRecipes,
                List.of(() -> new ItemStack(ModItems.POINTER.get()))
        );
        allCategories.add(new PointerApplicationCategory(pointerApplicationInfo));

        // ==================== 风箱 · 熔炼 ====================
        CreateRecipeCategory.Info<AbstractCookingRecipe> bellowsBlastingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.BELLOWS_BLASTING,
                Component.translatable("jei.create_hand_made.bellows_blasting"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.BELLOWS.get()),
                        () -> new ItemStack(Items.LAVA_BUCKET)),
                CreateHandMadeJEI::collectBellowsBlastingRecipes,
                List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
        );
        allCategories.add(new BellowsCookingCategory(bellowsBlastingInfo,
                () -> BellowsMediaRegistry.getMediaFor(AllFanProcessingTypes.BLASTING)));

        // ==================== 风箱 · 烟熏 ====================
        CreateRecipeCategory.Info<AbstractCookingRecipe> bellowsSmokingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.BELLOWS_SMOKING,
                Component.translatable("jei.create_hand_made.bellows_smoking"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.BELLOWS.get()),
                        () -> new ItemStack(Items.CAMPFIRE)),
                CreateHandMadeJEI::collectBellowsSmokingRecipes,
                List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
        );
        allCategories.add(new BellowsCookingCategory(bellowsSmokingInfo,
                () -> BellowsMediaRegistry.getMediaFor(AllFanProcessingTypes.SMOKING)));

        // ==================== 风箱 · 缠魂 ====================
        CreateRecipeCategory.Info<HauntingRecipe> bellowsHauntingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.BELLOWS_HAUNTING,
                Component.translatable("jei.create_hand_made.bellows_haunting"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.BELLOWS.get()),
                        () -> new ItemStack(Items.SOUL_CAMPFIRE)),
                CreateHandMadeJEI::collectBellowsHauntingRecipes,
                List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
        );
        allCategories.add(new BellowsHauntingCategory(bellowsHauntingInfo,
                () -> BellowsMediaRegistry.getMediaFor(AllFanProcessingTypes.HAUNTING)));

        // ==================== 风箱 · 洗涤 ====================
        CreateRecipeCategory.Info<SplashingRecipe> bellowsSplashingInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.BELLOWS_SPLASHING,
                Component.translatable("jei.create_hand_made.bellows_splashing"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.BELLOWS.get()),
                        () -> new ItemStack(Items.WATER_BUCKET)),
                CreateHandMadeJEI::collectBellowsSplashingRecipes,
                List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
        );
        allCategories.add(new BellowsSplashingCategory(bellowsSplashingInfo,
                () -> BellowsMediaRegistry.getMediaFor(AllFanProcessingTypes.SPLASHING)));

        registration.addRecipeCategories(allCategories.toArray(CreateRecipeCategory[]::new));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        this.ingredientManager = registration.getIngredientManager();
        allCategories.forEach(c -> c.registerRecipes(registration));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        allCategories.forEach(c -> c.registerCatalysts(registration));
    }

    // ==================== 配方收集 ====================

    private static List<CuttingRecipe> collectHandSawRecipes() {
        List<CuttingRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.CUTTING.getType())) {
            if (r instanceof CuttingRecipe c) result.add(c);
        }
        return result;
    }

    private static List<PressingRecipe> collectPressingRecipes() {
        List<PressingRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.PRESSING.getType())) {
            if (r instanceof PressingRecipe p) result.add(p);
        }
        return result;
    }

    private static List<BasinRecipe> collectCompactingRecipes() {
        List<BasinRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.COMPACTING.getType())) {
            if (r instanceof BasinRecipe b) result.add(b);
        }
        return result;
    }

    private static List<BasinRecipe> collectAutoSquareRecipes() {
        List<BasinRecipe> result = new ArrayList<>();
        if (!AllConfigs.server().recipes.allowShapedSquareInPress.get()) return result;

        for (Recipe<?> r : CreateJEI.getTypedRecipes(RecipeType.CRAFTING)) {
            if (!(r instanceof CraftingRecipe cr)) continue;
            if (cr instanceof MechanicalCraftingRecipe) continue;
            if (!MechanicalPressBlockEntity.canCompress(cr)) continue;
            if (AllRecipeTypes.shouldIgnoreInAutomation(cr)) continue;
            result.add(BasinRecipe.convertShapeless(cr));
        }
        return result;
    }

    private List<FillingRecipe> collectInfusionGunRecipes() {
        List<FillingRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.FILLING.getType())) {
            if (r instanceof FillingRecipe f) result.add(f);
        }
        if (ingredientManager != null) {
            SpoutCategory.consumeRecipes(result::add, ingredientManager);
        }
        return result;
    }

    private static List<MillingRecipe> collectMillingRecipes() {
        List<MillingRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.MILLING.getType())) {
            if (r instanceof MillingRecipe m) result.add(m);
        }
        return result;
    }

    private static List<CrushingRecipe> collectCrushingRecipes() {
        List<CrushingRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.CRUSHING.getType())) {
            if (r instanceof CrushingRecipe c) result.add(c);
        }
        return result;
    }

    private static List<BasinRecipe> collectMixingRecipes() {
        List<BasinRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.MIXING.getType())) {
            if (r instanceof BasinRecipe b) result.add(b);
        }
        return result;
    }

    private static List<BasinRecipe> collectAutoShapelessRecipes() {
        List<BasinRecipe> result = new ArrayList<>();
        if (!AllConfigs.server().recipes.allowShapelessInMixer.get()) return result;

        for (Recipe<?> r : CreateJEI.getTypedRecipes(RecipeType.CRAFTING)) {
            if (!(r instanceof CraftingRecipe cr)) continue;
            if (cr instanceof ShapedRecipe) continue;
            if (cr.getIngredients().size() <= 1) continue;
            if (MechanicalPressBlockEntity.canCompress(cr)) continue;
            if (AllRecipeTypes.shouldIgnoreInAutomation(cr)) continue;
            result.add(BasinRecipe.convertShapeless(cr));
        }
        return result;
    }

    private static List<BasinRecipe> collectAutoBrewingRecipes() {
        // ★ 1.20.1：Create 1.20.1 的 PotionMixingRecipes API 与 1.21+ 不同，
        //   暂时返回空列表，JEI 里"自动酿造"分类会显示为空
        return new ArrayList<>();
    }

    private static List<ItemApplicationRecipe> collectPointerRecipes() {
        List<ItemApplicationRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.DEPLOYING.getType())) {
            if (r instanceof ItemApplicationRecipe ra) result.add(ra);
        }
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.ITEM_APPLICATION.getType())) {
            if (r instanceof ItemApplicationRecipe ra) result.add(ra);
        }
        return result;
    }

    private static List<AbstractCookingRecipe> collectBellowsBlastingRecipes() {
        List<AbstractCookingRecipe> result = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level == null) return result;
        result.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING));
        result.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING));
        return result;
    }

    private static List<AbstractCookingRecipe> collectBellowsSmokingRecipes() {
        List<AbstractCookingRecipe> result = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level == null) return result;
        result.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING));
        return result;
    }

    private static List<HauntingRecipe> collectBellowsHauntingRecipes() {
        List<HauntingRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.HAUNTING.getType())) {
            if (r instanceof HauntingRecipe h) result.add(h);
        }
        return result;
    }

    private static List<SplashingRecipe> collectBellowsSplashingRecipes() {
        List<SplashingRecipe> result = new ArrayList<>();
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.SPLASHING.getType())) {
            if (r instanceof SplashingRecipe s) result.add(s);
        }
        return result;
    }
}