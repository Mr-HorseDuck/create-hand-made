# PLAN — Create: Hand Made 的 JEI 支持（Fabric · MC 26.1.2 · JEI 29.47.0.111）

编写阶段：A（PLAN）。本文件只描述方案，**不含实现代码**；所有签名都标注了核实状态与证据来源。
核实日期：2026-11-15。当前 `.\gradlew.bat build` 已验证通过（改动前基线）。

---

## 0. 目标与成功标准

**目标**：在 `src/main/java/com/alben/createhandmade/compat/jei/` 下新增 JEI 兼容包，让 JEI 里出现本模组
16 个配方类别（上游 13 个类别类，其中 3 个类各被复用多次），内容与游戏内 8 件手工工具的候选集一致。

**成功标准（可客观判定）**

| # | 判据 | 判定方式 |
|---|------|----------|
| S1 | `.\gradlew.bat build` 通过 | Gradle 退出码 0 |
| S2 | 产物 `build/libs/create_hand_made-<ver>.jar` 内含 `fabric.mod.json` 的 `jei_mod_plugin` entrypoint 与 `com/alben/createhandmade/compat/jei/CreateHandMadeJEI.class` | 解包检查 / `processResources` 输出检查 |
| S3 | 未安装 JEI 时游戏正常启动（无 NoClassDefFoundError） | 用户 runClient 或正式客户端目视 |
| S4 | 安装 JEI 后 16 个类别可见，且不出现空类别（L2 过滤 / 配置关闭时整类不显示） | 用户目视 + JEI 分类列表截图 |
| S5 | 不修改 `neo-source/`、不修改现有 13 个 package 的业务逻辑 | `git status` / diff 审查 |

**明确不做的**：不新增配方图形资源、不加 `jei` 为硬依赖、不做 REI/EMI 兼容、不改 README。

---

## 1. 平台 / 版本 / 时代判定

- 时代：**26.x 现代线**。判定依据（已核实）：`gradle.properties` 里 `minecraft_version=26.1.2`；
  `gradle/gradle-daemon-jvm.properties` 钉 `toolchainVersion=25`；Loom `1.19.4`；wrapper `9.8.2`；
  MC 已不混淆（mapping 用官方 mojmap，无 yarn）。
- 平台：Fabric（`net.fabricmc.fabric-loom`），Loader 0.20.1，Fabric API 0.155.3+26.1.2。
- Java：25（toolchain 自动 provision，不手管 JDK）。
- JDK 环境结论：**不要碰** `gradle-daemon-jvm.properties`（Loom 1.19.x 要求 Gradle 自身跑 JDK 25）。
- 元数据现状：`src/main/resources/fabric.mod.json` 已有 `suggests.jei`，缺 `jei_mod_plugin` entrypoint。

---

## 2. 三个改变方案的硬事实（先说结论，后面所有设计都由它们决定）

### F1. 客户端拿不到 `RecipeManager` —— 配方来源必须换成 JEI 的客户端配方快照

本模组自己的 `HandMadeRecipePool`（`recipe/HandMadeRecipePool.java:321-329`）明确写着
「26.1 起配方只在服务端可用；客户端拿不到全量配方」，并在 `level.isClientSide()` 时返回 `null`。
`HandSawRecipeCache` 的类注释同样记录了这条（所以 HUD 走网络包同步）。

⇒ **JEI 插件是纯客户端代码，不能调用 `HandMadeRecipePool.getBaseRecipes(tool, level)`，也不能用
`level.getRecipeManager()`。**

26.1 的正确来源是 JEI 自己在客户端维护的同步配方快照（已核实，源码见 §5 来源 S3）：

```java
// mezz.jei.common.Internal（JEI mod jar，非 -api jar）
public static RecipeMap getClientSyncedRecipes();   // 无数据时返回 RecipeMap.EMPTY
public static boolean hasClientSyncedRecipes();
```

```java
// 用法（已核实，Create Fly 26.1 的 JeiClientPlugin.registerRecipes 就是这么写的）
RecipeMap preparedRecipes = Internal.getClientSyncedRecipes();
Collection<RecipeHolder<MillingRecipe>> rs = preparedRecipes.byType(AllRecipeTypes.MILLING);
```

`RecipeMap` 里含**所有已同步的配方类型**（vanilla、Create、以及本模组的 L3 `tool_recipe` /
`stonecutting_recipe` / `bellows_recipe`），这正是我们需要的全集。

**直接后果**：`mezz.jei.common.Internal` 不在 `jei-<mc>-fabric-api` 里，而在**完整 mod jar**
`mezz.jei:jei-26.1.2-fabric:29.47.0.111` 里 ⇒ **`build.gradle` 的 compileOnly 必须换成完整 jar**（见 §6）。

**第二个后果**：`HandMadeRecipePool` 里的筛选语义（config 开关、L2 datapack 过滤、`_manual_only`、
压缩判定、`mergeInGlobalOrder` 的全序）不会自动复用，需要有一层**客户端配方源适配器**把它们按
`RecipeMap` 重做一遍（判据见 §7 `JeiRecipeSource`）。好消息：L2 过滤在客户端也活着 ——
`HandMadeRecipeFilterLoader implements ResourceManagerReloadListener`（`recipe/HandMadeRecipeFilterLoader.java:25`），
资源重载在客户端同样会跑，所以 `HandMadeRecipeFilters.isDisabled(tool, id)` 客户端可用（已核实：该方法签名
`public static boolean isDisabled(HandMadeTool, Identifier)`）。

### F2. 上游 `CreateHandMadeJEI` 的骨架整体作废，必须按 26.1 的「无状态类别类」重写

上游（NeoForge 1.21.1）用的是旧 Create 的 **`CreateRecipeCategory.Info<T>` + builder 配置 + 收集器 Supplier**
那套结构（`CreateHandMadeJEI.java:90-116` 等）。26.1 的 Create Fly **没有 `Info`、没有 `EmptyBackground`、
没有 `CreateRecipeCategory`**，类别是「自己回答 类型/标题/图标/高度」的裸类（已核实，源码见 S1）：

```java
public abstract class CreateCategory<T> implements mezz.jei.api.recipe.category.IRecipeCategory<T> {
    public static final SlotRenderer EMPTY, SLOT, CHANCE_SLOT;
    public static final PotionTooltip POTION;
    public static final KeepHeldTooltip KEEP_HELD;
    public static ContextMap createIngredientContext();
    public static List<ItemStack> getStacks(SizedIngredient, Supplier<ContextMap>);
    public static List<ItemStack> getStacks(Ingredient, Supplier<ContextMap>);
    public static List<List<ItemStack>> condenseIngredients(List<Ingredient>);
    public static IRecipeSlotBuilder addFluidSlot(IRecipeLayoutBuilder, int, int, FluidStack);
    public static IRecipeSlotBuilder addFluidSlot(IRecipeLayoutBuilder, int, int, FluidIngredient);
    public static void addChanceSlot(IRecipeLayoutBuilder, int x, int y, ProcessingOutput);
    public static IRecipeSlotBuilder addJunkSlot(IRecipeLayoutBuilder, int x, int y);
    @Override public int getWidth() { return 177; }   // 默认 177，可不覆写
}
```

⇒ 每个类别只实现：`getRecipeType()` / `getTitle()` / `getIcon()` / `getHeight()` /
`setRecipe(builder, recipe, focuses)` / `draw(recipe, slotsView, GuiGraphicsExtractor, mx, my)`，
外加可选的 `getIdentifier(T) → Identifier`（给配方稳定 UID）。**背景由 JEI 自己按 width/height 画，不需要
`EmptyBackground`**（证据：Create Fly 全部 22 个类别都没有背景字段）。图标的旧 `DoubleItemIcon` 换成
`renderer.TwoIconRenderer(Item icon, Item subIcon)`（已核实是 `record`，`implements IDrawable`，
`draw(GuiGraphicsExtractor, int, int)`）。

### F3. 26.1 的渲染实参类型是 `GuiGraphicsExtractor`，「代理配方」那一堆技巧全部不需要了

- JEI 29 的 `IDrawable#draw` 与 `IRecipeCategory#draw` 都改成接收 `net.minecraft.client.gui.GuiGraphicsExtractor`
  （已核实：`TwoIconRenderer.draw(GuiGraphicsExtractor graphics, int x, int y)`；所有类别 `draw` 的第 3 参就是它）。
- `AllGuiTextures.JEI_*` 的 `render(...)` 第一参同样是 `GuiGraphicsExtractor`（已核实，见各 Create 类别）。
- 3D 装饰（磨盘/风扇/锯/工作盆）在 26.1 走新的 render-state 通道：
  `graphics.guiRenderState.addPicturesInPictureState(new XxxRenderState(new Matrix3x2f(graphics.pose()), x, y))`
  （已核实：`MillstoneRenderState` / `FanRenderState` / `SawRenderState` / `MixingBasinRenderState` /
  `CrushWheelRenderState` / `BasinBlazeBurnerRenderState` 的构造均为 `(Matrix3x2f, int, int[, BlockState])`）。
- **最重要的一条**：上游为了把 L3 独占配方塞进 Create 的类别，写了一整套「显示代理」
  （`CreateHandMadeJEI.java:350-591`：`collectFromPoolWithProxy` / `asDeployerProxy` / `wrapAsBasin` /
  `asBellowsProcessingProxy` / `asBellowsCookingProxy`）。**26.1 里这些几乎全部不需要**，因为本项目的 L3
  配方类已经实现了 Create 的配方接口（已核实，`src/main/java/com/alben/createhandmade/recipe/`）：

| L3 配方类 | 实现的 Create 接口 | 意义 |
|---|---|---|
| `HandMadeCuttingRecipe` | `CreateSingleStackRollableRecipe`, `TimedRecipe` | 手锯切削：L1+L3 同类型参数，无需代理 |
| `HandMadeCrushingRecipe` | `CreateSingleStackRollableRecipe`, `TimedRecipe` | 研钵/碾钵：同上 |
| `HandMadePressingRecipe` | `CreateSingleStackRollableRecipe` | 冲压锤置物台：同上 |
| `HandMadeBellowsRecipe` | `CreateSingleStackRollableRecipe` | 风箱缠魂/洗涤：同上 |
| `HandMadeToolRecipe` | `BasinRecipe`, `TimedRecipe` | 工作盆：L1+L3 都是 `BasinRecipe` |
| `HandMadeApplicationRecipe` | `ItemApplicationRecipe`（Create 的接口！） | 指杆：L1+L3 同类型参数 |
| `HandMadeFillingRecipe` | `CreateRecipe<FillingInput>` | 灌注枪：需与 `FillingRecipe` 归一 |
| `HandMadeStonecuttingRecipe` | `extends SingleItemRecipe` | 切石：交给折叠逻辑统一处理 |

⇒ 保留「显示代理」的只有两处：**风箱熔炼/烟熏**（L1 是 `SingleItemRecipe`/`AbstractCookingRecipe` 语义、
L3 是多产物，需要归一化 display）与 **工作台→工作盆** 的自动两类别（见 §3 风险 R5）。

---

