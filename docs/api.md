# Create: Hand Made 对外 API

适用版本：**1.20.1 Forge / 0.6.2-beta+**
入口类：`com.alben.createhandmade.api.CreateHandMadeApi`
`API_VERSION = 1`

跨模组请优先绑定 `CreateHandMadeApi`，不要直接依赖各工具类的 public 方法。
工具类的 40 个公开方法不保证跨版本稳定；`CreateHandMadeApi` 的签名视为稳定契约。

---

## 稳定性承诺

- `API_VERSION` 只在**破坏性变更**时递增；新增方法不算破坏性变更。
- 本 API 只提供**查询 / 读写组件**级别能力，**不提供“替玩家执行一次加工”的写操作**。
- 需要执行加工时，调用各工具类的 public 方法，并自行承担副作用（见下文“写操作副作用”）。

---

## 方法总览（13 个）

### 配方查询

| 方法 | 返回 | 说明 |
|---|---|---|
| `recipesFor(ToolType tool, Level level)` | `List<Recipe<?>>` | 某工具在给定世界里可用的全部基础配方（L1 + L3，顺序即游戏内查询优先顺序）。`level == null` 时返回空列表。 |
| `isRecipeDisabled(ToolType tool, ResourceLocation recipeId)` | `boolean` | 该配方是否被数据包 / KubeJS 过滤器对本工具禁用。三个过滤表任一命中即 `true`。 |

### 风箱介质

| 方法 | 返回 | 说明 |
|---|---|---|
| `bellowsMediaTypes(ItemStack stack)` | `List<ResourceLocation>` | 该物品能充当哪些风箱处理类型。可能为空列表，不返回 `null`。 |
| `isBellowsMedia(ItemStack stack)` | `boolean` | 该物品是否能作为风箱介质。 |
| `bellowsMediaItems(ResourceLocation typeId)` | `List<ItemStack>` | 某风箱处理类型对应的介质物品列表。 |
| `isBellowsTypeDisabled(ResourceLocation typeId)` | `boolean` | 某风箱处理类型是否被配置禁用。 |

### 灌注枪

| 方法 | 返回 | 说明 |
|---|---|---|
| `infusionGunCapacity()` | `int` | 灌注枪容量（mB），当前为 `1000`。 |
| `infusionGunContents(ItemStack gun)` | `InfusionGunContents` | 读枪内流体。**永不返回 `null`**；空枪返回 `new InfusionGunContents(FluidStack.EMPTY)`。 |
| `setInfusionGunContents(ItemStack gun, InfusionGunContents contents)` | `void` | 写枪内流体。`contents` 为空或 `null` 时移除 NBT 组件（纯空枪不留空组件）。 |
| `fluidSourceAt(Level level, BlockPos pos)` | `FluidStack` | 读取世界某位置的流体源。**只读**，不消耗、不改变世界。非源方块返回 `FluidStack.EMPTY`。 |

### 研钵 / 碾钵

| 方法 | 返回 | 说明 |
|---|---|---|
| `mortarContents(ItemStack stack)` | `@Nullable MortarContents` | 读研钵内容物；空碗返回 `null`。 |
| `setMortarContents(ItemStack stack, @Nullable MortarContents contents)` | `void` | 写研钵内容物；传 `null` 表示清空。 |
| `mortarProcessingTicks(boolean crusherMortar)` | `int` | 按当前配置计算加工 tick（不含起手）。`crusherMortar == true` 读碾钵配置，`false` 读研钵配置。 |

---

## 写操作副作用（直接调用工具类 public 方法时必读）

本 API 不提供写操作。如果你需要执行加工，调用下表中的工具类 public 方法，并自行承担副作用：

| 方法 | 副作用 |
|---|---|
| `InfusionGunItem#tryInjectItems` | 消耗枪内流体、转化传送带物品、播放粒子/音效、扣耐久；**只在服务端调用** |
| `InfusionGunItem#tryStoreFluid` | 把枪内流体存入方块、扣耐久；**只在服务端调用** |
| `InfusionGunItem#tryExtractTick` | 从方块抽取流体进枪、扣耐久；**只在服务端调用** |
| `PointerItem#executeDepotProcess` | 直接改置物台状态并产出；**只在服务端调用** |
| `BellowsItem#applyToTarget` | 对目标应用鼓风效果，跨模组触发上游效果，可能转化物品 |
| `PressHammerItem#tryPressBasin` / `tryPressTransported` | 消耗输入并产出，播放粒子/音效；**只在服务端调用** |
| `HandSawItem#executeCut` / `stripBlock` | 破坏/改变世界方块；**只在服务端调用**，需自行负责耐久与玩家行为约束 |
| `MortarItem#tryGrindOnce` / `CrusherMortarItem#tryGrindOnce` | 消耗输入、产出结果、扣耐久、播放粒子/音效；**只在服务端调用** |

