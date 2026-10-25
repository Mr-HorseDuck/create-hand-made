package com.alben.createhandmade.compat.jei;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.compat.jei.category.BellowsCookingCategory;
import com.alben.createhandmade.compat.jei.category.BellowsExclusiveCategory;
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
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.HandMadeApplicationRecipe;
import com.alben.createhandmade.recipe.HandMadeBellowsRecipe;
import com.alben.createhandmade.recipe.HandMadeCrushingRecipe;
import com.alben.createhandmade.recipe.HandMadeCuttingRecipe;
import com.alben.createhandmade.recipe.HandMadePressingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.recipe.HandMadeToolRecipe;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.compat.jei.CreateJEI;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.category.BlockCuttingCategory;
import com.simibubi.create.compat.jei.category.BlockCuttingCategory.CondensedBlockCuttingRecipe;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.SpoutCategory;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IJeiHelpers;
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
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@JeiPlugin
public class CreateHandMadeJEI implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(CreateHandMade.MODID, "jei_plugin");

    private final List<CreateRecipeCategory<?>> allCategories = new ArrayList<>();
    private IIngredientManager ingredientManager;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        allCategories.clear();   // ★ 照抄 Create
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
        // 背景必须 177×70（不是手锯切削用的 177×55）：本类别复用了 Create 的
        // BlockCuttingCategory 布局，产物网格纵向跨 y 10..66，55 高会画到背景外。
        CreateRecipeCategory.Info<CondensedBlockCuttingRecipe> handSawStonecuttingInfo =
                new CreateRecipeCategory.Info<>(
                        ModJeiTypes.HAND_SAW_STONECUTTING,
                        Component.translatable("jei.create_hand_made.hand_saw_stonecutting"),
                        new EmptyBackground(177, 70),
                        new DoubleItemIcon(
                                () -> new ItemStack(ModItems.HAND_SAW.get()),
                                () -> new ItemStack(Items.STONE_BRICK_STAIRS)),
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

// ==================== 冲压锤 · 工作盆（自动摆放 4/9 合 1） ★ 新增 ====================
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

        // ==================== 碾钵 · 粉碎 + 研磨 ====================
        // 类型用 AbstractCrushingRecipe（CRUSHING 与 MILLING 的共同父类），
        // 类别显示名仍是"碾钵粉碎"，但内容同时包含两种配方。
        CreateRecipeCategory.Info<AbstractCrushingRecipe> crusherMortarCrushingInfo = new CreateRecipeCategory.Info<>(
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

// ==================== 搅拌杖 · 自动无序合成 ★ 新增 ====================
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

// ==================== 搅拌杖 · 自动酿造 ★ 新增 ====================
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

        // ==================== 指杆 · 应用（部署 + 物品应用） ====================
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
                () -> BellowsMediaRegistry.getMediaFor(fanTypeId(AllFanProcessingTypes.BLASTING))));

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
                () -> BellowsMediaRegistry.getMediaFor(fanTypeId(AllFanProcessingTypes.SMOKING))));

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
                () -> BellowsMediaRegistry.getMediaFor(fanTypeId(AllFanProcessingTypes.HAUNTING))));

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
                () -> BellowsMediaRegistry.getMediaFor(fanTypeId(AllFanProcessingTypes.SPLASHING))));

        // ==================== 风箱 · 第三方兼容（非 create 命名空间） ====================
        // T11 B6/B7：4 个原版类别只收 fan_type 等于对应 Create 内置类型的独占配方，
        // 第三方类型（dndesires:sanding / create_more_catalysts:snow_flow …）原本在 JEI 里看不到。
        // 这个类别只收第三方类型，避免与原版 4 类重复；介质图标按每条配方自己的 fan_type 查，
        // 页面上还会写出 fan_type 的 id。
        CreateRecipeCategory.Info<HandMadeBellowsRecipe> bellowsExclusiveInfo = new CreateRecipeCategory.Info<>(
                ModJeiTypes.BELLOWS_EXCLUSIVE,
                Component.translatable("jei.create_hand_made.bellows_exclusive"),
                new EmptyBackground(177, 70),
                new DoubleItemIcon(
                        () -> new ItemStack(ModItems.BELLOWS.get()),
                        () -> new ItemStack(Items.DRAGON_HEAD)),
                CreateHandMadeJEI::collectThirdPartyBellowsExclusive,
                List.of(() -> new ItemStack(ModItems.BELLOWS.get()))
        );
        allCategories.add(new BellowsExclusiveCategory(bellowsExclusiveInfo));

        registration.addRecipeCategories(allCategories.toArray(CreateRecipeCategory[]::new));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        this.ingredientManager = registration.getIngredientManager();
        allCategories.forEach(c -> c.registerRecipes(registration));

        // ★ T11 B8 诊断：把"第三方兼容"类别的条数打出来。
        //   如果这里是 0，说明当前数据包里没有 fan_type 属于第三方模组的 bellows_recipe
        //   （数据包没启用 / 没装对应模组 / fan_type 写成了 create 命名空间）。
        CreateHandMade.LOGGER.debug("JEI: third-party bellows recipe category has {} recipe(s)",
                collectThirdPartyBellowsExclusive().size());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        allCategories.forEach(c -> c.registerCatalysts(registration));

        // ★ T11 B6：把风箱挂到第三方鼓风类别的催化剂上
        registerThirdPartyFanCatalysts(registration);
    }

    /**
     * 把 {@code ModItems.BELLOWS} 注册成第三方模组鼓风类别的催化剂（T11 B6）。
     *
     * <h3>做法：用 JEI 的 RecipeType 注册表按 UID 反查，不碰第三方类</h3>
     * <p>反编译三个模组后确认，它们的鼓风 JEI 类别 UID 全部是
     * <b>{@code <命名空间>:fan_<类型名>}</b>：</p>
     * <ul>
     *   <li>DnD：{@code dndesires:fan_sanding} / {@code fan_freezing} / {@code fan_seething} /
     *       {@code fan_dragon_breathing}（{@code DesiresJEI} 里的 {@code CategoryBuilder#build}）；</li>
     *   <li>CMC：{@code create_more_catalysts:fan_withering} / {@code fan_snow_flow} /
     *       {@code fan_sand_blow} …（{@code FanCategoryRegistrar}）；</li>
     *   <li>龙+：{@code create_dragons_plus:fan_sanding} / {@code fan_freezing} / {@code fan_ending}
     *       （{@code FanSandingCategory.create()} 等）。</li>
     * </ul>
     * <p>所以只要遍历 Create 的 {@code FanProcessingType} 注册表，把每个类型 id
     * ({@code <ns>:<name>}) 拼成候选 UID {@code <ns>:fan_<name>}（找不到再退回 {@code <ns>:<name>}，
     * Create 自己的类别就是这个写法），用 {@link IJeiHelpers#getRecipeType} 反查即可 ——
     * <b>不需要反射、不需要编译期依赖</b>，也不会因为某个模组没装而报错。</p>
     *
     * <p>Create 自带的 4 类跳过：它们的类别由本模组自己的 4 个风箱类别负责
     * （而且 {@code create:blasting} 这类本来就没有同名 RecipeType，鼓风熔炼用的是
     * {@code minecraft:smelting}）。</p>
     *
     * <p><b>T11 B10：</b>除了风箱本身，还会把该类型的<b>介质物品</b>
     * （{@link BellowsMediaRegistry#getMediaFor}）一并注册成催化剂 —— 玩家打开龙+「批量终结」
     * 页面时，左侧能直接看到"该拿龙息桶/龙首"，而不是只有一个空降的风箱。</p>
     */
    private static void registerThirdPartyFanCatalysts(IRecipeCatalystRegistration registration) {
        IJeiHelpers helpers = registration.getJeiHelpers();
        ItemStack bellows = new ItemStack(ModItems.BELLOWS.get());
        List<String> added = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        java.util.Set<ResourceLocation> registeredUids = new java.util.LinkedHashSet<>();

        for (ResourceLocation typeId : CreateBuiltInRegistries.FAN_PROCESSING_TYPE.keySet()) {
            if (typeId.getNamespace().equals("create") || typeId.getNamespace().equals(CreateHandMade.MODID)) {
                continue;
            }

            mezz.jei.api.recipe.RecipeType<?> jeiType = findJeiFanType(helpers, typeId);
            if (jeiType == null) {
                // 该模组只注册了 FanProcessingType、没有对应的 JEI 类别（例如只支持真实鼓风机）
                missing.add(typeId.toString());
                continue;
            }
            // ★ 同一个类别 UID 只挂一次风箱：龙+ 的染色是"每个染料变体一个 FanProcessingType、
            //   但 JEI 只有一个 category"，不去重就会在页面上叠出一排风箱图标。
            ResourceLocation catalystUid = jeiType.getUid();
            if (!registeredUids.contains(catalystUid)) {
                registeredUids.add(catalystUid);
                registration.addRecipeCatalyst(bellows, jeiType);
            }
            // 按用户要求：龙+ / DnD 的催化剂栏<b>只挂风箱</b>（媒介是什么玩家心里有数，避免冗余）；
            // 更多触媒（CMC）本身提供了物品形态的媒介，保持"风箱 + 媒介"。
            boolean withMedia = typeId.getNamespace().equals("create_more_catalysts");
            List<ItemStack> media = withMedia ? BellowsMediaRegistry.getMediaFor(typeId) : List.of();
            for (ItemStack stack : media) {
                if (stack.isEmpty()) continue;
                registration.addRecipeCatalyst(stack, jeiType);
            }
            added.add(typeId + " -> " + catalystUid + " (+" + media.size() + " media)");
        }

        if (added.isEmpty()) {
            // 有第三方鼓风类型但一个 JEI 类别都没匹配上：把候选打出来，便于排查（不静默失败）
            CreateHandMade.LOGGER.info(
                    "JEI: no third-party fan category matched for bellows catalysts (candidates: {})", missing);
        } else {
            CreateHandMade.LOGGER.debug("JEI: bellows registered as catalyst for {} third-party fan categor(ies): {}",
                    added.size(), added);
            if (!missing.isEmpty()) {
                CreateHandMade.LOGGER.debug("JEI: fan types without a matching JEI category: {}", missing);
            }
        }

        // ★ 写死兜底：以上按"鼓风类型 → UID"推导，个别模组的类别 UID 对不上时整页都没有风箱。
        //   这里把已知的第三方鼓风类别 UID 直接列出来，逐个查/造再挂一次风箱（幂等去重）。
        for (String uid : HARDCODED_FAN_CATEGORY_UIDS) {
            ResourceLocation uidId = ResourceLocation.tryParse(uid);
            if (uidId == null) continue;
            if (!registeredUids.add(uidId)) continue;   // 同一类别只挂一次，避免叠出一排风箱
            mezz.jei.api.recipe.RecipeType<?> type = helpers.getRecipeType(uidId)
                    .orElseGet(() -> mezz.jei.api.recipe.RecipeType.createRecipeHolderType(uidId));
            registration.addRecipeCatalyst(bellows, type);
            CreateHandMade.LOGGER.debug("JEI: bellows catalyst added by hardcoded UID {}", uid);
        }

        // ★ 最终兜底：反射读第三方自己的 RecipeTypeInfo#getId()，用它当 JEI 类别 UID。
        //   现场诊断证明"按命名惯例猜 UID"对龙+ 的喷砂/冷冻/终结、DnD 的超级冶炼无效
        //   （那几页始终没有图标），而模组自己的 id 一定是它注册类别时用的那个。
        registerByThirdPartyIds(registration, bellows, registeredUids);
    }

    /** 第三方 RecipeTypeInfo 的「类名#字段名」；两家的对象都有 {@code getId()}。 */
    private static final String[][] THIRD_PARTY_RECIPE_TYPE_REFS = {
            {"plus.dragons.createdragonsplus.common.registry.CDPRecipes", "SANDING"},
            {"plus.dragons.createdragonsplus.common.registry.CDPRecipes", "FREEZING"},
            {"plus.dragons.createdragonsplus.common.registry.CDPRecipes", "ENDING"},
            {"plus.dragons.createdragonsplus.common.registry.CDPRecipes", "COLORING"},
            {"dev.lopyluna.dndesires.register.DesiresRecipeTypes", "SEETHING"},
            {"dev.lopyluna.dndesires.register.DesiresRecipeTypes", "SANDING"},
            {"dev.lopyluna.dndesires.register.DesiresRecipeTypes", "FREEZING"},
            {"dev.lopyluna.dndesires.register.DesiresRecipeTypes", "DRAGON_BREATHING"},
    };

    /**
     * 反射读第三方模组自己的配方类型 id，并据此注册风箱催化剂（T11 B10 收尾）。
     *
     * <p>为什么不再猜 UID：现场日志里龙+ 的喷砂/冷冻/终结、DnD 的超级冶炼页面
     * 用任何"命名惯例"都挂不上，而对方 {@code RecipeTypeInfo#getId()} 返回的 id
     * 就是它注册 JEI 类别时用的那个。JAR 里这两个类都是 public、常量字段也是 public，
     * 反射不需要给构建加任何依赖；模组没装时 {@link ClassNotFoundException} 直接跳过。</p>
     */
    private static void registerByThirdPartyIds(IRecipeCatalystRegistration registration, ItemStack bellows,
                                                java.util.Set<ResourceLocation> registeredUids) {
        for (String[] ref : THIRD_PARTY_RECIPE_TYPE_REFS) {
            try {
                Class<?> holder = Class.forName(ref[0]);
                Object info = holder.getField(ref[1]).get(null);
                Object id = info.getClass().getMethod("getId").invoke(info);
                if (!(id instanceof ResourceLocation typeId)) continue;
                if (!registeredUids.add(typeId)) continue;

                registration.addRecipeCatalyst(bellows,
                        mezz.jei.api.recipe.RecipeType.createRecipeHolderType(typeId));
                CreateHandMade.LOGGER.debug("JEI: bellows catalyst added for {}#{} (id {})", ref[0], ref[1], typeId);
            } catch (Throwable ignored) {
                // 模组没装（ClassNotFound）/ 字段改名 / 方法缺失 —— 都是"这条兼容不可用"，静默跳过
            }
        }
    }

    /**
     * 写死的第三方鼓风类别 UID（按用户现场反馈补的）：
     * 龙+ 的批量喷砂/冷冻/终结/染色 + DnD 的磨砂/冷冻/超级冶炼/龙息。
     */
    private static final List<String> HARDCODED_FAN_CATEGORY_UIDS = List.of(
            "create_dragons_plus:fan_sanding",
            "create_dragons_plus:fan_freezing",
            "create_dragons_plus:fan_ending",
            "create_dragons_plus:fan_coloring",
            "dndesires:fan_sanding",
            "dndesires:fan_freezing",
            "dndesires:fan_seething",
            "dndesires:seething",
            "dndesires:bulk_seething",
            "dndesires:fan_bulk_seething",
            "dndesires:seething_catalyst",
            "dndesires:fan_dragon_breathing",
            "create:bulk_seething", "create:seething", "create:fan_bulk_seething",
            "create:bulk_sanding", "create:bulk_freezing");

    /**
     * 按 UID 反查某个鼓风类型对应的 JEI 类别类型。
     *
     * <p>先试 {@code <ns>:fan_<name>}（三家第三方模组的实测惯例），再退回 {@code <ns>:<name>}
     * （Create 自身与部分模组的写法）。都没有就返回 {@code null}。</p>
     */
    @Nullable
    private static mezz.jei.api.recipe.RecipeType<?> findJeiFanType(IJeiHelpers helpers, ResourceLocation fanTypeId) {
        List<ResourceLocation> candidates = fanTypeUidCandidates(fanTypeId);
        for (ResourceLocation uid : candidates) {
            Optional<mezz.jei.api.recipe.RecipeType<?>> found = helpers.getRecipeType(uid);
            if (found.isPresent()) return found.get();
        }
        // ★ 用户实测兜底：龙+ 的批量染色/喷砂/终结类别在"注册催化剂"这个时机查不到
        //   （JEI 的 RecipeType 注册表那时可能还没填完）。
        //   JEI 内部按 UID 匹配类别，所以直接按最可能的 UID 造一个即可 ——
        //   只要对方类别用的就是这个 UID，催化剂就会挂上去；UID 不存在则 JEI 静默忽略。
        return mezz.jei.api.recipe.RecipeType.createRecipeHolderType(candidates.get(0));
    }

    /** 一个鼓风类型可能对应的 JEI 类别 UID（按可能性排序）；含写死的例外表。 */
    private static List<ResourceLocation> fanTypeUidCandidates(ResourceLocation fanTypeId) {
        ResourceLocation override = FAN_TYPE_UID_OVERRIDES.get(fanTypeId);
        if (override != null) return List.of(override);

        String ns = fanTypeId.getNamespace();
        String path = fanTypeId.getPath();
        // 龙+ 的染色类型按染料变体各注册一个（coloring_black…），但 JEI 只有<b>一个</b> coloring 类别
        if (path.startsWith("coloring_")) {
            return List.of(jeiUid(ns, "fan_coloring"), jeiUid(ns, "coloring"), fanTypeId);
        }
        // DnD 的鼓风类别注册在 create 命名空间下（页面标题就是 "Bulk Seething"、模组显示 Create），
        // 所以 create:bulk_<name> 必须排第一位；旧候选保留做兜底。
        if (ns.equals("dndesires")) {
            return List.of(jeiUid("create", "fan_" + path), jeiUid("create", "bulk_" + path),
                    jeiUid("create", path), jeiUid("create", "fan_bulk_" + path), jeiUid(ns, "fan_" + path),
                    jeiUid(ns, path), fanTypeId);
        }
        return List.of(jeiUid(ns, "fan_" + path), jeiUid("create", "bulk_" + path), jeiUid("create", path),
                jeiUid("create", "fan_bulk_" + path), jeiUid(ns, "bulk_" + path), jeiUid(ns, path), fanTypeId);
    }

    /**
     * JEI 运行时到达后核对：那 8 个第三方类别 UID 在 JEI 里到底存不存在。
     *
     * <p>催化剂只能在注册阶段添加（JEI 运行时 API 没有 addRecipeCatalyst），所以这里只做诊断：
     * 若某个 UID 在运行时"不存在"，说明我们注册阶段是<b>现造实例</b>挂上去的（必然不显示）；
     * 若"存在"却仍不显示，就是注册时机/插件顺序问题，需要改注册策略。</p>
     */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        // 枚举 JEI 里真实存在的全部类别 UID —— 这是唯一可靠的"真实 ID"来源
        // （IRecipeManager#getRecipeType 只认注册过配方条目的类型，之前用它做判据是错的）。
        List<String> fanCategories = new ArrayList<>();
        try {
            runtime.getRecipeManager()
                    .createRecipeCategoryLookup()
                    .includeHidden()
                    .get()
                    .forEach(category -> {
                        ResourceLocation uid = category.getRecipeType().getUid();
                        String ns = uid.getNamespace();
                        if (ns.equals("create") || ns.equals("create_dragons_plus") || ns.equals("dndesires")
                                || ns.equals("create_more_catalysts")) {
                            fanCategories.add(uid + " [" + category.getTitle().getString() + "]");
                        }
                    });
        } catch (Throwable t) {
            CreateHandMade.LOGGER.warn("JEI: failed to enumerate recipe categories", t);
        }
        CreateHandMade.LOGGER.debug("JEI runtime: fan category UIDs = {}", fanCategories);
    }

    private static ResourceLocation jeiUid(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** 写死的 UID 例外：与 {@link #fanTypeUidCandidates} 的通用规则不一致的类别。 */
    private static final java.util.Map<ResourceLocation, ResourceLocation> FAN_TYPE_UID_OVERRIDES =
            java.util.Map.of(
                    jeiUid("create_dragons_plus", "sanding"), jeiUid("create_dragons_plus", "fan_sanding"),
                    jeiUid("create_dragons_plus", "freezing"), jeiUid("create_dragons_plus", "fan_freezing"),
                    jeiUid("create_dragons_plus", "ending"), jeiUid("create_dragons_plus", "fan_ending"));

    // ==================== 配方收集 ====================

    /**
     * 从统一配方池收集某个工具类别的配方，并按目标类型过滤。
     *
     * <p>JEI 与游戏内工具现在共用同一份候选集（{@link HandMadeRecipePool}）：
     * 池负责「收集候选」，本方法只做类型对齐，不再重复任何筛选规则 ——
     * 那些规则（config 开关、MechanicalCraftingRecipe 排除、压缩判定、
     * automation 忽略……）都已经在池里。</p>
     *
     * <p>level 为 null（尚未进入世界）时返回空列表。</p>
     *
     * @param tool  工具 + 配方类型组合
     * @param clazz 目标配方类型，用于过滤与泛型对齐
     * @return 该类别下所有 {@code clazz} 类型的配方
     */
    private static <T extends Recipe<?>> List<RecipeHolder<T>> collectFromPool(HandMadeTool tool, Class<T> clazz) {
        List<RecipeHolder<T>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(tool, level)) {
            // isInstance + cast 是类型安全的，不需要 unchecked 强转。
            if (clazz.isInstance(holder.value())) {
                result.add(new RecipeHolder<>(holder.id(), clazz.cast(holder.value())));
            }
        }
        return result;
    }

    /**
     * 从配方池收集某个工具类别的配方，并把 <b>L3 独占配方</b>代理成类别所需的 Create 配方类型。
     *
     * <p>L3 独占配方按家族使用不同的配方类（basin {@link HandMadeToolRecipe} / 碾磨
     * {@link HandMadeCrushingRecipe} / 冲压 {@link HandMadePressingRecipe} / 切削
     * {@link HandMadeCuttingRecipe}），它们都不是对应 JEI 类别所用的那个具体 Create 类，
     * 所以 {@link #collectFromPool} 的 {@code clazz.isInstance(...)} 会把独占配方丢掉。
     * 本方法在类型不匹配时用同一份 {@link ProcessingRecipeParams} 造一个<b>显示代理</b>
     * 供渲染（代理不会注册进配方管理器，也不参与游戏内匹配）。</p>
     *
     * <p>只有归属工具正好等于本类别工具的独占配方会被代理；配方池本身也按归属过滤，
     * 这里再判一次是为了让本方法不依赖池的实现细节。</p>
     *
     * <p>例外：碾钵类别用的是 {@link AbstractCrushingRecipe}（碾磨家族类的父类），
     * 独占配方天然通过 {@code clazz.isInstance(...)}，因此继续用 {@link #collectFromPool}，
     * 不需要代理。</p>
     *
     * @param tool         工具 + 配方类型组合
     * @param clazz        本类别需要的配方类型
     * @param proxyFactory 用 params 造代理对象的工厂（例如 {@code MillingRecipe::new}）
     */
    private static <T extends Recipe<?>> List<RecipeHolder<T>> collectFromPoolWithProxy(
            HandMadeTool tool, Class<T> clazz, Function<ProcessingRecipeParams, T> proxyFactory) {
        List<RecipeHolder<T>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(tool, level)) {
            Object value = holder.value();

            // isInstance + cast 是类型安全的，不需要 unchecked 强转。
            if (clazz.isInstance(value)) {
                result.add(new RecipeHolder<>(holder.id(), clazz.cast(value)));
                continue;
            }

            if (value instanceof HandMadeToolRecipeLike exclusive
                    && exclusive.getTool() == tool
                    && value instanceof StandardProcessingRecipe<?> processing) {
                result.add(new RecipeHolder<>(holder.id(), proxyFactory.apply(processing.getParams())));
            }
        }
        return result;
    }

    /** 手锯 · 切削（CUTTING）。 */
    private static List<RecipeHolder<CuttingRecipe>> collectHandSawRecipes() {
        return collectFromPoolWithProxy(HandMadeTool.HAND_SAW, CuttingRecipe.class, CuttingRecipe::new);
    }

    /**
     * 手锯 · 切石（原版 {@code minecraft:stonecutting} + L3 {@code create_hand_made:stonecutting_recipe}）。
     *
     * <p><b>为什么走 {@link BlockCuttingCategory#condenseRecipes}：</b>切石是"一个输入 → 多条产物"
     * 的形态（原版单输入最多 16 条），逐条平铺会让 JEI 里出现几十行同输入的配方。Create 的
     * 块切类别为此提供了公开的折叠助手：它接收 {@code List<RecipeHolder<?>>}（内部只用
     * {@code getIngredients()} / {@code getResultItem(...)} 这两个通用 {@code Recipe} API，
     * 不检查配方类），把同输入的配方折成一条 {@code CondensedBlockCuttingRecipe}。</p>
     *
     * <p><b>为什么不需要显示代理：</b>本模组的 L3 配方 {@code HandMadeStonecuttingRecipe} 继承
     * 原版 {@code SingleItemRecipe}（不是 {@code StonecutterRecipe}），但折叠助手对它一视同仁 ——
     * 前面说的两个通用 API 它都有实现。所以直接把配方池的候选集喂进去即可。</p>
     *
     * <p><b>候选集为什么取自配方池：</b>池里 L1（原版切石）在前、L3（本模组独占）在后，
     * 且池自带两道闸：第一步是 Create 配置 {@code allowStonecuttingOnSaw} 的门，最后一步是 L2
     * 过滤。所以本类别的显示内容与游戏内手锯的候选集完全一致（关配置 → 返回空列表 → JEI 不
     * 显示该类别；被 L2 禁用的配方 → 也不显示）。</p>
     */
    private static List<RecipeHolder<CondensedBlockCuttingRecipe>> collectHandSawStonecuttingRecipes() {
        Level level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return BlockCuttingCategory.condenseRecipes(
                HandMadeRecipePool.getBaseRecipes(HandMadeTool.HAND_SAW_STONECUTTING, level));
    }

    /** 冲压锤 · 置物台（PRESSING）。 */
    private static List<RecipeHolder<PressingRecipe>> collectPressingRecipes() {
        return collectFromPoolWithProxy(HandMadeTool.PRESS_HAMMER_DEPOT, PressingRecipe.class, PressingRecipe::new);
    }

    /** 冲压锤 · 工作盆 · 打包（COMPACTING）。 */
    private static List<RecipeHolder<BasinRecipe>> collectCompactingRecipes() {
        return collectFromPool(HandMadeTool.PRESS_HAMMER_BASIN, BasinRecipe.class);
    }

    /**
     * 冲压锤 · 工作盆 · 自动摆放（4/9 合 1）。
     *
     * <p>配方池的 {@link HandMadeTool#PRESS_HAMMER_AUTO_SQUARE} 只提供「可压缩」的
     * CraftingRecipe（config 开关、压缩判定、automation 忽略都已在池里），
     * 这里只负责把它包成 BasinRecipe 以便 BasinCategory 渲染。</p>
     */
    private static List<RecipeHolder<BasinRecipe>> collectAutoSquareRecipes() {
        return wrapAsBasin(HandMadeTool.PRESS_HAMMER_AUTO_SQUARE);
    }

    /** 灌注枪 · 注液（FILLING），再追加 SpoutCategory 的转换配方。 */
    private List<RecipeHolder<FillingRecipe>> collectInfusionGunRecipes() {
        List<RecipeHolder<FillingRecipe>> result =
                collectFromPoolWithProxy(HandMadeTool.INFUSION_GUN, FillingRecipe.class, FillingRecipe::new);

        if (ingredientManager != null) {
            SpoutCategory.consumeRecipes(result::add, ingredientManager);
        }

        return result;
    }

    /**
     * 研钵 · 研磨（MILLING）。
     *
     * <p>候选集来自配方池，池里除了 Create 的 {@link MillingRecipe}，还可能有 L3 独占配方
     * {@link HandMadeCrushingRecipe}（碾磨家族，它是 {@code AbstractCrushingRecipe} 的子类，
     * <b>不是</b> {@code MillingRecipe} 的子类）。本类别的类型参数是 {@code MillingRecipe}，
     * 若直接走 {@code collectFromPool}，它的 {@code clazz.isInstance(...)} 会把独占配方丢掉，
     * 所以这里对独占配方做一次<b>显示代理</b>：用同一份 {@code ProcessingRecipeParams}
     * 造一个 {@code MillingRecipe} 供渲染。</p>
     *
     * <p>代理对象只服务于 JEI 展示（{@code MillingRecipe} 的构造是 public，
     * {@code ProcessingRecipe.getParams()} 也是 public），不会注册进配方管理器，
     * 也不参与游戏内匹配。</p>
     */
    private static List<RecipeHolder<MillingRecipe>> collectMillingRecipes() {
        return collectFromPoolWithProxy(HandMadeTool.MORTAR, MillingRecipe.class, MillingRecipe::new);
    }

    /**
     * 碾钵 · 粉碎 + 研磨。
     *
     * <p>配方池的 {@link HandMadeTool#CRUSHER_MORTAR} 本来就把 CRUSHING 与 MILLING
     * 都收进来了（游戏内是「先粉碎、后研磨」，池里 CRUSHING 在前、MILLING 在后）。
     * 这里按 {@link AbstractCrushingRecipe} 过滤 —— 它是两者的共同父类，
     * 因此两类都会进本类别，且保持"粉碎在前"的顺序。</p>
     */
    private static List<RecipeHolder<AbstractCrushingRecipe>> collectCrushingRecipes() {
        return collectFromPool(HandMadeTool.CRUSHER_MORTAR, AbstractCrushingRecipe.class);
    }

    /** 搅拌杖 · 混合（MIXING）。 */
    private static List<RecipeHolder<BasinRecipe>> collectMixingRecipes() {
        return collectFromPool(HandMadeTool.STIRRING_STAFF, BasinRecipe.class);
    }
    /**
     * 搅拌杖 · 自动无序合成。
     *
     * <p>配方池的 {@link HandMadeTool#STIRRING_STAFF_AUTO_SHAPELESS} 只提供
     * 符合条件的无序 CraftingRecipe（config 开关、ShapedRecipe 排除、原料数、
     * 压缩判定、automation 忽略都已在池里），这里只负责包成 BasinRecipe 以便渲染。</p>
     */
    private static List<RecipeHolder<BasinRecipe>> collectAutoShapelessRecipes() {
        return wrapAsBasin(HandMadeTool.STIRRING_STAFF_AUTO_SHAPELESS);
    }

    /**
     * 搅拌杖 · 自动酿造。
     *
     * <p>直接消费配方池的 {@link HandMadeTool#STIRRING_STAFF_AUTO_BREWING}
     * —— 池内部已经做完 {@code PotionMixingRecipes.createRecipes} 与泛型上推，
     * 并含 {@code allowBrewingInMixer} 开关判断。这里只剩一次类型对齐，
     * JEI 侧的 unchecked cast 是必要的。</p>
     *
     * <p>注意：游戏内的自动酿造<b>不走</b>这条路径 —— 它用
     * {@code PotionMixingRecipes.sortRecipesByItem} 的按物品索引直查，
     * 池里的这一条只服务于本类别。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<BasinRecipe>> collectAutoBrewingRecipes() {
        Level level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return (List) HandMadeRecipePool.getBaseRecipes(HandMadeTool.STIRRING_STAFF_AUTO_BREWING, level);
    }

    /**
     * 指杆 · 应用（DEPLOYING + ITEM_APPLICATION，前者在前）。
     *
     * <p>候选集来自配方池，池里除了 Create 的 {@link ItemApplicationRecipe} 家族，还可能有
     * L3 独占配方 {@link HandMadeApplicationRecipe}。本类别的类型参数是
     * {@link ItemApplicationRecipe}，而独占配方不是它的子类，所以要造<b>显示代理</b>。</p>
     *
     * <p><b>为什么不能走 {@link #collectFromPoolWithProxy}：</b>那个 helper 的代理工厂签名是
     * {@code ProcessingRecipeParams -> T}，但 Create 的 {@link DeployerApplicationRecipe}
     * 要求的是 {@code ItemApplicationRecipeParams}
     * （{@code DeployerApplicationRecipe.java:27-29} → {@code ItemApplicationRecipe.java:23}）——
     * 直接传普通 params 会 ClassCastException；而且代理还需要配方 id 与原料/产物本身，
     * 不只是 params。所以这里改用 Create 公开的 {@code ItemApplicationRecipe.Builder}
     * （它内部创建正确的 params 类型，并支持 {@code toolNotConsumed()}）。</p>
     *
     * <p>代理只用于 JEI 展示，不会注册进配方管理器，也不参与游戏内匹配。</p>
     */
    private static List<RecipeHolder<ItemApplicationRecipe>> collectPointerRecipes() {
        List<RecipeHolder<ItemApplicationRecipe>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.POINTER, level)) {
            Object value = holder.value();

            if (value instanceof ItemApplicationRecipe application) {
                result.add(new RecipeHolder<>(holder.id(), application));
                continue;
            }

            if (value instanceof HandMadeApplicationRecipe exclusive
                    && exclusive.getTool() == HandMadeTool.POINTER) {
                result.add(new RecipeHolder<>(holder.id(), asDeployerProxy(holder.id(), exclusive)));
            }
        }
        return result;
    }

    /** 用 Create 的 builder 把一条 L3 独占配方包成 {@link DeployerApplicationRecipe}（仅展示用）。 */
    private static DeployerApplicationRecipe asDeployerProxy(ResourceLocation id, HandMadeApplicationRecipe exclusive) {
        ItemApplicationRecipe.Builder<DeployerApplicationRecipe> builder =
                new ItemApplicationRecipe.Builder<>(DeployerApplicationRecipe::new, id)
                        .withItemIngredients(exclusive.getIngredients())
                        .withItemOutputs(exclusive.getRollableResults().toArray(new ProcessingOutput[0]));
        if (exclusive.shouldKeepHeldItem()) {
            builder.toolNotConsumed();
        }
        return builder.build();
    }

    /**
     * 把配方池里某一类「工作台配方」包装成 {@link BasinRecipe}，供 BasinCategory 渲染。
     *
     * <p>配方池刻意返回原始 {@link CraftingRecipe} 而不是 BasinRecipe
     * （见 {@link HandMadeRecipePool} 里的说明：包装会让 {@code getRemainingItems}
     * 走 BasinRecipe 的默认实现，与游戏内原本的容器残留返还行为不一致）。
     * 包装只为 JEI 展示服务，所以放在这里做，且只在客户端执行
     * （{@code BasinRecipe.convertShapeless} 内部使用
     * {@code Minecraft.getInstance().level}）。</p>
     */
    private static List<RecipeHolder<BasinRecipe>> wrapAsBasin(HandMadeTool tool) {
        List<RecipeHolder<BasinRecipe>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(tool, level)) {
            result.add(BasinRecipe.convertShapeless(holder));
        }
        return result;
    }

    /**
     * 风箱 · 熔炼。
     *
     * <p>与 Create 原版「鼓风熔炼」类别（{@code CreateJEI} 里的 {@code fan_blasting}）
     * 的配方集完全一致，逐步对应它的 builder 链：</p>
     * <ol>
     *   <li>{@code addTypedRecipesExcluding(SMELTING, BLASTING)} —— 熔炼表里
     *       「输入与某条 BLASTING 相同」的跳过；</li>
     *   <li>{@code addTypedRecipes(BLASTING)} —— 再补上全部 BLASTING；</li>
     *   <li>{@code removeRecipes(SMOKING)} —— 输入与输出都与某条 SMOKING 相同的移除，
     *       <b>这一步才是把食物（生牛肉 → 熟牛肉之类）排掉的关键</b>；</li>
     *   <li>{@code removeNonAutomation()} —— 移除 id 以 {@code _manual_only} 结尾的配方。</li>
     * </ol>
     *
     * <p>前两步的顺序也照 Create 保留：SMELTING（已排除者）在前，BLASTING 在后。</p>
     *
     * <p><b>第 4 步与 Create 逐字一致：</b>Create 的 {@code removeNonAutomation()} 实现是
     * {@code recipes.removeIf(AllRecipeTypes.CAN_BE_AUTOMATED.negate())}，
     * 只看配方 id 的 {@code _manual_only} 后缀；而 {@code shouldIgnoreInAutomation}
     * 还会额外看 serializer 的 AUTOMATION_IGNORE 标签。因此这里用的是前者。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<AbstractCookingRecipe>> collectBellowsBlastingRecipes() {
        List<RecipeHolder<AbstractCookingRecipe>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        // 1. BLASTING 全表：既用作排除参照，也是最终结果的一部分。
        List<RecipeHolder<AbstractCookingRecipe>> blastingAll = new ArrayList<>();
        for (var h : level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING)) {
            blastingAll.add((RecipeHolder) h);
        }

        // 2. SMELTING 里排除「输入与某条 BLASTING 相同」的。
        for (var h : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            boolean excluded = false;
            for (RecipeHolder<AbstractCookingRecipe> b : blastingAll) {
                if (CreateJEI.doInputsMatch(h.value(), b.value())) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) {
                result.add((RecipeHolder) h);
            }
        }

        // 3. 加上全部 BLASTING。
        result.addAll(blastingAll);

        // 4. 移除「输入与输出都与某条 SMOKING 相同」的 —— 食物就是在这里被排掉的。
        List<RecipeHolder<AbstractCookingRecipe>> smokingAll = new ArrayList<>();
        for (var h : level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING)) {
            smokingAll.add((RecipeHolder) h);
        }
        result.removeIf(r -> {
            for (RecipeHolder<AbstractCookingRecipe> s : smokingAll) {
                if (CreateJEI.doInputsMatch(r.value(), s.value())
                        && CreateJEI.doOutputsMatch(r.value(), s.value())) {
                    return true;
                }
            }
            return false;
        });

        // 5. 移除 non-automation（与 Create 的 removeNonAutomation 逐字一致）。
        result.removeIf(AllRecipeTypes.CAN_BE_AUTOMATED.negate());

        // 6. 追加 L3 独占配方（create_hand_made:bellows_recipe, fan_type=blasting）。
        //    刻意放在最后：它只按 fan_type 归类，不参与上面那几步 L1 去重 / 自动化过滤
        //    （独占配方本来就是"手搓"语义，不该被 _manual_only 那套规则挡住）。
        for (RecipeHolder<HandMadeBellowsRecipe> h : collectBellowsExclusive(fanTypeId(AllFanProcessingTypes.BLASTING))) {
            AbstractCookingRecipe proxy = asBellowsCookingProxy(h.value(), true);
            if (proxy != null) {
                result.add(new RecipeHolder<>(h.id(), proxy));
            }
        }

        return result;
    }

    /**
     * 风箱 · 烟熏。
     *
     * <p>对应 Create 原版 {@code fan_smoking}：
     * {@code addTypedRecipes(SMOKING)} 之后还有一步 {@code removeNonAutomation()}。
     * 原实现只做了前半步，这里补上后半步，与 Create 一致
     * （同样用 {@code CAN_BE_AUTOMATED.negate()}，即只按 {@code _manual_only} 后缀过滤）。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<AbstractCookingRecipe>> collectBellowsSmokingRecipes() {
        List<RecipeHolder<AbstractCookingRecipe>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (var h : level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING)) {
            result.add((RecipeHolder) h);
        }
        result.removeIf(AllRecipeTypes.CAN_BE_AUTOMATED.negate());

        // L3 独占配方（fan_type=smoking）；位置与理由同 blasting
        for (RecipeHolder<HandMadeBellowsRecipe> h : collectBellowsExclusive(fanTypeId(AllFanProcessingTypes.SMOKING))) {
            AbstractCookingRecipe proxy = asBellowsCookingProxy(h.value(), false);
            if (proxy != null) {
                result.add(new RecipeHolder<>(h.id(), proxy));
            }
        }

        return result;
    }

    private static List<RecipeHolder<HauntingRecipe>> collectBellowsHauntingRecipes() {
        List<RecipeHolder<HauntingRecipe>> result = new ArrayList<>();
        List<RecipeHolder<?>> all = CreateJEI.getTypedRecipes(AllRecipeTypes.HAUNTING.getType());
        for (RecipeHolder<?> h : all) {
            if (h.value() instanceof HauntingRecipe r) {
                result.add(new RecipeHolder<>(h.id(), r));
            }
        }

        // L3 独占配方（fan_type=haunting）：包成 HauntingRecipe 后混进同一类别
        for (RecipeHolder<HandMadeBellowsRecipe> h : collectBellowsExclusive(fanTypeId(AllFanProcessingTypes.HAUNTING))) {
            HauntingRecipe proxy = asBellowsProcessingProxy(HauntingRecipe::new, h.id(), h.value());
            if (proxy != null) {
                result.add(new RecipeHolder<>(h.id(), proxy));
            }
        }

        return result;
    }

    private static List<RecipeHolder<SplashingRecipe>> collectBellowsSplashingRecipes() {
        List<RecipeHolder<SplashingRecipe>> result = new ArrayList<>();
        List<RecipeHolder<?>> all = CreateJEI.getTypedRecipes(AllRecipeTypes.SPLASHING.getType());
        for (RecipeHolder<?> h : all) {
            if (h.value() instanceof SplashingRecipe r) {
                result.add(new RecipeHolder<>(h.id(), r));
            }
        }

        // L3 独占配方（fan_type=splashing）：同上
        for (RecipeHolder<HandMadeBellowsRecipe> h : collectBellowsExclusive(fanTypeId(AllFanProcessingTypes.SPLASHING))) {
            SplashingRecipe proxy = asBellowsProcessingProxy(SplashingRecipe::new, h.id(), h.value());
            if (proxy != null) {
                result.add(new RecipeHolder<>(h.id(), proxy));
            }
        }

        return result;
    }

    // ==================== 风箱 L3 独占配方（create_hand_made:bellows_recipe） ====================

    /**
     * 收集 {@code create_hand_made:bellows_recipe} 里 {@code fan_type} 匹配的 L3 独占配方。
     *
     * <p><b>为什么不走 {@link HandMadeRecipePool}：</b>池的语义是"某个 {@code HandMadeTool}
     * 的候选集"，而风箱不在 {@code HandMadeTool} 体系里（它按 {@code fan_type} 归类，没有 tool 字段）。
     * 所以这里直接查 {@code RecipeManager} —— 与游戏内 {@code BellowsItem.findL3BellowsRecipe}
     * 的数据来源一致（那边是服务端用同一个 RecipeType 查的）。</p>
     *
     * <p>只做 {@code fan_type} 过滤；"这条配方是否匹配某个物品"由配方自己的 {@code matches}
     * 在游戏内判定（JEI 类别只负责展示）。</p>
     */
    private static List<RecipeHolder<HandMadeBellowsRecipe>> collectBellowsExclusive(ResourceLocation fanTypeId) {
        List<RecipeHolder<HandMadeBellowsRecipe>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (RecipeHolder<?> holder : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.BELLOWS_RECIPE.getType())) {
            if (!(holder.value() instanceof HandMadeBellowsRecipe recipe)) continue;
            if (!fanTypeId.equals(recipe.getFanTypeId())) continue;
            result.add(new RecipeHolder<>(holder.id(), recipe));
        }
        return result;
    }

    /**
     * 收集 {@code create_hand_made:bellows_recipe} 里<b>第三方</b> {@code fan_type} 的配方（T11 B6/B7）。
     *
     * <p>与 {@link #collectBellowsExclusive} 的关系：后者给 4 个"按 Create 内置类型分组"的类别用
     * （精确匹配某一个 id），本方法服务"第三方兼容"总览类别。</p>
     *
     * <p><b>为什么排除 {@code create} 命名空间（B7）：</b>4 个原版类别已经把
     * {@code create:blasting / smoking / haunting / splashing} 的 L3 配方各显示了一遍，
     * 总览类别再收就是完全重复的两份。这里按命名空间排除最省事，也顺带覆盖将来 Create
     * 可能新增的同命名空间类型。</p>
     */
    private static List<RecipeHolder<HandMadeBellowsRecipe>> collectThirdPartyBellowsExclusive() {
        List<RecipeHolder<HandMadeBellowsRecipe>> result = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return result;

        for (RecipeHolder<?> holder : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.BELLOWS_RECIPE.getType())) {
            if (!(holder.value() instanceof HandMadeBellowsRecipe recipe)) continue;

            ResourceLocation fanTypeId = recipe.getFanTypeId();
            // fan_type 理论上是必填（序列化器校验过），这里仍然兜一手：null 无法归属，跳过
            if (fanTypeId == null) continue;
            if (fanTypeId.getNamespace().equals("create")) continue;

            result.add(new RecipeHolder<>(holder.id(), recipe));
        }
        return result;
    }

    /**
     * Create 内置鼓风类型 → 类型 id（{@code create:blasting} 等）。
     *
     * <p>T11 批次 1 起 {@code fan_type} 是 {@link ResourceLocation}，JEI 这边按 id 分组；
     * 用注册表反查而不是写死字符串，Create 若改 id 这里会跟着变。</p>
     */
    private static ResourceLocation fanTypeId(FanProcessingType type) {
        ResourceLocation id = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
        return id != null ? id : ResourceLocation.fromNamespaceAndPath("create", "blasting");
    }

    /**
     * 把一条 L3 风箱配方包成 {@link AbstractCookingRecipe}（仅 JEI 展示用）。
     *
     * <p>用 {@link SmeltingRecipe}（{@code smelting=true}）或 {@link SmokingRecipe}：
     * 与 Create 的"鼓风熔炼 / 鼓风烟熏"底层配方来源一致（鼓风熔炼优先
     * {@code minecraft:smelting}，见 {@code AllFanProcessingTypes.java:121-137}）。
     * 两者的构造都是 public，只需要一个输入 + 一个产物。</p>
     *
     * <p><b>已知展示限制：</b>{@code AbstractCookingRecipe} 只能承载<b>一个</b>产物，
     * 而本模组的 L3 配方最多允许 12 个产物、每个可带概率 —— 所以
     * {@code fan_type: blasting / smoking} 的 L3 配方在 JEI 里<b>只显示第一个产物</b>
     * （{@code fan_type: haunting / splashing} 走 {@code getRollableResults()}，
     * 多产物与概率都会显示）。</p>
     */
    @Nullable
    private static AbstractCookingRecipe asBellowsCookingProxy(HandMadeBellowsRecipe exclusive, boolean smelting) {
        if (exclusive.getIngredients().isEmpty()) return null;
        List<ProcessingOutput> results = exclusive.getRollableResults();
        if (results.isEmpty()) return null;
        ItemStack result = results.get(0).getStack();
        if (result.isEmpty()) return null;

        Ingredient ingredient = exclusive.getIngredients().get(0);
        // experience / cookingTime 只是构造占位 —— 该类别不渲染这两个值
        // （BellowsCookingCategory:37,52 只读输入与产物）
        return smelting
                ? new SmeltingRecipe("", CookingBookCategory.MISC, ingredient, result, 0f, 200)
                : new SmokingRecipe("", CookingBookCategory.MISC, ingredient, result, 0f, 200);
    }

    /**
     * 把一条 L3 风箱配方包成 Create 的鼓风配方（{@link HauntingRecipe} / {@link SplashingRecipe}），
     * 仅 JEI 展示用。
     *
     * <p>用 Create 自己的 {@link StandardProcessingRecipe.Builder} 构造：它内部的
     * {@code createParams()} 会 new 一个 {@link ProcessingRecipeParams} 并在 Create 包内
     * 填好字段，所以外部包不需要碰 {@code protected} 的 params 字段
     * （T5c 批次 4 给部署器造代理时用的也是同一套 Builder）。</p>
     *
     * <p>产物走 {@code withItemOutputs(...)}，因此多产物与概率都会被类别渲染出来
     * （{@code BellowsHauntingCategory:45-56} 就是遍历 {@code getRollableResults()}
     * 并给每个产物挂概率 tooltip）。</p>
     */
    @Nullable
    private static <R extends StandardProcessingRecipe<?>> R asBellowsProcessingProxy(
            StandardProcessingRecipe.Factory<R> factory, ResourceLocation id, HandMadeBellowsRecipe exclusive) {
        if (exclusive.getIngredients().isEmpty()) return null;
        List<ProcessingOutput> results = exclusive.getRollableResults();
        if (results.isEmpty()) return null;

        return new StandardProcessingRecipe.Builder<>(factory, id)
                .withItemIngredients(exclusive.getIngredients())
                .withItemOutputs(results.toArray(new ProcessingOutput[0]))
                .build();
    }
}