## 3. 可行性结论与逐类别评估（16 个 JEI 类型）

图例：**A = 照搬改包名/类型**（几乎机械）；**B = 按新基类重写布局**（坐标照抄 Create，逻辑重写）；
**C = 需要新设计（display 归一化 / 折叠 / 特殊数据源）**。

| # | JEI 类型常量 | 上游类别类 | 26.1 可用基类 & 数据源 | 档 | 说明 |
|---|---|---|---|---|---|
| 1 | `HAND_SAW_CUTTING` | `HandSawCategory` | `CreateCategory<RecipeHolder<? extends CreateSingleStackRollableRecipe>>`；源 `byType(AllRecipeTypes.CUTTING)` + L3 `HandMadeCuttingRecipe` | B | 布局照抄 26.1 `SawingCategory`（输入 (44,5)、产物 (118/137, 48+)），把机械锯换成手锯图标 |
| 2 | `HAND_SAW_STONECUTTING` | `HandSawStonecuttingCategory` | **上游的 `CondensedBlockCuttingRecipe`/`condenseRecipes` 在 26.1 不存在**；改用 `display.BlockCuttingDisplay(Identifier, Ingredient, List<List<ItemStackTemplate>>)` + 照抄 26.1 `BlockCuttingCategory.getRecipes` 的折叠算法（5×3 槽、>15 条并入同槽） | C | 必须自写折叠器并额外吃 L3 `HandMadeStonecuttingRecipe`（因为它不是 `StonecutterRecipe`，Create 的现成收集器只扫 vanilla 类型）。可 `extends BlockCuttingCategory` 只覆 `getRecipeType/getTitle/getIcon` |
| 3 | `HAND_PRESS_DEPOT` | `HandPressDepotCategory` | `CreateCategory<RecipeHolder<? extends CreateSingleStackRollableRecipe>>`；源 `byType(AllRecipeTypes.PRESSING)` + L3 `HandMadePressingRecipe` | A/B | 布局照抄 26.1 `PressingCategory` |
| 4 | `HAND_PRESS_BASIN` | `HandPressBasinCategory.standard` | `CreateCategory<RecipeHolder<? extends BasinRecipe>>`；源 `byType(AllRecipeTypes.COMPACTING)` + L3 `HandMadeToolRecipe`；布局复用 `BasinCategory` 的静态 helper | B | `BasinCategory.addIngredientSlots/addResultSlots/addFluidResultSlots/addHeatSlots/drawBackground` 全部接受 `BasinRecipe`（已核实），L3 也是 `BasinRecipe` ⇒ 零代理 |
| 5 | `HAND_PRESS_BASIN_AUTO_SQUARE` | 同上 `.autoSquare` | 工作台配方（`CraftingRecipe`）→ 工作盆语义 | C | 见风险 **R5**：需要 `CraftingDisplay`（或 `BasinRecipe.convertShapeless`，待核实） |
| 6 | `HAND_INFUSION_GUN` | `HandInfusionGunCategory` | `byType(AllRecipeTypes.FILLING)` + L3 `HandMadeFillingRecipe`（`CreateRecipe<FillingInput>`） | C | 两者不是同一接口；需要一个 `FillingDisplay(Identifier, ItemStackTemplate result, Ingredient ingredient, List<FluidStack>)` 或分别渲染两个子布局。上游还追加了 `SpoutCategory.consumeRecipes`（26.1 是 `SpoutFillingCategory`，无该静态方法）→ 建议首版不加转换配方 |
| 7 | `MORTAR_MILLING` | `MortarMillingCategory` | `CreateCategory<RecipeHolder<? extends CreateSingleStackRollableRecipe>>`；源 `byType(AllRecipeTypes.MILLING)` + L3 `HandMadeCrushingRecipe` | A/B | 布局照抄 26.1 `MillingCategory` |
| 8 | `CRUSHER_MORTAR_CRUSHING` | `CrusherMortarCrushingCategory` | 同上类型；源 `byType(AllRecipeTypes.CRUSHING)` 在前、`MILLING` 在后 | A/B | 注意 26.1 **没有 `AbstractCrushingRecipe`**，共同父类换成 `CreateSingleStackRollableRecipe`（已核实） |
| 9 | `STIRRING_STAFF_MIXING` | `StirringStaffMixingCategory.standard` | `CreateCategory<RecipeHolder<? extends BasinRecipe>>`；源 `byType(AllRecipeTypes.MIXING)` + L3 | B | 同 #4，只是布局照抄 `MixingCategory`（含流体输入/输出与热量槽） |
| 10 | `STIRRING_STAFF_AUTO_SHAPELESS` | 同上 `.autoShapeless` | 无序 `ShapelessRecipe` → 工作盆 | C | 同 R5 |
| 11 | `STIRRING_STAFF_AUTO_BREWING` | 同上 `.autoBrewing` | `byType(AllRecipeTypes.POTION)` | B | 26.1 Create 有 `PotionsCategory`（`PotionRecipe`）可直接照抄布局；是否复用 `PotionTooltip` 待核实 |
| 12 | `POINTER_APPLICATION` | `PointerApplicationCategory` | `CreateCategory<RecipeHolder<? extends ItemApplicationRecipe>>`；源 `byType(AllRecipeTypes.DEPLOYING)` + `ITEM_APPLICATION` + L3 | A/B | L3 直接 `implements ItemApplicationRecipe`（已核实）⇒ **不再需要 `asDeployerProxy`** |
| 13 | `BELLOWS_BLASTING` | `BellowsCookingCategory` | `CookingDisplay(Identifier id, Ingredient input, ItemStackTemplate result)`，源 = SMELTING（排除有同输入 BLASTING 的）+ BLASTING（去重 SMOKING）+ L3 `BellowsRecipe(fan_type=BLASTING)`，L3 取第一个产物 | C | 上游 `CreateJEI.doInputsMatch/doOutputsMatch` 在 26.1 不存在（`CreateJEI` 类已改名 `JeiClientPlugin` 且无这些方法，已核实）⇒ 用 `SingleRecipeInput` + `recipe.matches(input, level)` 自己实现等价判定（照抄 26.1 `FanBlastingCategory.addRecipe` 的做法） |
| 14 | `BELLOWS_SMOKING` | `BellowsCookingCategory` | SMOKING + `_manual_only` 过滤 + L3(fan_type=SMOKING) | C | 同上 |
| 15 | `BELLOWS_HAUNTING` | `BellowsHauntingCategory` | `byType(AllRecipeTypes.HAUNTING)`（`HauntingRecipe`）+ L3 `HandMadeBellowsRecipe`（同 `CreateSingleStackRollableRecipe`） | A/B | 布局照抄 26.1 `FanHauntingCategory`；多产物与概率可直接渲染 |
| 16 | `BELLOWS_SPLASHING` | `BellowsSplashingCategory` | `byType(AllRecipeTypes.SPLASHING)` + L3 | A/B | 布局照抄 26.1 `FanWashingCategory`（已核实源码） |

**总体可行性结论：可行。** 16 个里 **8 个是 A/B 档（机械照搬 26.1 同类布局）**、**6 个是 C 档需要新写归一化/折叠**
（#2 切石、#5/#10 工作台→工作盆、#6 灌注枪、#13/#14 熔炼烟熏）。最大的两个风险点：**#2 折叠逻辑**与
**R5 工作台→工作盆**。

**建议的分期**：首版交付 A/B 档 10 个类别（#1,3,4,7,8,9,11,12,15,16）→ 第二期 C 档 6 个。若 D 阶段时间紧，
#5/#10 可整体后置（它们在游戏内的语义是「工作台配方也能手搓」，JEI 里本来就有原版合成类别可查）。

---

## 4. API 对照表（上游符号 → 26.1 精确签名）

状态标记：**✅已核实**（有可复现来源，见 §5）/ **⚠️待核实**（必须由 B 或 D 在编译期确认，附验证方法）/
**❌不存在**（26.1 已无此符号，必须换实现）。

### 4.1 JEI 侧

| 上游（JEI 19 / 1.21.1） | 26.1 目标 | 状态 | 证据 |
|---|---|---|---|
| `mezz.jei.api.recipe.RecipeType<T>` | `mezz.jei.api.recipe.types.IRecipeType<T>` | ✅ | Create `JeiClientPlugin` import 与字段类型 |
| `RecipeType.createRecipeHolderType(ResourceLocation)` | 无同名重载；用 `(IRecipeType<RecipeHolder<T>>) IRecipeType.create(uid, RecipeHolder.class)` | ✅ | Create 自建的 `createRecipeHolderType(String)` 内部就是这两行 |
| — | `IRecipeType.create(Identifier uid, Class<?> recipeClass)` | ✅ | 同上 |
| — | `IRecipeType.create(String namespace, String path, Class<T>)` | ✅ | `JeiClientPlugin.MYSTERY_CONVERSION` / `BLOCK_CUTTING` 用该重载 |
| `@JeiPlugin` + `implements IModPlugin` | 不变 | ✅ | Create `JeiClientPlugin` 仍是这套 |
| `IModPlugin#getPluginUid(): ResourceLocation` | `Identifier getPluginUid()` | ✅ | `public Identifier getPluginUid()` |
| `IModPlugin#registerCategories(IRecipeCategoryRegistration)` | 不变；注册用 `registration.addRecipeCategories(IRecipeCategory<?>...)` | ✅ | 同上 |
| `IModPlugin#registerRecipes(IRecipeRegistration)` | 不变；`registration.addRecipes(IRecipeType<T>, Collection<T>)`；`registration.getIngredientManager()` | ✅ | 同上 |
| `IModPlugin#registerRecipeCatalysts(IRecipeCategoryRegistration)` | `registration.addCraftingStation(IRecipeType<T>, ItemLike...)` | ✅ | `registration.addCraftingStation(AUTOMATIC_PACKING, AllItems.MECHANICAL_PRESS, AllItems.BASIN)` |
| `IRecipeCategory#draw(T, IRecipeSlotsView, GuiGraphics, double, double)` | `draw(T, IRecipeSlotsView, GuiGraphicsExtractor, double, double)` | ✅ | 所有 26.1 类别 |
| `IDrawable#draw(GuiGraphics, int, int)` | `draw(GuiGraphicsExtractor, int, int)` | ✅ | `TwoIconRenderer` |
| — | `IRecipeCategory#getIdentifier(T) → Identifier`（可选覆写，稳定配方 UID） | ✅ | `BlockCuttingCategory.getIdentifier` |
| — | `IRecipeCategory#getBackground()` 是否存在 | ⚠️ | Create 的类别都没有背景；若编译期提示有抽象方法未实现，则补 `getBackground()` 返回 `IDrawable`。验证：B 首次编译 |
| fabric entrypoint key | `"jei_mod_plugin"` | ✅ | Create `fabric.mod.json` entrypoints |
| 是否需要在 `depends` 里声明 `jei` | **不需要**（Create 也没有，靠 entrypoint 缺席容忍） | ✅ | Create `fabric.mod.json` 无 jei 依赖 |

### 4.2 Create Fly 侧（`com.zurrtum.create.*`）