本模组自身的调用点都在 `if (!level.isClientSide)` 之内；开放后请在调用方自行保证这一前提。

---

## 调用示例

### 查询某工具的全部可用配方

```java
List<Recipe<?>> recipes = CreateHandMadeApi.recipesFor(ToolType.HAND_SAW, level);
for (Recipe<?> recipe : recipes) {
    // 注意：1.20.1 的裸 Recipe 没有 id()
    // 需要配方 ID 时用 RecipeManager 反查：
    ResourceLocation id = level.getRecipeManager()
            .getRecipeIds()
            .filter(r -> level.getRecipeManager().byKey(r).orElse(null) == recipe)
            .findFirst()
            .orElse(null);
    if (id != null && CreateHandMadeApi.isRecipeDisabled(ToolType.HAND_SAW, id)) {
        continue; // 被数据包 / KubeJS 禁用
    }
}
```

## 判断物品能否作为风箱介质
```java
ItemStack stack = new ItemStack(Items.POWDER_SNOW_BUCKET);
if (CreateHandMadeApi.isBellowsMedia(stack)) {
    List<ResourceLocation> types = CreateHandMadeApi.bellowsMediaTypes(stack);
    // 例如 [create_dragons_plus:freezing]
}
```

## 读 / 写灌注枪内容物
```java
InfusionGunContents contents = CreateHandMadeApi.infusionGunContents(gun);
if (!contents.isEmpty()) {
    FluidStack fluid = contents.fluid();
    int amount = fluid.getAmount();
    int remaining = contents.remaining();
}

// 清空
CreateHandMadeApi.setInfusionGunContents(gun, null);

// 写入
CreateHandMadeApi.setInfusionGunContents(gun,
        new InfusionGunContents(new FluidStack(Fluids.WATER, 500)));
```

## 读研钵内容物
```java
MortarContents contents = CreateHandMadeApi.mortarContents(mortar);
if (contents != null) {
    ItemStack input = contents.stack();
    int batchCount = input.getCount();
    int ticks = CreateHandMadeApi.mortarProcessingTicks(false); // 研钵
}
```

## 1.20.1 与 1.21 的差异
跨版本移植时注意：
| 项 | 1.21 NeoForge | 1.20.1 Forge |
|---|---|---|
| 工具枚举 | `HandMadeTool` (12 值) | `ToolType`（10 值） |
| `recipesFor` 返回 | `List<RecipeHolder<?>>` | `List<Recipe<?>>`，没有 `RecipeHolder` 包装，调用方不要 `.value()` |
| 配方 ID | `RecipeHolder.id()` 直接可取 | 裸 `Recipe` 没有 `id()`，要用 `RecipeManager.getRecipeIds() + byKey()` 反查 |
| `mortarProcessingTicks` | `(MortarContents, boolean)` | 	`(boolean)`，不看内容物，tick 由配置固定值决定 |
| FluidStack.matches | 存在 | 不存在，手写 `getFluid()` / `getAmount()` / `getTag()` 比较 |
| ItemStack.isSameItemSameComponents | 1.20.5+ | 1.20.1 用 `isSameItemSameTags` |
| 内容物存储 | `DataComponentType` | `ItemStack` NBT |
| 三个 AUTO 类别 | 独立 `HandMadeTool` 值，`getBaseRecipes` 能查到 | 走 L2 过滤，`getBaseRecipes` 不返回，JEI 各类别单独收集 |

