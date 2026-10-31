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
import com.alben.createhandmade.compat.jei.category.HandSawStonecuttingCategory;
import com.alben.createhandmade.compat.jei.category.MortarMillingCategory;
import com.alben.createhandmade.compat.jei.category.PointerApplicationCategory;
import com.alben.createhandmade.compat.jei.category.StirringStaffMixingCategory;
import com.alben.createhandmade.registry.ModItems;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.type.HandMadeStonecuttingRecipe;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.alben.createhandmade.compat.jei.category.BellowsExclusiveCategory;
import com.alben.createhandmade.recipe.type.HandMadeBellowsRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.compat.jei.CreateJEI;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.category.BlockCuttingCategory;
import com.simibubi.create.compat.jei.category.BlockCuttingCategory.CondensedBlockCuttingRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.SpoutCategory;
import com.simibubi.create.content.fluids.potion.PotionMixingRecipes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
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
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

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

        // ==================== 手锯切石 ====================
        CreateRecipeCategory.Info<CondensedBlockCuttingRecipe> handSawStonecuttingInfo =
                new CreateRecipeCategory.Info<>(
                        ModJeiTypes.HAND_SAW_STONECUTTING,
                        Component.translatable("jei.create_hand_made.hand_saw_stonecutting"),
                        new EmptyBackground(177, 70),
                        new DoubleItemIcon(
                                () -> new ItemStack(ModItems.HAND_SAW.get()),
                                () -> new ItemStack(Items.STONECUTTER)),
                        CreateHandMadeJEI::collectHandSawStonecuttingRecipes,
                        List.of(() -> new ItemStack(ModItems.HAND_SAW.get()))
                );
        allCategories.add(new HandSawStonecuttingCategory(handSawStonecuttingInfo));

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
        CreateRecipeCategory.Info<AbstractCrushingRecipe> mortarMillingInfo =
                new CreateRecipeCategory.Info<>(
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

        // ==================== 碾钵 · 粉碎（含研磨备选） ====================
        CreateRecipeCategory.Info<AbstractCrushingRecipe> crusherMortarCrushingInfo =
                new CreateRecipeCategory.Info<>(
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
        CreateRecipeCategory.Info<ItemApplicationRecipe> pointerApplicationInfo =
                new CreateRecipeCategory.Info<>(
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

            // ==================== 风箱 · 第三方兼容 ====================
        CreateRecipeCategory.Info<HandMadeBellowsRecipe> bellowsExclusiveInfo =
                new CreateRecipeCategory.Info<HandMadeBellowsRecipe>(
                        ModJeiTypes.BELLOWS_EXCLUSIVE,
                        Component.translatable("jei.create_hand_made.bellows_exclusive"),
                        new EmptyBackground(177, 85),
                        new DoubleItemIcon(
                                () -> new ItemStack(ModItems.BELLOWS.get()),
                                () -> new ItemStack(Items.FIRE_CHARGE)),
                        BellowsExclusiveCategory::collectThirdPartyRecipes,
                        List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
                );
        CreateHandMade.LOGGER.info("[HandMade-JEI] before exclusive add, size={}", allCategories.size());
        allCategories.add(new BellowsExclusiveCategory(bellowsExclusiveInfo));  
        CreateHandMade.LOGGER.info("[HandMade-JEI] after exclusive add, size={}", allCategories.size());      
        CreateHandMade.LOGGER.info("[HandMade-JEI] before register, size={}", allCategories.size());
        registration.addRecipeCategories(allCategories.toArray(CreateRecipeCategory[]::new));
        
    }

        @Override
    public void registerRecipes(IRecipeRegistration registration) {
        this.ingredientManager = registration.getIngredientManager();
        allCategories.forEach(c -> c.registerRecipes(registration));
    }

    // ==================== 配方收集 ====================
        @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        allCategories.forEach(c -> c.registerCatalysts(registration));
        registerThirdPartyFanCatalysts(registration);
    }

    
    /**
     * 把风箱注册成第三方模组鼓风类别的催化剂。
     *
     * <p>遍历 Create 的 FAN_PROCESSING_TYPE 注册表拼候选 UID
     * （{@code <ns>:fan_<name>} 和 {@code <ns>:<name>}），
     * 再用 {@code getAllRecipeTypes} 匹配 JEI 已注册的类别。</p>
     *
     * <p>不用 {@code getRecipeType(uid)}：JEI 15.20.0.106 对不存在的 UID 可能抛异常。</p>
     */
    private void registerThirdPartyFanCatalysts(IRecipeCatalystRegistration registration) {
        ItemStack bellows = new ItemStack(ModItems.BELLOWS.get());
        Set<ResourceLocation> registered = new HashSet<>();

        // 候选 UID
        Set<ResourceLocation> candidates = new HashSet<>();
        for (ResourceLocation fanTypeId : com.simibubi.create.api.registry.CreateBuiltInRegistries
                .FAN_PROCESSING_TYPE.keySet()) {
            String ns = fanTypeId.getNamespace();
            String name = fanTypeId.getPath();
            if (ns.equals("create") && List.of("blasting", "smoking", "haunting", "splashing").contains(name)) {
                continue;
            }
            candidates.add(new ResourceLocation(ns, "fan_" + name));
            candidates.add(new ResourceLocation(ns, name));
        }

        // 遍历 JEI 已注册类别，用候选集合匹配
        for (mezz.jei.api.recipe.RecipeType<?> type : registration.getJeiHelpers()
                .getAllRecipeTypes().toList()) {
            ResourceLocation uid = type.getUid();
            if (!candidates.contains(uid)) continue;
            if (!registered.add(uid)) continue;

            registration.addRecipeCatalyst(bellows, type);

            // 反推 fan_type id，注册介质
            ResourceLocation fanTypeId = uid.getPath().startsWith("fan_")
                    ? new ResourceLocation(uid.getNamespace(), uid.getPath().substring(4))
                    : uid;
            for (ItemStack media : BellowsMediaRegistry.getMediaFor(fanTypeId)) {
                if (!media.isEmpty()) {
                    registration.addRecipeCatalyst(media, type);
                }
            }
        }

        if (CreateHandMade.LOGGER.isDebugEnabled()) {
            CreateHandMade.LOGGER.debug(
                    "[HandMade] Registered bellows catalyst into {} third-party fan categories: {}",
                    registered.size(), registered);
        }
    }

    /**
     * 手锯切削：L1（CuttingRecipe）+ L3（HandMadeCuttingRecipe extends CuttingRecipe）。
     */
    private static List<CuttingRecipe> collectHandSawRecipes() {
        List<CuttingRecipe> result = new ArrayList<>();

        // L1：Create 的切削
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.CUTTING.getType())) {
            if (r instanceof CuttingRecipe c) result.add(c);
        }

        // L3：独立 type（HandMadeCuttingRecipe 也是 CuttingRecipe 子类）
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (Recipe<?> r : level.getRecipeManager()
                    .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
                if (!(r instanceof CuttingRecipe c)) continue;
                if (!(r instanceof HandMadeToolRecipeLike like)) continue;
                if (like.getTool() != ToolType.HAND_SAW) continue;
                result.add(c);
            }
        }

        return result;
    }

    /**
     * 手锯切石：L1（原版 minecraft:stonecutting）+ L3（HandMadeStonecuttingRecipe）。
     */
    private static List<CondensedBlockCuttingRecipe> collectHandSawStonecuttingRecipes() {
        List<Recipe<?>> recipes = new ArrayList<>();

        // L1：原版切石
        for (Recipe<?> r : CreateJEI.getTypedRecipes(RecipeType.STONECUTTING)) {
            if (r instanceof StonecutterRecipe) recipes.add(r);
        }

        // L3：独立 type 的切石
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (HandMadeStonecuttingRecipe r : level.getRecipeManager()
                    .getAllRecipesFor(HandMadeRecipeTypes.STONECUTTING_RECIPE.get())) {
                recipes.add(r);
            }
        }

        return BlockCuttingCategory.condenseRecipes(recipes);
    }

    /**
     * 冲压锤 · 置物台：L1（PressingRecipe）+ L3（HandMadePressingRecipe extends PressingRecipe）。
     */
    private static List<PressingRecipe> collectPressingRecipes() {
        List<PressingRecipe> result = new ArrayList<>();

        // L1
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.PRESSING.getType())) {
            if (r instanceof PressingRecipe p) result.add(p);
        }

        // L3
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (Recipe<?> r : level.getRecipeManager()
                    .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
                if (!(r instanceof PressingRecipe p)) continue;
                if (!(r instanceof HandMadeToolRecipeLike like)) continue;
                if (like.getTool() != ToolType.PRESS_HAMMER_DEPOT) continue;
                result.add(p);
            }
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

    /**
     * 灌注枪 · 注液：L1（FillingRecipe）+ L3（HandMadeFillingRecipe extends FillingRecipe）。
     */
    private List<FillingRecipe> collectInfusionGunRecipes() {
        List<FillingRecipe> result = new ArrayList<>();

        // L1
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.FILLING.getType())) {
            if (r instanceof FillingRecipe f) result.add(f);
        }
        if (ingredientManager != null) {
            SpoutCategory.consumeRecipes(result::add, ingredientManager);
        }

        // L3
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (Recipe<?> r : level.getRecipeManager()
                    .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
                if (!(r instanceof FillingRecipe f)) continue;
                if (!(r instanceof HandMadeToolRecipeLike like)) continue;
                if (like.getTool() != ToolType.INFUSION_GUN) continue;
                result.add(f);
            }
        }

        return result;
    }

    /**
     * 研钵研磨：L1（MillingRecipe）+ L3（HandMadeCrushingRecipe）
     * 公共父类型是 AbstractCrushingRecipe。
     */
    private static List<AbstractCrushingRecipe> collectMillingRecipes() {
        List<AbstractCrushingRecipe> result = new ArrayList<>();

        // L1：Create 的 MILLING
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.MILLING.getType())) {
            if (r instanceof AbstractCrushingRecipe m) result.add(m);
        }

        // L3：独立 type（tool=mortar）
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (Recipe<?> r : level.getRecipeManager()
                    .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
                if (!(r instanceof AbstractCrushingRecipe acr)) continue;
                if (!(r instanceof HandMadeToolRecipeLike like)) continue;
                if (like.getTool() != ToolType.MORTAR) continue;
                result.add(acr);
            }
        }

        return result;
    }

    /**
     * 碾钵粉碎：L1（CrushingRecipe + MillingRecipe）+ L3（HandMadeCrushingRecipe）。
     */
    private static List<AbstractCrushingRecipe> collectCrushingRecipes() {
        List<AbstractCrushingRecipe> result = new ArrayList<>();

        // L1：Create 的 CRUSHING
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.CRUSHING.getType())) {
            if (r instanceof AbstractCrushingRecipe c) result.add(c);
        }
        // L1：Create 的 MILLING（备选）
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.MILLING.getType())) {
            if (r instanceof AbstractCrushingRecipe m) result.add(m);
        }

        // L3：独立 type（tool=crusher_mortar 或 mortar）
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (Recipe<?> r : level.getRecipeManager()
                    .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
                if (!(r instanceof AbstractCrushingRecipe acr)) continue;
                if (!(r instanceof HandMadeToolRecipeLike like)) continue;
                ToolType owner = like.getTool();
                if (owner != ToolType.CRUSHER_MORTAR && owner != ToolType.MORTAR) continue;
                result.add(acr);
            }
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
        List<BasinRecipe> result = new ArrayList<>();
        if (!AllConfigs.server().recipes.allowBrewingInMixer.get()) return result;

        result.addAll(PotionMixingRecipes.ALL);
        return result;
    }

    /**
     * 指杆 · 应用：L1（ItemApplicationRecipe）+ L3（HandMadeApplicationRecipe extends ItemApplicationRecipe）。
     */
    private static List<ItemApplicationRecipe> collectPointerRecipes() {
        List<ItemApplicationRecipe> result = new ArrayList<>();

        // L1：Create 的 DEPLOYING
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.DEPLOYING.getType())) {
            if (r instanceof ItemApplicationRecipe ra) result.add(ra);
        }
        // L1：Create 的 ITEM_APPLICATION
        for (Recipe<?> r : CreateJEI.getTypedRecipes(AllRecipeTypes.ITEM_APPLICATION.getType())) {
            if (r instanceof ItemApplicationRecipe ra) result.add(ra);
        }

        // L3：独立 type
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (Recipe<?> r : level.getRecipeManager()
                    .getAllRecipesFor(HandMadeRecipeTypes.TOOL_RECIPE.get())) {
                if (!(r instanceof ItemApplicationRecipe iar)) continue;
                if (!(r instanceof HandMadeToolRecipeLike like)) continue;
                if (like.getTool() != ToolType.POINTER) continue;
                result.add(iar);
            }
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