| 上游（NeoForge Create 1.21.1） | 26.1 目标 | 状态 |
|---|---|---|
| `com.simibubi.create.compat.jei.category.CreateRecipeCategory` + `.Info` | `com.zurrtum.create.client.compat.jei.CreateCategory<T>`（**无 Info**，方法见 §F2） | ✅ |
| `com.simibubi.create.compat.jei.DoubleItemIcon` | `...compat.jei.renderer.TwoIconRenderer(Item, Item)` | ✅ |
| `com.simibubi.create.compat.jei.EmptyBackground(w,h)` | ❌ 不存在，也不需要 | ✅ |
| `com.simibubi.create.compat.jei.CreateJEI`（`getTypedRecipes` / `doInputsMatch` / `doOutputsMatch`） | ❌ 类已改为 `...compat.jei.JeiClientPlugin`，无上述方法 | ✅ |
| `com.simibubi.create.compat.jei.category.BlockCuttingCategory.CondensedBlockCuttingRecipe` + `condenseRecipes` | ❌；改为 `...compat.jei.display.BlockCuttingDisplay` record + 类别内自写折叠 | ✅ |
| `com.simibubi.create.compat.jei.category.SpoutCategory.consumeRecipes(...)` | ❌；26.1 是 `SpoutFillingCategory`，无该静态方法 | ✅ |
| `com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe` | ❌；共同父类是 `com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe` | ✅ |
| `com.simibubi.create.foundation.gui.AllGuiTextures` | `com.zurrtum.create.client.foundation.gui.AllGuiTextures`；`JEI_ARROW / JEI_LONG_ARROW / JEI_DOWN_ARROW / JEI_SHADOW / JEI_LIGHT / JEI_SLOT / JEI_CHANCE_SLOT / JEI_HEAT_BAR / JEI_NO_HEAT_BAR` 均存在 | ✅ |
| `AllGuiTextures.JEI_X.render(GuiGraphics, int, int)` | `render(GuiGraphicsExtractor, int, int)` | ✅ |
| `net.createmod.catnip.animation.AnimationTickHolder` | `com.zurrtum.create.client.catnip.animation.AnimationTickHolder`（文件存在） | ⚠️ 方法名（`getPartialTicks` / `getTicks`）待核实 |
| `net.createmod.catnip.gui.element.GuiGameElement` | `com.zurrtum.create.client.catnip.gui.element.GuiGameElement`（文件存在，含内部 Builder） | ⚠️ `render(...)` 的实参类型是否为 `GuiGraphicsExtractor` 待核实；**建议不用它**，改用 `graphics.item(ItemStack, x, y)` + `graphics.pose()` 做缩放（`TwoIconRenderer` 已示范这套写法） |
| 类别装饰动画（磨盘/风扇/锯/工作盆） | `graphics.guiRenderState.addPicturesInPictureState(new XxxRenderState(new Matrix3x2f(graphics.pose()), x, y[, state]))` | ✅ |
| `AllRecipeTypes.X`（类型常量） | `byType(AllRecipeTypes.COMPACTING/PRESSING/MILLING/CRUSHING/CUTTING/MIXING/POTION/DEPLOYING/ITEM_APPLICATION/FILLING/HAUNTING/SPLASHING)` 均可用；`AllRecipeTypes.CAN_BE_AUTOMATED` 是 `Predicate<RecipeHolder<?>>`（本项目已在用） | ✅ |
| `BasinRecipe.convertShapeless(RecipeHolder)`（上游 wrapping 用） | ⚠️ 26.1 是否存在未知 | ⚠️ 验证：`javap`/源码确认；不存在就走自建 `CraftingDisplay` |

### 4.3 MC / 数据源侧

| 上游 | 26.1 目标 | 状态 |
|---|---|---|
| `Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(type)` | `mezz.jei.common.Internal.getClientSyncedRecipes()` → `RecipeMap#byType(RecipeType<T>) → Collection<RecipeHolder<T>>` | ✅（客户端已无 `RecipeManager`，见 F1） |
| `new ResourceLocation(ns, path)` | `Identifier.fromNamespaceAndPath(ns, path)`（26.1 改名） | ✅（本项目已在用） |
| `Recipe#getIngredients()` / `getResultItem(registryAccess)` | 收敛到具体 accessor：`CreateSingleStackRollableRecipe#ingredient()/results()`、`BasinRecipe#ingredients()/fluidIngredients()/results()/fluidResults()/heat()`、`SingleItemRecipe#input()/result()`（`result()` 返回 `ItemStackTemplate`） | ✅ |
| `ItemStack` 作为配方产物 | `ItemStackTemplate`（配方层）与 `ItemStack`（渲染层）并存；槽位 `slot.add(ItemStackTemplate)` 可用 | ✅ |
| 自定义 `RecipeType` 取值 | `HandMadeRecipeTypes.BELLOWS_RECIPE.getType()` 返回 `RecipeType<R>`（本项目 `recipe/HandMadeRecipeTypes.java:48`）；传给 `byType` 需要 raw cast + `@SuppressWarnings`（同本项目 `HandMadeRecipePool.recipesOfType` 的做法） | ⚠️ 泛型写法待编译确认 |

### 4.4 需要 B 落地时补测的 3 个「一票否决项」

1. **JEI 版本漂移**：Create Fly 26.1 是对 **`29.6.2.31`** 编译的（其 `gradle.properties`：
   `jei_version = 26.1.2-fabric:29.6.2.31`），而本项目钉 `29.47.0.111`。29.6→29.47 之间若
   `IRecipeType.create(...)` 或 `IRecipeCategory` 抽象方法有变动，编译会直接报错。
   **兜底**：把 `gradle.properties` 的 `jei_version` 改成 `29.6.2.31`（=`mezz.jei:jei-26.1.2-fabric:29.6.2.31`），
   与 Create Fly 对齐；语义不变，仅落后一个小版本。**这是首选的降级路径，别改代码去绕。**
2. **`CuttingRecipe implements CreateSingleStackRollableRecipe` 是否成立**（决定 #1 手锯切削能否直接用统一类型）。
   验证：`javap -p -classpath libs/create-fly-26.1.2-6.0.9-4.jar com.zurrtum.create.content.kinetics.saw.CuttingRecipe`
   或读 §5 S1 源码；不成立则给 `HandSawCategory` 单独做一个 `SawDisplay`。
3. **`IRecipeCategory` 在 29.47 的完整抽象方法集**（是否有 `getBackground()` / `getTooltipStrings` 变化）。

---

## 5. 参考来源（可复现）

- **S1** Create Fly 26.1 分支源码（GitHub，`ZurrTum/Create-Fly`，branch `26.1`，客户端源码集
  `src/client/java/`）——本文件中所有 `com.zurrtum.create.*` 签名均出自这里：
  - `com/zurrtum/create/client/compat/jei/CreateCategory.java`
  - `com/zurrtum/create/client/compat/jei/JeiClientPlugin.java`
  - `com/zurrtum/create/client/compat/jei/display/BlockCuttingDisplay.java`
  - `com/zurrtum/create/client/compat/jei/renderer/TwoIconRenderer.java`
  - `com/zurrtum/create/client/compat/jei/category/{MillingCategory,CrushingCategory,SawingCategory,Basing*}.java`
    （`BasinCategory` / `MixingCategory` / `BlockCuttingCategory` / `FanBlastingCategory` / `FanWashingCategory`）
  - `build.gradle`（依赖写法）、`gradle.properties`（`jei_version` 形态）、
    `src/main/resources/fabric.mod.json`（entrypoint 键名）
- **S2** JEI maven 目录（已确认坐标存在）：
  `https://maven.blamejared.com/mezz/jei/jei-26.1.2-fabric/29.47.0.111/`（完整 mod jar ≈ 2.4 MB，
  另有 `-sources.jar` 1.0 MB、`-unshaded.jar`）；
  `https://maven.blamejared.com/mezz/jei/jei-26.1.2-fabric-api/29.47.0.111/`（6.6 KB 薄壳，
  pom 依赖 `jei-26.1.2-common-api`）。
- **S3** JEI 26.1 分支源码 `mezz/JustEnoughItems` branch `26.1`，
  `Common/src/main/java/mezz/jei/common/Internal.java`（`getClientSyncedRecipes()/hasClientSyncedRecipes()`）。
  注意：**JEI 的 `mezz.jei.api` 包不在该仓库任何模块里**（只有 `Common`/`Fabric`/`Gui`/`Library`/`NeoForge`），
  因此 API 侧签名的最终确认只能靠**编译**或对本机 jar 做 `javap`。
- **S4** 本项目内已核实可复用的符号：
  `recipe/HandMadeRecipeTypes.java`、`recipe/HandMadeTool.java`、`recipe/FanType.java`、
  `recipe/HandMadeRecipePool.java`（客户端不可用）、`recipe/HandMadeRecipeFilters.java`、
  `item/ModItems.java`（`PRESS_HAMMER/MORTAR/CRUSHER_MORTAR/POINTER/STIRRING_STAFF/BELLOWS/INFUSION_GUN/HAND_SAW`
  均为 `Item` 实例）。

> 说明（务必转告 D）：本会话**没有 shell/javap 可用**（工具集里没有命令执行入口），所以
> `libs/create-fly-26.1.2-6.0.9-4.jar` 与 Gradle 缓存里的 JEI jar **未能用 `javap` 实测**；
> 相应签名我是用「Create Fly 26.1 分支源码」反推的（同一份代码编出的 jar，可信度高但不等于字节码验证）。
> **凡是标 ⚠️ 的项，D 阶段用 javap 或直接编译一次即可定案。**

---

## 6. 依赖与元数据方案

### 6.1 `build.gradle`（必须改）

现状：
```groovy
repositories { maven { url = 'https://maven.blamejared.com' } }
dependencies { compileOnly "mezz.jei:jei-${minecraft_version}-fabric-api:${jei_version}" }
```

改为（**换掉** api-only 依赖为完整 jar；理由见 F1：`mezz.jei.common.Internal` 只在完整 jar 里）：
```groovy
dependencies {
    // JEI（可选，编译期）：必须是完整 mod jar，-fabric-api 薄壳里没有 mezz.jei.common.Internal
    compileOnly "mezz.jei:jei-${minecraft_version}-fabric:${jei_version}"
}
```
`gradle.properties` 的 `jei_version=29.47.0.111` **可保持不变**（坐标形态 `jei-26.1.2-fabric:29.47.0.111`
已确认在 maven 上）；若触发 §4.4-① 的版本漂移，改成 `29.6.2.31`。

**dev 运行时（`runClient` 里真的加载 JEI）** —— ⚠️**待核实**，给 D 两条候选，按顺序试：

1. `modLocalRuntime "mezz.jei:jei-26.1.2-fabric:${jei_version}"`（Loom 的传统写法；Loom 1.19 在 26.x
   不混淆环境下可能把它当普通运行时依赖，等价于 `runtimeOnly`）。
2. 若 ① 报「not a mod jar / remap 失败」：退化为
   `runtimeOnly "mezz.jei:jei-26.1.2-fabric:${jei_version}"`，再不行就把 JEI 的发布 jar 丢进 run 目录的
   `mods/`（Loom 的 `run/` 目录）。