# 已知限制
1. recipesFor 的 L3 在 switch 之后统一收：不要在 switch 里给某个工具单独加 TOOL_RECIPE 查询，会重复。
2. 碾钵接受研钵的 L3：CRUSHER_MORTAR 的 getBaseRecipes 会同时收 ToolType.MORTAR 的独占配方，这是设计行为，不是 bug。
3. mortarProcessingTicks(boolean) 的 crusherMortar 参数：1.20.1 中研钵与碾钵各有独立配置段（[tools.mortar] / [tools.crusher_mortar]），参数为 true 时读碾钵配置，false 时读研钵配置。不要传 null 或其他值，布尔参数没有三态。
4. fluidSourceAt 只读：调用它不会消耗流体、不会改变方块状态。需要实际抽取时用 InfusionGunItem#tryExtractTick，并注意上文的副作用表。
5. infusionGunContents 永不返回 null：空枪返回 new InfusionGunContents(FluidStack.EMPTY)。判断空枪用 contents.isEmpty()，不要用 == null。
6. mortarContents 可能返回 null：空碗返回 null，和 infusionGunContents 的语义不同。判断空碗用 contents == null || contents.stack().isEmpty()。

# 补充
没开放、但需要知道的 3 个签名差异
| 1.21 | 1.20.1 | 处理 |
|---|---|---|
| tryPressBasin(Level, BlockPos) | tryPressBasin(Level, BlockPos, @Nullable FilterItemStack) | 1.20.1 多一个 filter 参数。“1.20.1 支持传入过滤器”。 |
| tryPressTransported(Level, BlockPos) | `tryPressTransported(Level, BlockPos, @Nullable FilterItemStack)` + `tryPressTransported(Level, BlockPos, @Nullable FilterItemStack, int batchSize)` | 1.20.1 多一个 filter 参数 + 一个 batchSize 重载。|
| onCriticalHit(CriticalHitEvent) | onLivingHurt(LivingHurtEvent) | 方法名和事件类型都不同。保留 1.20.1 的 onLivingHurt，不要为了对齐改名——Forge 1.20.1 的暴击事件是 CriticalHitEvent，但 1.20.1 这里用的是 LivingHurtEvent，改名会破坏事件注册。1.20.1 用 onLivingHurt，1.21 用 onCriticalHit。 |

### 手锯 / 冲压锤 / 指杆 / 搅拌杖 / 风箱（写操作与查询）

| 方法 | 返回 | 说明 |
|---|---|---|
| `handSawRecipes(Level, ItemStack)` | `List<Recipe<?>>` | 手锯在某输入上可用的切削配方 |
| `findPressingRecipe(Level, ItemStack)` | `@Nullable Recipe<?>` | 冲压锤压制配方 |
| `pressBasin(Level, BlockPos, @Nullable FilterItemStack)` | `boolean` | 工作盆冲压，只在服务端调用 |
| `pressTransported(Level, BlockPos, @Nullable FilterItemStack)` | `boolean` | 传送带冲压，只在服务端调用 |
| `grindMortarOnce(..., boolean crusherMortar)` | `boolean` | 研钵/碾钵一次性研磨，只在服务端调用 |
| `fluidHandlerAt(Level, BlockPos)` | `@Nullable IFluidHandler` | 方块流体处理器，只读 |
| `injectItems(Level, BlockPos, LivingEntity, ItemStack, @Nullable FilterItemStack)` | `boolean` | 灌注枪注入，只在服务端调用 |
| `storeFluid(Level, BlockPos, LivingEntity, ItemStack)` | `boolean` | 灌注枪存液，只在服务端调用 |
| `extractFluidTick(Level, BlockPos, LivingEntity, ItemStack, int)` | `void` | 灌注枪抽液，只在服务端调用 |
| `findPointerRecipe(Level, ItemStack, ItemStack)` | `@Nullable Recipe<?>` | 指杆应用配方 |
| `isPointerApplicationRecipe(@Nullable Recipe<?>)` | `boolean` | 是否指杆可执行的应用配方 |
| `findStirringRecipe(Level, BasinBlockEntity)` | `@Nullable Recipe<?>` | 搅拌杖混合配方 |
| `bellowsCanProcess(Level, ItemStack, FanProcessingType)` | `boolean` | 风箱介质能否处理某物品 |
| `applyBellows(Level, BlockPos, FanProcessingType)` | `boolean` | 风箱应用鼓风，只在服务端调用 |
| `damageTool(ItemStack, LivingEntity)` | `void` | 扣工具耐久，只在服务端调用 |