3. 兜底：**不做 dev 内验证**，由用户用发布 jar 在正式客户端目视（见 §8 第 3 步）。Create Fly 自己也是
   把 REI/JEI 的运行时依赖注释掉的，所以「dev 不装 JEI」是这条线的常规状态。

**不要**给 JEI 加 `implementation`（会打进 jar / 变成硬依赖），**不要**给 `-fabric-api` 薄壳与完整 jar
同时声明（重复类）。

### 6.2 `src/main/resources/fabric.mod.json`

追加 entrypoint（已核实键名 `jei_mod_plugin`，值 = 插件类全名）：
```json
"entrypoints": {
  "main":   ["com.alben.createhandmade.CreateHandMade"],
  "client": ["com.alben.createhandmade.CreateHandMadeClient"],
  "jei_mod_plugin": ["com.alben.createhandmade.compat.jei.CreateHandMadeJEI"]
}
```
其余保持不变：**不新增 `depends.jei`**（会变成硬依赖，装不装 JEI 都必须能启动 —— Create 自己也没声明）；
现有 `"suggests": {"jei": ">=29.0.0"}` 保留即可（信息性，不参与加载判定）。

### 6.3 不需要动的
`gradle/gradle-daemon-jvm.properties`（JDK 25 钉死）、Loom 版本、wrapper、`settings.gradle`、mixins 配置。

---

## 7. 文件清单与切片

新增根目录：`src/main/java/com/alben/createhandmade/compat/jei/`（**只新增，不改任何现有文件**）。

### 7.1 文件清单（每个文件的职责 + 归属）

| 文件 | 职责 | 归属 |
|---|---|---|
| `build.gradle`（改 1 行）+ `gradle.properties`（可能改 1 行）+ `src/main/resources/fabric.mod.json`（加 entrypoint） | 依赖与元数据 | **B** |
| `compat/jei/CreateHandMadeJEI.java` | `@JeiPlugin implements IModPlugin`：`getPluginUid()`、`registerCategories`、`registerRecipes`、`registerRecipeCatalysts`。所有类别实例在此集中创建并登记催化剂（工具物品） | **B**（骨架：空注册）→ **C**（补全注册与收集器接线） |
| `compat/jei/ModJeiTypes.java` | 16 个 `IRecipeType` 常量 + `static <T> IRecipeType<T> holderType(String path)`（内部 `(IRecipeType<T>) IRecipeType.create(Identifier.fromNamespaceAndPath(MODID, path), RecipeHolder.class)`） | **B** |
| `compat/jei/JeiRecipeSource.java` | **唯一配方来源**：`RecipeMap` 抓取 + 客户端版筛选语义（L2 `HandMadeRecipeFilters.isDisabled`、`AllRecipeTypes.CAN_BE_AUTOMATED`、`_manual_only`、config 门）。所有类别的静态 `getRecipes()` 只调它 | **B**（壳 + `recipesOfType`)→ **C**（按 tool 补齐 12 条语义） |
| `compat/jei/display/BlockCuttingDisplay（复用 Create 的）` | 切石显示类型，直接复用 `com.zurrtum.create.client.compat.jei.display.BlockCuttingDisplay`，不新建 | — |
| `compat/jei/display/CookingDisplay.java` | `record CookingDisplay(Identifier id, Ingredient input, ItemStackTemplate result)`，归一闪炼/烟熏的 L1+L3 | **C** 切片 4 |
| `compat/jei/display/FillingDisplay.java` | `record FillingDisplay(Identifier id, Ingredient input, ItemStackTemplate result, List<FluidStack> fluids)` | **C** 切片 4 |
| `compat/jei/display/CraftingDisplay.java` | `record CraftingDisplay(Identifier id, List<Ingredient> grid, ItemStackTemplate result)`（仅当 `BasinRecipe.convertShapeless` 不存在时启用） | **C** 切片 3 |
| `compat/jei/category/HandSawCategory.java` | #1 切削 | **C** 切片 2 |
| `compat/jei/category/HandSawStonecuttingCategory.java` | #2 切石（含折叠器 `condense(...)`） | **C** 切片 2 |
| `compat/jei/category/MortarMillingCategory.java` | #7 | **C** 切片 2 |
| `compat/jei/category/CrusherMortarCrushingCategory.java` | #8 | **C** 切片 2 |
| `compat/jei/category/HandPressDepotCategory.java` | #3 | **C** 切片 2 |
| `compat/jei/category/BellowsHauntingCategory.java` | #15 | **C** 切片 2 |
| `compat/jei/category/BellowsSplashingCategory.java` | #16 | **C** 切片 2 |
| `compat/jei/category/HandPressBasinCategory.java` | #4（+ 若 R5 解决再含 #5 的子类或同一类的第二个实例） | **C** 切片 3 |
| `compat/jei/category/StirringStaffMixingCategory.java` | #9（+ #10/#11） | **C** 切片 3 |
| `compat/jei/category/PointerApplicationCategory.java` | #12 | **C** 切片 3 |
| `compat/jei/category/BellowsCookingCategory.java` | #13/#14（一个类两个实例：blasting/smoking + 介质图标） | **C** 切片 4 |
| `compat/jei/category/HandInfusionGunCategory.java` | #6 | **C** 切片 4 |
| `compat/jei/category/BasinLayoutHelper.java`（可选） | 若决定不直接复用 Create `BasinCategory` 的静态 helper，则本地复制其坐标算法 | **C** 切片 3 |
| `compat/jei/compat/jei.md` 之类的文档 | 不需要 | — |
| 整体 diff / 编译 / 运行验证 | 审查 + 构建 | **D** |

### 7.2 切片（按依赖顺序，每个切片都可独立编译通过）

**切片 1 — 骨架（B）**
产出：`build.gradle` / `gradle.properties`（如需）/ `fabric.mod.json` 改动 + `ModJeiTypes`（16 常量）+
`JeiRecipeSource`（能 `hasClientSyncedRecipes()` 判空 + `recipesOfType(RecipeMap, RecipeType)`）+
`CreateHandMadeJEI`（`getPluginUid` + 空的三个 register 方法）。
完成判据：`.\gradlew.bat build` 通过；jar 里有 `jei_mod_plugin` entrypoint 和插件类；`ModJeiTypes` 里 16 个
常量的 UID 与上游完全一致（`create_hand_made:hand_saw_cutting` 等），保证与旧版 JEI 收藏/书签语义不变。

**切片 2 — 单输入多产物家族（C）**：`HandSawCategory`(#1)、`HandSawStonecuttingCategory`(#2)、
`MortarMillingCategory`(#7)、`CrusherMortarCrushingCategory`(#8)、`HandPressDepotCategory`(#3)、
`BellowsHauntingCategory`(#15)、`BellowsSplashingCategory`(#16) + 在 `CreateHandMadeJEI` 里注册这 7 个。
布局坐标照抄 26.1 `SawingCategory/MillingCategory/CrushingCategory/FanHauntingCategory/FanWashingCategory`；
装饰动画用 `SawRenderState/CrushWheelRenderState/MillstoneRenderState/FanRenderState`；
手锯/研钵/风箱的图标用 `TwoIconRenderer(ModItems.X, Items.Y)`。
#2 的折叠器照抄 26.1 `BlockCuttingCategory.getRecipes` 的 map 折叠 + 15 槽逻辑，输入集合
= vanilla `STONECUTTING`（过 `CAN_BE_AUTOMATED`）+ 本模组 `stonecutting_recipe` 类型，最后过 L2 过滤。

**切片 3 — 工作盆家族（C）**：`HandPressBasinCategory`(#4)、`StirringStaffMixingCategory`(#9、#11)、
`PointerApplicationCategory`(#12)；#5/#10 视 R5 结论决定「同切片实现」或「延后」。
#4/#9/#11 用 `CreateCategory<RecipeHolder<? extends BasinRecipe>>` + 复用 `BasinCategory` 静态 helper
（`addIngredientSlots/addResultSlots/addFluidResultSlots/addHeatSlots/drawBackground`），
L3 `HandMadeToolRecipe` 天然是 `BasinRecipe`，**不要**写任何代理/包装类。

**切片 4 — 归一化家族（C）**：`BellowsCookingCategory`(#13/#14)、`HandInfusionGunCategory`(#6) +
`display/CookingDisplay`、`display/FillingDisplay`。
熔炼/烟熏的去重规则照抄 26.1 `FanBlastingCategory.addRecipe`（`SingleRecipeInput` + `matches` 判定 +
`CAN_BE_AUTOMATED`），L3 只取 `results().get(0)`（`AbstractCookingRecipe` 语义的单产物限制要在代码注释里保留）。

---

## 8. 验证方式（交给 D）

### 8.1 必做（无 GUI，纯构建 + 静态检查）

1. **编译**：`mc_gradle(projectDir=C:\Users\Colin\Desktop\fabric, task="build", timeoutMs=900000)`。
   - 首次会从 `maven.blamejared.com` 拉 JEI 完整 jar（约 2.4 MB），别开 `--offline`。
   - 退出码 0 才算过；有 `compileJava` 错误时按 `§4.4` 的三项优先排查（尤其 JEI 版本漂移）。
   - 建议追加 `args=["--no-daemon"]` 避免守护进程残留占用 build 目录。
2. **产物检查**：
   - `.\gradlew.bat processResources` 后读 `build/resources/main/fabric.mod.json`，确认含
     `"jei_mod_plugin": ["com.alben.createhandmade.compat.jei.CreateHandMadeJEI"]`。
   - 确认 `build/libs/create_hand_made-0.5.0-beta.jar` 内存在
     `com/alben/createhandmade/compat/jei/CreateHandMadeJEI.class` 与 `ModJeiTypes.class`
     （用 `jar tf` 或解包；若环境无 shell，则用 Gradle 的 `jar` 任务输出 + 目录比对）。
3. **无 JEI 场景的静态保证**：确认新包**没有任何**「静态初始化期就会触达 JEI 类」的代码
   （`ModJeiTypes` 的常量是 `IRecipeType` 实例 —— 这是类静态初始化，**只在类被加载时**才需要 JEI；
   插件类只由 JEI 的 entrypoint 加载器实例化 ⇒ 未装 JEI 时不会加载。检查点：`CreateHandMade` /
   `CreateHandMadeClient` 里**不得**出现对 `compat.jei.*` 的引用）。
4. **可选（快）**：`.\gradlew.bat compileJava --rerun-tasks` 单独确认编译面。

### 8.2 可选的 dev 运行时验证（`runClient`）

按 §6.1 试 `modLocalRuntime`/`runtimeOnly` 装 JEI 后 `runClient`：
进世界 → 按 `R`（JEI 默认键）或点右下角 JEI 按钮 → 逐个展开 16 个类别，检查：
输入/产物槽位不重叠、`JEI_SHADOW`/箭头位置正常、无空白类别、点产物能反查。
⚠️ 这一步在当前环境属**待核实**（JEI 运行时依赖配置未实测），失败不影响 S1/S2 判定。

### 8.3 留给用户的目视验收脚本（写进交付说明，不要写进 README）

> 1. 用正式客户端（Fabric Loader 0.20.1 + Fabric API 0.155.3+26.1.2 + Create Fly 26.1.2-6.0.9-4 + JEI 29.47.0.111）。
> 2. 把 `build/libs/create_hand_made-0.5.0-beta.jar` 放进 `mods/`。
> 3. 启动 → 进单人世界（创造）→ 打开物品栏，右侧应出现 JEI 面板。
> 4. 点 JEI 的「分类」列表，逐一确认 16 个带手作工具图标的类别：
>    手锯切削、手锯切石、冲压锤·置物台、冲压锤·工作盆、研钵研磨、碾钵粉碎、搅拌杖混合、
>    搅拌杖自动酿造、指杆应用、风箱熔炼、风箱烟熏、风箱缠魂、风箱洗涤（+ 自动摆放/自动无序合成，若本期包含）。
> 5. 反查一次：点亮「磨石」(millstone) → 应在 JEI 里高亮「研钵研磨」类别（催化剂生效）。
> 6. 冒烟：卸掉 JEI 再启动一次，确认模组照常加载、无崩溃日志。

---

## 9. 风险清单

| # | 风险 | 影响 | 处置 |
|---|---|---|---|
| R1 | **JEI 版本漂移**：Create Fly 26.1 是对 `29.6.2.31` 编译的，本项目钉 `29.47.0.111` | 编译期签名不匹配（一票否决） | 首选直接降 `jei_version` 到 `29.6.2.31`（§4.4-①）；不要改代码迁就 |
| R2 | **客户端没有 `RecipeManager`**：直接抄上游会得到**全空**的类别（JEI 里什么都看不到，且不报错） | 静默失败，最难查 | 统一走 `Internal.getClientSyncedRecipes()`（F1）；`JeiRecipeSource` 里加一行 `if (RecipeMap.EMPTY == ...) return List.of();` + 日志，禁止任何类别自己抓配方 |
| R3 | **`mezz.jei.common.Internal` 属 JEI 内部 API** | 未来 JEI 小版本可能改签名 | 只用一个方法（`getClientSyncedRecipes()`），集中封装在 `JeiRecipeSource`，将来只需改一处 |
| R4 | **切石折叠逻辑**：26.1 的 `CondensedBlockCuttingRecipe`/`condenseRecipes` 已消失 | #2 需自写，坐标易错（5×3、第 16 个并入同槽） | 逐行照抄 26.1 `BlockCuttingCategory.getRecipes` 的折叠算法（本文件 §S1 有路径）；用「原版 250 条切石」当回归基准 |
| R5 | **工作台→工作盆**（#5/#10）：`BasinRecipe.convertShapeless` 在 26.1 是否存在未知 | 两个类别可能做不出来 | 先 javap 确认；不存在则用自建 `CraftingDisplay` + 3×3 布局（等价于 JEI 自己的合成类别布局），**或直接后置到下一期**（不阻塞首版） |
| R6 | **`GuiGameElement` 在 26.1 的 render 实参类型未知**（上游手锯晃动依赖它） | 手锯/工具图标的晃动写不出来 | 用 `graphics.item(...)` + `graphics.pose()` 手写缩放/抖动（`TwoIconRenderer` 已给出可复制范式），不依赖 `GuiGameElement` |
| R7 | **`environment: "*"` 且单 sourceset**：`compat/jei` 里引用客户端类（`GuiGraphicsExtractor` 等） | 专用服务器上若被加载会 `NoClassDefFoundError` | 插件类只由 JEI 客户端 entrypoint 加载；`CreateHandMade`/`CreateHandMadeClient` 不引用它（§8.1-3 是硬检查项）。真出问题再拆 `splitEnvironmentSourceSets`（本方案不拆，避免动 build 结构） |
| R8 | **我这边没有 shell/javap** | 部分签名是「源码反推」而非「字节码实测」 | 所有 ⚠️ 项已列出，D 用 javap 或一次编译即可定案；**不要**凭记忆补 API |
| R9 | **上游 16 个类型的 UID 必须逐字保留** | 用户旧的 JEI 收藏/书签失效 | 切片 1 的验收项里逐条核对 UID 字符串 |
| R10 | **首次构建需联网**（JEI 完整 jar 2.4 MB） | 网络不可达时构建失败 | 与现有 `libs/create-fly-*.jar` 不同，JEI 走 maven；若网络受限，可把 jar 放进 `libs/` 并改 `compileOnly files(...)`（⚠️ 需破坏「只 compileOnly maven」的现状，仅在必要时） |
| R11 | **L3 配方是否真的出现在客户端 `RecipeMap` 里** | 若客户端只同步「原版+已知 serializer」配方，L3 可能缺席 | `HandMadeRecipeSerializers` 是标准 Fabric 注册（已核实注册在 `BuiltInRegistries.RECIPE_SERIALIZER`），26.1 的 `RecipeMap` 同步是「全量配方」，预期包含；若实测缺失，退回「服务端推包 + 客户端缓存」模式（参考现有 `HandSawRecipeCache`/`HandSawRecipesPacket` 的既有做法） |
| R12 | 类别数量多、坐标靠试 | 返工 | 切片 2/3/4 都给出了可直接对照的 26.1 原类别文件路径，坐标照抄即可，禁止自由发挥 |

---

## 10. 一页速查（给 B/C 的最小信息集）

```
插件类    com.alben.createhandmade.compat.jei.CreateHandMadeJEI   (@JeiPlugin, IModPlugin)
UID       Identifier.fromNamespaceAndPath("create_hand_made", "jei_plugin")
类型      16 × IRecipeType<RecipeHolder<T>>  (常量名与上游 ModJeiTypes 一致)
类型工厂  (IRecipeType<RecipeHolder<T>>) IRecipeType.create(uid, RecipeHolder.class)
基类      com.zurrtum.create.client.compat.jei.CreateCategory<T>
          getWidth()=177 默认；需实现 getHeight/setRecipe/draw/getRecipeType/getTitle/getIcon
槽位帮助  SLOT / CHANCE_SLOT / EMPTY、addChanceSlot(b,x,y,ProcessingOutput)、addJunkSlot(b,x,y)
          addFluidSlot(b,x,y,FluidStack|FluidIngredient)、getStacks(...)、condenseIngredients(...)
icons     new TwoIconRenderer(Item, Item)   // 两个 Item，不是 ItemStack
渲染      AllGuiTextures.JEI_*.render(GuiGraphicsExtractor, x, y)
          3D 装饰: graphics.guiRenderState.addPicturesInPictureState(new XxxRenderState(new Matrix3x2f(graphics.pose()), x, y))
配方源    Internal.getClientSyncedRecipes() → RecipeMap → byType(AllRecipeTypes.X | RecipeType.X)
注册      registration.addRecipeCategories(IRecipeCategory<?>...)
          registration.addRecipes(type, collection)
          registration.addCraftingStation(type, ItemLike...)
entrypoint "jei_mod_plugin"
```

---

## B 阶段变更记录

执行日期：2026-11-15。本阶段只做**切片 1（骨架）**，未写任何类别类、未改任何既有业务代码。

### 改了哪些文件

| 文件 | 动作 | 内容 |
|---|---|---|
| `build.gradle` | 改 1 行 + 加注释 | `compileOnly "mezz.jei:jei-${minecraft_version}-fabric-api:..."` → `compileOnly "mezz.jei:jei-${minecraft_version}-fabric:..."`（完整 mod jar）。 |
| `gradle.properties` | **未改** | `jei_version` 保持 `29.47.0.111`。 |
| `src/main/resources/fabric.mod.json` | 加 entrypoint | `"jei_mod_plugin": ["com.alben.createhandmade.compat.jei.CreateHandMadeJEI"]`；`depends` 未动（无 `jei` 硬依赖），`suggests.jei` 原样保留。 |
| `compat/jei/ModJeiTypes.java` | 新增 | 16 个 `IRecipeType` 常量（UID 与上游逐字一致）+ 私有 `holderType(String)` 工厂。 |
| `compat/jei/JeiRecipeSource.java` | 新增（壳） | `recipeMap()` + `of(RecipeType<T>)`，唯一配方来源。 |
| `compat/jei/CreateHandMadeJEI.java` | 新增（空注册） | `@JeiPlugin implements IModPlugin`，`getPluginUid()` + 三个空 register 方法。 |

`neo-source/`、`README.md`、wrapper、`settings.gradle`、mixins、`gradle-daemon-jvm.properties`、Loom 版本、现有 13 个 package 全未触碰。

### 结论性事实（本阶段实测，可直接采信）

1. **`jei_version` 最终值 = `29.47.0.111`，未降级。** 编译期**没有**出现任何 JEI 侧签名错误，
   §4.4-① 的版本漂移风险**未发生**（29.6→29.47 对本次用到的符号无影响）。§9 R1 可关闭。
2. **F1 的依赖结论实测成立**：`jei-26.1.2-fabric-api:29.47.0.111` 里**没有**
   `mezz/jei/common/Internal.class`；`jei-26.1.2-fabric:29.47.0.111` 里有。所以换完整 jar 是必须的。
   （完整 jar 的传递依赖还带 `jei-26.1.2-common` / `-common-api` / `-gui` / `-lib`，`Internal` 同时存在于
   `-fabric` 与 `-common` 中。）
3. **`RecipeMap` 的真实身份与 PLAN 写法不符（PLAN 需修正）**：
   `mezz.jei.common.Internal.getClientSyncedRecipes()` 返回的是
   **`net.minecraft.world.item.crafting.RecipeMap`（Minecraft 的类，不是 JEI 的）**。
   JEI 全仓**没有**任何 `mezz.jei.*.RecipeMap`。实测签名：
   `public static RecipeMap getClientSyncedRecipes();`
   `public static boolean hasClientSyncedRecipes();`
   `public static boolean hasClientFallbackRecipes();`
   `public static boolean hasClientRecipes();`
   `RecipeMap`：`public static final RecipeMap EMPTY;`
   `public <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T>);`
   `public Collection<RecipeHolder<?>> values();`
   ⇒ §2 F1 / §5 S3 里「JEI 自己在客户端维护的 RecipeMap」应读作「JEI 持有的 MC `RecipeMap` 快照」。
4. **`RecipeHolder<T>` 的 `T` 有 `extends Recipe<?>` 上界**（MC 26.1 的 record 声明）。
   ⇒ `IRecipeType<RecipeHolder<BlockCuttingDisplay>>` **不可编译**：`BlockCuttingDisplay` 是纯显示 record、
   不是配方。已改为直接绑 `IRecipeType<BlockCuttingDisplay>`（与 Create 自己的 `BLOCK_CUTTING` 同类形态），
   UID 不变。§7.1 里「16 个类型统一是 `IRecipeType<RecipeHolder<X>>`」这句不成立，实为 15 + 1。
5. `IRecipeType.create(Identifier, Class<? extends T>)` 返回 `IRecipeType<T>`，与任务描述一致；
   传 `RecipeHolder.class` 时 `T` 只能推成裸 `RecipeHolder`，所以 `holderType(...)` 用
   `Object` 中转再做一次 unchecked cast（`@SuppressWarnings("unchecked")`）。
6. `mezz.jei.api.JeiPlugin` 注解在 29.47 **仍存在**，已加上（Fabric 侧真正的发现途径仍是
   `fabric.mod.json` 的 `jei_mod_plugin` entrypoint）。
7. `com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe` 是**非泛型**接口，
   `extends CreateRollableRecipe<SingleRecipeInput>` ⇒ 可直接作为 `RecipeHolder` 的 `T`，
   §3 #8 / §4.2 的「共同父类换成它」方案成立（`AbstractCrushingRecipe` 确实不存在）。
8. `com.zurrtum.create.client.compat.jei.display.BlockCuttingDisplay` 实测是
   `final class ... extends Record`，构造 `(Identifier, Ingredient, List<List<ItemStackTemplate>>)`，
   getter `id()/input()/outputs()` ⇒ §3 #2 的折叠目标形态确认。
9. `net.minecraft.world.item.crafting.AbstractCookingRecipe` 在 26.1 存在，是
   `abstract class ... extends SingleItemRecipe` ⇒ §3 #13/#14 用它做显示类型可行（本身可实例化的子类仍走
   `CookingDisplay`，见原方案）。

### dev 运行时（`modLocalRuntime`）试的结果

- 试过：`modLocalRuntime "mezz.jei:jei-${minecraft_version}-fabric:${jei_version}"`。
- 结果：**Gradle 配置阶段直接失败** ——
  `Could not find method modLocalRuntime() for arguments [...] on object of type DefaultDependencyHandler`，
  `Where: build file build.gradle line 31`。
- 结论：**Loom 1.19.4 不提供 `modLocalRuntime`（也没有 `modRuntimeOnly`）**，26.x 的 Fabric Loom 已不再有
  remap-mod 那套配置名。按 PLAN §6.1 的处置，本行**未保留**（只在 build.gradle 里留成注释 + 说明）。
- 待定（交给 D / 需要 dev 内验证时）：把 JEI 发布 jar 丢进 `run/mods/`，或不改构建脚本直接跳过 dev 内验证
  （§8.2 本来就是可选项）。**没有**改用 `runtimeOnly` —— 那会改变 dev 运行期类路径但无法在本阶段验证，
  留作后续选项。

### 构建结果

- `mc_gradle(task="build", args=["--no-daemon"])` → **BUILD SUCCESSFUL**（含 `compileJava` / `processResources`
  / `jar` / `sourcesJar`；`test` 为 `NO-SOURCE`，项目没有 test sourceset，本阶段未引入 JUnit 依赖）。
- 产物：`build/libs/create_hand_made-0.5.0-beta.jar`，内含
  `com/alben/createhandmade/compat/jei/{CreateHandMadeJEI,JeiRecipeSource,ModJeiTypes}.class`。
- `build/resources/main/fabric.mod.json`（`processResources` 输出）已含
  `"jei_mod_plugin": ["com.alben.createhandmade.compat.jei.CreateHandMadeJEI"]`，且 `version` 已展开为
  `0.5.0-beta`。
- 无 JEI 场景的静态保证：`CreateHandMade` / `CreateHandMadeClient` 里**没有**任何 `compat.jei` 引用
  （全库 grep 只在 `compat/jei/` 自身命中，另加一条注释文字），§8.3-3 的硬检查项通过。

### 与 PLAN 不符 / 需要 C、D 注意的点

1. **§2 F1、§5 S3**：`RecipeMap` 是 MC 的类（见上「事实 3」），不是 JEI 的；`Internal` 的两个方法名与
   返回类型已按实测写入 `JeiRecipeSource`。
2. **§3 #2、§7.1**：切石类型的泛型应为 `IRecipeType<BlockCuttingDisplay>`（无 `RecipeHolder` 包装）。
   C 阶段写 `HandSawStonecuttingCategory` 时必须按这个类型声明 `getRecipeType()`，否则泛型对不上。
3. **§7.1 `JeiRecipeSource` 的 `of` 签名**：实际写成了
   `public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> of(RecipeType<T> type)`
   —— 原版 `RecipeMap.byType` 是双类型参数，单 `T` 版本编译不过。C 阶段直接调 `of(...)` 即可，不用管 `I`。
4. **§7.1 的 `recipeMap()`**：用 `Internal.hasClientSyncedRecipes()` 判空后返回 `RecipeMap.EMPTY`，
   比「拿 `EMPTY` 做引用比较」更准（能区分「同步快照」与「fallback 配方」），debug 日志也在这里。
5. **§4.1 `getBackground()` ⚠️ 项本阶段无法定案**：本阶段没有任何类别类，`IRecipeCategory` 的抽象方法集
   仍未验证，留给 C 第一次写类别时用编译定案（预计 6 个抽象方法，与任务描述一致）。
6. **§4.4-③** 同上，未验证。
7. **本阶段未新增自动化测试**：`build.gradle` 里没有测试依赖，`test` 为 `NO-SOURCE`。若 D 需要测试入口，
   需先加 JUnit 依赖（属构建结构变更，超出 B 的允许范围，未擅自加）。
8. **探测手段备注（给 D）**：本会话没有 shell，验证 jar 符号用的是一次性 `mcProbeJavap` Gradle 任务
   （内部 `ProcessBuilder` 调 JDK 25 的 `javap`），用完即删，`build.gradle` 里没有残留。
   另外：zip 条目名可用 grep 命中、class 常量池不行（条目内容被 deflate），所以「某类是否存在」用 grep，
   「某方法签名」必须用 javap。

---

## C 切片2 变更记录

执行日期：2026-11-15。本阶段落地**切片 2：单输入多产物家族的 7 个类别**（#1 #2 #3 #7 #8 #15 #16），
`.\gradlew.bat build`（`--no-daemon`）**BUILD SUCCESSFUL**。

### 改了哪些文件（全部在 `src/main/java/com/alben/createhandmade/compat/jei/` 下）

| 文件 | 动作 | 内容 |
|---|---|---|
| `category/HandSawCategory.java` | 新增 | #1 手锯切削；布局照抄 `SawingCategory`；源 `CUTTING` + L3 `HandMadeCuttingRecipe` |
| `category/HandSawStonecuttingCategory.java` | 新增 | #2 手锯切石；`extends BlockCuttingCategory`，只覆类型/标题/图标 + 自写折叠器 |
| `category/MortarMillingCategory.java` | 新增 | #7 研钵研磨；布局照抄 `MillingCategory`；源 `MILLING` + L3(`tool=MORTAR`) |
| `category/CrusherMortarCrushingCategory.java` | 新增 | #8 碾钵粉碎；布局照抄 `CrushingCategory`；源 `CRUSHING`→去重 `MILLING`→L3 |
| `category/HandPressDepotCategory.java` | 新增 | #3 冲压锤置物台；布局照抄 `PressingCategory`；源 `PRESSING` + L3 |
| `category/BellowsHauntingCategory.java` | 新增 | #15 风箱缠魂；布局照抄 `FanHauntingCategory`；源 `HAUNTING` + L3(`fan_type=haunting`) |
| `category/BellowsSplashingCategory.java` | 新增 | #16 风箱洗涤；布局照抄 `FanWashingCategory`；源 `SPLASHING` + L3(`fan_type=splashing`) |
| `JeiRecipeSource.java` | 扩展 | 仍是唯一配方来源：新增 `appendRollable`/`rollable`/`stonecutting()`/`l3Tool()/l3Stonecutting()/l3Bellows()` + `record StonecuttingEntry` |
| `ModJeiTypes.java` | 改泛型 | `HAND_SAW_CUTTING`/`HAND_PRESS_DEPOT`/`MORTAR_MILLING`/`BELLOWS_HAUNTING`/`BELLOWS_SPLASHING` 由具体 record 改绑 `RecipeHolder<CreateSingleStackRollableRecipe>`；**UID 一个字符未动**；`HAND_SAW_STONECUTTING` 仍是 `IRecipeType<BlockCuttingDisplay>` |
| `CreateHandMadeJEI.java` | 补注册 | 7 个类别实例 + `registerRecipes` 7 次 `addRecipes` + `registerRecipeCatalysts` 挂 `HAND_SAW`/`MORTAR`/`CRUSHER_MORTAR`/`PRESS_HAMMER`/`BELLOWS` |

`neo-source/`、`README.md`、`build.gradle`、`fabric.mod.json`、`gradle/`、wrapper、Loom、mixins、
`settings.gradle` 及 PLAN 既有章节**全未触碰**；`CreateHandMade`/`CreateHandMadeClient` 无 `compat.jei` 引用（grep 已核）。

### 布局来源（每个类别照抄哪个 Create 类，`javap -p -c` 逐常量核对，坐标未自由发挥）

| 本模组类别 | 抄自 | 关键坐标 |
|---|---|---|
| HandSawCategory | `SawingCategory` | 输入 (44,5)；产物单条 (118,48)、多条 118/137 每行 -19；`JEI_DOWN_ARROW`(70,6)、`JEI_SHADOW`(55,55)、装饰 (64,31)；高 70 |
| HandSawStonecuttingCategory | `BlockCuttingCategory`（继承） | 输入 (5,5)；产物 5 列自 (78,48)；`JEI_DOWN_ARROW`(31,6)、`JEI_SHADOW`(16,50)；高 70 |
| MortarMillingCategory | `MillingCategory` | 输入 (15,9)；产物单条 (139,27)、多条 133/152；`JEI_ARROW`(85,32)、`JEI_DOWN_ARROW`(43,4)、`JEI_SHADOW`(32,40)、装饰 (42,19)；高 53 |
| CrusherMortarCrushingCategory | `CrushingCategory` | 输入 (51,3)；产物 `3+(179-19n)/2` 起、y=83、步长 19；`JEI_DOWN_ARROW`(72,7)、装饰 (42,24)；高 100 |
| HandPressDepotCategory | `PressingCategory` | 输入 (27,51)；产物 `131+19i`、y=51；`JEI_SHADOW`(61,41)、`JEI_LONG_ARROW`(52,54)、装饰 x=73（y 由 -16 改 8）；高 70 |
| BellowsHauntingCategory | `FanHauntingCategory` | 单条 输入(21,48)/产物(141,48)；多条 `shift=1-min(3,n)`，输入 `21+shift*5`、产物 `141+shift*9`、y=48/57、3 列 +19 每行 -19；`JEI_SHADOW`(46,27)、`JEI_LIGHT`(65,39)、`JEI_LONG_ARROW`(54+7shift,51)、装饰 (56,4)；高 72 |
| BellowsSplashingCategory | `FanWashingCategory` | 同缠魂；差异是两块 `JEI_SHADOW`（46,27）+（65,39），无水那侧的 `JEI_LIGHT` |

### 本阶段实测得到的、与 PLAN / 任务 facts 不符或需修正之处

1. **`SingleItemRecipe#result()` 在 26.1 是 `protected`**（`javap -p` 实测），跨包取不到。
   而 Create 的 `BlockCuttingCategory` 字节码里直接 `invokevirtual StonecutterRecipe.result()` —— 说明
   Create 的编译期访问标志与运行期 MC jar 不一致（潜在 `IllegalAccessError` 隐患，不属本模组问题。
   本模组取数改走 **public 的 `assemble(SingleRecipeInput)`**（其实现就是 `result.create()`），
   再 `ItemStackTemplate.fromNonEmptyStack`，避开该访问性问题。
2. **`Ingredient` 覆写了 `equals` 但没有 `hashCode()`**（`javap -p` 实测）⇒ 不能当 `HashMap` 键
   （Create 为此用了内部类 `BlockCuttingCategory$1` 的自定义 `Hash.Strategy`）。折叠器改为
   线性查表 + `equals` 分组，语义等价。
3. **`CreateCategory.SLOT/CHANCE_SLOT/EMPTY` 的字段类型是 `SlotRenderer`**（不是 `IDrawable`）；
   仍可直接传给 `IRecipeSlotBuilder#setBackground(IDrawable,int,int)`。`CreateCategory` 只实现
   `getWidth()`，其余 5 个抽象方法由子类补。
4. **`AllGuiTextures` 还多出 `JEI_CATALYST_SLOT`、`JEI_QUESTION_MARK`**（PLAN §4.2 的清单可补）；
   `render` 有两个重载：`(GuiGraphicsExtractor,int,int)` 与 `(…, Color)`。
5. **`IRecipeCategory` 的完整方法集**：6 个抽象（`getRecipeType/getTitle/getWidth/getHeight/getIcon/setRecipe`）
   + default 的 `createRecipeExtras`/`onDisplayedIngredientsUpdate`/`getTooltip`/`isHandled`/`getIdentifier`/
   `getSourceRecipeIds`/`getRegistryName`/`getCodec`/`needsRecipeBorder`/`draw`。**`getBackground()` 不存在**
   ⇒ PLAN §4.1 最后一行与 §4.4-③ 可关闭。
6. **数据源筛选用身份比较是安全的**：`CuttingRecipe`/`PressingRecipe`/`HauntingRecipe` 等的
   `getType()` 直接 `getstatic AllRecipeTypes.X`（`javap -c` 实测），所以 `recipe.getType() == type`
   与 `RecipeMap.byType` 等价。`JeiRecipeSource` 因此改成遍历 `RecipeMap.values()`（全量，实测 = `byKey.values()`），
   完全绕开 `byType` 的双类型参数推演（与 `HandMadeRecipePool.recipesOfType` 同一写法）。
7. **L3 类型不要写成 `static final`**：`HandMadeRecipeTypes.getType()` 的字段在 `register()` 里才赋值，
   写成类常量会在类初始化时抓死 `null`（静默空类别）。现改成 `l3Tool()/l3Stonecutting()/l3Bellows()` 方法。
8. 装饰用的 3D RenderState 实际签名 `(Matrix3x2f,int,int)`（`FanRenderState` 多一个 `BlockState`），
   包在 `com.zurrtum.create.client.foundation.gui.render`。**本阶段没有用它们** —— 按任务要求把
   机械锯/磨盘/压床/鼓风机换成工具物品图标（`GuiGraphicsExtractor#item(ItemStack,int,int)`，实测存在）；
   图标坐标一律沿用 RenderState 原点，唯一例外是冲压锤（原点 y=-16 会被面板裁掉，改 y=8，已在类注释说明）。
9. 风箱两个类别不传 L2 过滤工具：`HandMadeTool` 里没有风箱常量（风箱走 Create 的
   `FanProcessingType`，不进 Pool），与服务端行为一致。config 开关（`allowStonecuttingOnSaw` 等）在客户端读不到
   （`AllConfigs.server()` 是服务端对象），JEI 这一层不做 config 门。
10. 切石类别 `draw` 仍继承 `BlockCuttingCategory`（装饰是机械锯 3D 模型）：按任务约定只覆 4 个方法，
    若要换成手锯图标只需追加一个 `draw` 覆写（做法已写在该类注释里）。

### 构建与验证

- `mc_gradle(task="build", args=["--no-daemon"])` → **BUILD SUCCESSFUL in 25s**（`compileJava` 一次通过，
  未出现 JEI 版本漂移）。产物 `build/libs/create_hand_made-0.5.0-beta.jar` 内含
  `com/alben/createhandmade/compat/jei/category/` 下 7 个类别 class（含 `HandSawStonecuttingCategory$Group`）。
- 仍未做 dev 内运行验证（§8.2 依旧可选）：slot 坐标、图标位置、分类是否为空都需 `runClient` 或正式客户端目视。
- 探测手段备注（给 D）：本阶段同样没有 shell，全部签名/字节码来自**一次性 Gradle init script**
  （`-I <临时目录>/probe-init*.gradle`，内部 `ProcessBuilder` 调 JDK 25 的 `javap`，输出写到 `%TEMP%\jei-probe\out`）。
  **没有**往 `build.gradle` 写任何临时任务，项目内零残留。

---

## C 切片3 / 切片4 变更记录

执行日期：2026-11-15。本节点补齐两件事：**① 切片 3 的交付归属更正**、**② 切片 4（归一化家族 3 类）
落地 + R5 裁定**。`mc_gradle(task="build", args=["--no-daemon"])` → **BUILD SUCCESSFUL**（退出码 0），
产物 `build/libs/create_hand_made-0.5.0-beta.jar` 已确认内含本节新增的 4 个 class。

### 切片 3 的归属更正（重要）

- **切片 3 的 4 个类别（`HandPressBasinCategory` #4 / `StirringStaffMixingCategory` #9 与内嵌
  `Brewing` #11 / `PointerApplicationCategory` #12）以及 `category/BasinSlots.java`，是由 lead
  手动补注册/补写的 —— 原 C 会话在该切片中途中断，不是 C 子代理的产出。**
  本节点的 C 子代理只负责切片 4（下面全部内容）。D 审查 diff 时请按这个归属看。
- 现状：11 个已注册类别 = 切片 2 的 7 个（C 交付）+ 切片 3 的 4 个（lead 补）。

### 本节点改了哪些文件（全部在 `src/main/java/com/alben/createhandmade/compat/jei/` 下）

| 文件 | 动作 | 内容 |
|---|---|---|
| `display/BellowsCookingDisplay.java` | **新增** | `record(Identifier id, Ingredient input, ItemStackTemplate result)`，熔炼/烟熏的归一化显示类型 |
| `category/BellowsCookingCategory.java` | **新增** | #13/#14 一个类两个实例（`new BellowsCookingCategory(FanType.BLASTING, Items.LAVA_BUCKET)` / `(SMOKING, Items.CAMPFIRE)`） |
| `category/HandInfusionGunCategory.java` | **新增** | #6 灌注枪；内含私有 `record View` 做 record 分派 |
| `JeiRecipeSource.java` | 扩展 | 新增 `bellowsCooking(FanType)` + `singleItemRecipes` / `addCooking` / `hasMatching` / `resolveInputStack` / `appendBellowsCookingL3` + `appendFilling(...)`；类注释补一条风箱家族的筛选语义 |
| `ModJeiTypes.java` | 改 3 个泛型 | `BELLOWS_BLASTING` / `BELLOWS_SMOKING` → `IRecipeType<BellowsCookingDisplay>`；`HAND_INFUSION_GUN` → `IRecipeType<RecipeHolder<CreateRecipe<FillingInput>>>`；新增私有 `displayType(...)` 工厂（`HAND_SAW_STONECUTTING` 顺带改用它，形态不变）。**16 个 UID 一个字符未动** |
| `CreateHandMadeJEI.java` | 补注册 | 3 个类别实例 + `registerRecipes` 3 次 + 催化剂（熔炼/烟熏 → `ModItems.BELLOWS`，灌注枪 → `ModItems.INFUSION_GUN`）+ R5 判定注释 + 类顶部 javadoc 更新为最终进度 |

`neo-source/`、`README.md`、`build.gradle`、`fabric.mod.json`、`gradle/`、wrapper、Loom、mixins、
`settings.gradle`、现有 13 个 package **全未触碰**（唯一的例外是 `PLAN.md` 追加本节）；
`CreateHandMade` / `CreateHandMadeClient` 仍**没有**任何 `compat.jei` 引用。

### 布局来源（`javap -p -c` 逐指令核对，坐标未自由发挥）

| 本模组类别 | 抄自 | 关键坐标 / 常量 |
|---|---|---|
| `BellowsCookingCategory`（熔炼） | `FanBlastingCategory` | 输入 `addInputSlot(21,48)` + `SLOT`；产物 `addOutputSlot(141,48)` + `SLOT` + `add(ItemStackTemplate)`；`JEI_SHADOW(46,27)`、`JEI_LIGHT(65,39)`、`JEI_LONG_ARROW(54,51)`；装饰原点 `(56,4)`（原为 `FanRenderState(…,56,4,Fluids.LAVA…)`）；高 72 |
| `BellowsCookingCategory`（烟熏） | `FanSmokingCategory` | **与熔炼逐字节相同**（唯一差别是 `FanRenderState` 的介质 BlockState：`Blocks.FIRE`）；高 72 |
| `HandInfusionGunCategory` | `SpoutFillingCategory` | 输入物品 `(27,51)`；输入流体 `(27,32)` + `setSlotName("fluid")`；输出 `(132,51)` + `add(ItemStackTemplate)`；`JEI_SHADOW(62,57)`、`JEI_DOWN_ARROW(126,29)`；装饰原点 `(75,1)`（`SpoutRenderState`）；高 70 |

### 三个类别的一句话说明

1. **熔炼 / 烟熏的去重规则**：`BLASTING` 的条目排除「存在同输入 `SMOKING`」的、`SMELTING` 的条目排除
   「存在同输入 `BLASTING` 或 `SMOKING`」的（`SingleRecipeInput` + `recipe.matches(input, level)` 判定，
   自身与被找到的那条都要过 `AllRecipeTypes.CAN_BE_AUTOMATED`）；烟熏类别只扫 `SMOKING`、不去重；
   L3 `bellows_recipe` 按 `fan_type` 分流、多产物只取第一个，追加在最后且**不参与**去重。
2. **灌注枪的归一化方案**：显示类型从 `RecipeHolder<FillingRecipe>` 换成
   `RecipeHolder<CreateRecipe<FillingInput>>`（两者都 implements 该接口，`javap` 实测），
   三个 accessor 由类别内私有 `record View` 按具体 record 分派 —— 与 `BasinSlots` 同思路，UID 未动。
3. **R5 裁定：两个类型不注册**（详见下）。`CreateHandMadeJEI.registerCategories` 上方留了判定注释。

### R5 判定：`BasinRecipe` **没有**工作台→工作盆转换入口 ⇒ #5 / #10 不注册

`javap -p -c com.zurrtum.create.content.processing.basin.BasinRecipe` 实测到的**全部**静态成员：

```
SHAPELESS_CACHE : Map<ShapelessRecipe, List<SizedIngredient>>
SHAPED_CACHE    : Map<ShapedRecipe,     List<SizedIngredient>>
matchCraftingRecipe(BasinInput, ShapelessRecipe, Level)   / (BasinInput, ShapedRecipe, Level)
applyCraftingRecipe(BasinInput, ShapedRecipe, Level)      / (BasinInput, ShapelessRecipe, Level)
tryCraft(BasinInput, Ingredient | SizedIngredient | List<SizedIngredient>)
prepareCraft(...) / prepareFluidCraft(...) / addRecipeRemainder(...) / matchFluidIngredient(...)
```

⇒ **没有 `convertShaped` / `convertShapeless` 之类的方法**（上游 NeoForge 1.21.1 有），
而 `matchCraftingRecipe` / `applyCraftingRecipe` 的第一个参数是运行期才存在的 `BasinInput`
（内部要用 `ServerFilteringBehaviour`），**不能**把 `CraftingRecipe` 转成可显示的 `BasinRecipe`。
两个类型绑的是 `RecipeHolder<BasinRecipe>`，需要一个 `BasinRecipe` 实例；`CraftingRecipe` 并不实现它。
按任务约定（「没有 → 不注册 + 留注释」，**不要**自造 3×3 布局），`HAND_PRESS_BASIN_AUTO_SQUARE` 与
`STIRRING_STAFF_AUTO_SHAPELESS` 两个常量保留在 `ModJeiTypes`（UID 留给以后）但**不注册**。
游戏内这两个能力本身不受影响。

### 与提示 facts / PLAN §7.1 不符或需修正之处

1. **§7.1 的文件清单**：原计划写 `display/CookingDisplay.java` + `display/FillingDisplay.java`；
   实际只新建了 `display/BellowsCookingDisplay.java`。理由：灌注枪的显示类型被裁定为
   **接口** `CreateRecipe<FillingInput>`（见上），不需要中间 display record；而 `CookingDisplay`
   改名为 `BellowsCookingDisplay`（内容同形：`id + input + result`），避免与「原版烹饪」混淆。
2. **`Ingredient#items()` 在 26.1 已 deprecated**（`-Xlint:deprecation` 实测）。所以没有照抄
   `FanBlastingCategory.addRecipe` 的 `input.values.stream()` 分支，统一走
   `Ingredient#display().resolveForFirstStack(ContextMap)`；输入物品栈**只用于去重**，
   不用于显示，因此没有 level / ContextMap 时跳过的是「去重」而不是「这条配方」。
3. **`FanBlastingCategory` 用的是 `Minecraft.getInstance().level`（无判空）**；本实现取同样的 level，
   但判空降级（`SlotDisplayContext.fromLevel` 需要非 null 的 level），不会在注册期 NPE。
4. **`_manual_only` 过滤在 26.1 不存在**：全仓 grep 无命中（PLAN §3 #14 提到的这个过滤项没有实现载体），
   风箱四个类别也都不走 `HandMadeRecipeFilters`（`HandMadeTool` 里没有风箱常量）。
   本实现的 L2 过滤：灌注枪走 `HandMadeTool.INFUSION_GUN`，风箱熔炼/烟熏**不过** L2 表。
5. **`SpoutFillingCategory` 确实没有 `consumeRecipes`**（`javap -p` 只看到
   `getRecipes(RecipeMap, Stream<ItemStack>, Stream<IJeiFluidIngredient>)` /
   `MAX` / `idGenerator` / 各覆写方法）⇒ 上游「转换配方」扩展本期不做，已在
   `HandInfusionGunCategory` 的类注释里写明。
6. **`getIdentifier(T)` 是 `IRecipeCategory` 的 default 方法**（`javap -p` 实测）；
   `BellowsCookingCategory` 覆写它返回 `display.id()`（纯显示类型没有 `RecipeHolder` 兜底，
   不覆写就没有稳定 uid），灌注枪类别不覆写（与其它 `RecipeHolder` 类别一致）。
7. **`AllGuiTextures` 的两个渲染重载**在前几切片已记录；本节点只用 `(GuiGraphicsExtractor,int,int)`。
8. **装饰统一换成物品图标**（延续切片 2 的决定）：熔炼/烟熏在 `(56,4)` 画风箱图标、灌注枪在 `(75,8)`
   画灌注枪图标（y 由 Create 的 1 改成 8，让 16×16 图标落在注液口中段）；介质（熔岩桶 / 营火）
   只出现在类别图标 `TwoIconRenderer` 的第二个图标里，不再单独占面板空间（免得与
   `JEI_LONG_ARROW(54,51)` / `JEI_LIGHT(65,39)` 重叠）。
9. **`ModJeiTypes` 新增 `displayType(String, Class)` 私有工厂**；`HAND_SAW_STONECUTTING` 顺带改用它
   （同一重载 `IRecipeType.create(Identifier, Class)`，UID 与泛型都没变）。
10. **`ModJeiTypes` 的 16 个 UID → 14 个已注册**（见 §「R5 判定」）；jar 内类清单可对照本节点表格。

### 构建与验证

- `mc_gradle(task="build", args=["--no-daemon"])` → **BUILD SUCCESSFUL**；jar 内含
  `compat/jei/display/BellowsCookingDisplay.class`、`category/BellowsCookingCategory.class`、
  `category/HandInfusionGunCategory.class` + `HandInfusionGunCategory$View.class`。
- `-Xlint:deprecation` 复测：本节点新增代码**零** deprecation 警告（剩余 8 条全在既有 package：
  `bellows/`、`item/InfusionGunItem`、`recipe/HandMadeRecipeFilter*`，属既有代码，未触碰）。
- 仍未做 dev 内运行验证（§8.2 可选）：槽位坐标、图标位置、类别是否为空都需 `runClient`/正式客户端目视。
- 探测手段：同样是一次性 Gradle init script（`-I C:/Users/Colin/AppData/Local/Temp/jei-probe/*.gradle`，
  内含 `ProcessBuilder` 调 JDK 25 的 `javap` 与 `ZipFile` 枚举），项目内**零残留**。

---

## D 阶段验证记录

执行日期：2026-11-15。范围：构建 + 产物静态检查 + 注册表一致性 + 未装 JEI 的启动安全 + 语言键 +
过时文档复核。**结论：可交付**（构建与静态判据全过）；运行期目视项仍未验证（见 §7）。
**本阶段未改动任何源码/资源**（`compat/jei/`、两个语言 json、`build.gradle`、`fabric.mod.json` 均未触碰）。

### 1. 构建

- `mc_gradle(task="build", args=["--no-daemon","--rerun-tasks"])` → **BUILD SUCCESSFUL in 18s，退出码 0**；
  `compileJava` / `processResources` / `jar` / `sourcesJar` 全部 executed（非 UP-TO-DATE）。
  `test` = `NO-SOURCE`（项目无 test sourceset）。deprecation 提示来自既有 package，与 `compat/jei` 无关。

### 2. 产物静态检查（一次性 init script 枚举 zip 条目，项目内零残留）

- `build/resources/main/fabric.mod.json` 与 jar 内 `fabric.mod.json` **均含**
  `"jei_mod_plugin": ["com.alben.createhandmade.compat.jei.CreateHandMadeJEI"]`，`version` 已展开为
  `0.5.0-beta`（无 `${version}` 残留）；`depends` 里**没有** `jei`，`suggests.jei` 保留。
- `build/libs/create_hand_made-0.5.0-beta.jar`：205 条目，`com/alben/createhandmade/compat/jei/` 下
  **21 个 class**（插件、`JeiRecipeSource`(+`$StonecuttingEntry`)、`ModJeiTypes`、`display/BellowsCookingDisplay`、
  12 个类别类 + `BasinSlots` + 3 个内部类 `HandInfusionGunCategory$View` / `HandSawStonecuttingCategory$Group` /
  `StirringStaffMixingCategory$Brewing`）。
- **`mezz/` 前缀条目 = 0**（`compileOnly` 未被打进产物）；`com/zurrtum/` 也 = 0（Create Fly 走 `libs/`，非 JiJ）。

### 3. 注册表一致性

- 16 个 UID path 与上游 `neo-source/com/alben/createhandmade/compat/jei/ModJeiTypes.java` **逐字一致**
  （namespace 均为 `create_hand_made` = `CreateHandMade.MODID`）。
- `registerCategories` **14** 个实例 == `registerRecipes` **14** 次 `addRecipes` == `registerRecipeCatalysts`
  **14** 次 `addCraftingStation`；每个类别的 `getRecipeType()` 返回值与 `addRecipes` 的键一一对应。
- 已注册 14：`hand_saw_cutting`、`hand_saw_stonecutting`、`hand_press_depot`、`hand_press_basin`、
  `hand_infusion_gun`、`mortar_milling`、`crusher_mortar_crushing`、`stirring_staff_mixing`、
  `stirring_staff_auto_brewing`、`pointer_application`、`bellows_blasting`、`bellows_smoking`、
  `bellows_haunting`、`bellows_splashing`。
- 未注册 2（**约定内例外，非缺陷**）：`hand_press_basin_auto_square`、`stirring_staff_auto_shapeless`。
- 14 个已注册类型都有对应的 `getRecipes()`（13 个方法，`BellowsCookingCategory.getRecipes(FanType)` 服务 2 个类型），
  且全部只调 `JeiRecipeSource` 的取数方法 —— **没有任何一处给 `addRecipes` 传 `List.of()` 字面空集合**。

### 4. 未装 JEI 的启动安全（静态）

- `CreateHandMade.java` / `CreateHandMadeClient.java` 中 **0** 处 `compat.jei` 引用（全 `src/main/java` grep
  只在 `compat/jei/` 内命中）。
- `mezz.jei` 只出现在 `compat/jei/` 的 13 个文件里；`compat/jei` 之外 **0** 处。

### 5. 语言键

- 14 个类别的 `TITLE_KEY` 常量值逐个对照：`en_us.json` / `zh_cn.json` **全部存在**（两个文件都齐 16 个
  `jei.create_hand_made.*`，含两个未注册类型）⇒ **无需补键**。

### 6. 过时 ⚠️ 项复核（均已被既有阶段记录覆盖，未改原章节）

| 项 | 现状 |
|---|---|
| §4.1 `IRecipeCategory#getBackground()` 不存在 | 已由「C 切片2 变更记录·实测 5」关闭；本阶段编译再次确认无该抽象方法 |
| `addRecipes` 收 `List` | `CreateHandMadeJEI.registerRecipes` javadoc 已写明，代码与注释一致 |
| `CuttingRecipe implements CreateSingleStackRollableRecipe` | 编译通过 + L1/L3 归一化已落地，§3 / §F3 结论成立 |

### 7. 仍未验证（需用户目视 / 运行期，**不要当已通过**）

1. **S3**：卸掉 JEI 启动一次（静态已保证无引用，运行期未实测）。
2. **S4**：JEI 里 14 个类别的槽位坐标、图标位置（装饰物品图标代替 3D 模型）、类别是否为空、点亮物品反查催化剂。
3. **R11**：L3 配方是否真的出现在客户端 `RecipeMap` 里 —— 若缺席，对应类别会「存在但为空」且不报错。
4. `runClient` 未做：Loom 1.19.4 无 `modLocalRuntime`，dev 类路径里没有 JEI（§8.2 本来就是可选）。
