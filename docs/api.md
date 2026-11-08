# Create: Hand Made 对外 API

适用版本：**1.20.1 Forge / 0.6.2-beta+**
入口类：`com.alben.createhandmade.api.CreateHandMadeApi`
`API_VERSION = 1`

跨模组请优先绑定 `CreateHandMadeApi`；各工具类的 public 方法不保证跨版本稳定。

---

## 稳定性承诺

- `API_VERSION` 只在**破坏性变更**时递增；新增方法不算破坏性变更。
- 本 API 提供两类能力：
  - **查询 / 读写组件**（23 个方法）—— 只读，不改世界、不消耗材料；
  - **写操作**（8 个方法）—— 会消耗输入、产出、扣耐久、播放特效，**只允许在服务端调用**。
- 写操作由本模组内部同一批实现承担，签名已收敛到 API 层；本模组自己的调用点都在 `if (!level.isClientSide)` 之内，开放后请在调用方保证这一前提。

---

## 方法总览（31 个）

### 配方查询

| 方法 | 返回 | 说明 |
|---|---|---|
| `recipesFor(ToolType tool, Level level)` | `List<Recipe<?>>` | 某工具的全部基础配方（L1 + L3，顺序即游戏内查询优先顺序）。**不含 L2 过滤**；`level == null` 返回空列表。 |
| `recipesForFiltered(ToolType tool, Level level)` | `List<Recipe<?>>` | 同上，但剔除数据包 / KubeJS（L2）禁用的配方。口径与游戏内一致：L1 与 L3 都过 L2。 |
| `findRecipeFor(ToolType tool, Level level, ItemStack input)` | `@Nullable Recipe<?>` | 按输入查该工具走哪条配方，内部已组装好 `ItemStackHandler` / `RecipeWrapper` 并跑完 `matches()`。只覆盖"看一件物品就能判"的工具（手锯切削、手锯切石、冲压锤 Depot、研钵、碾钵），其余返回 `null`。 |
| `isRecipeDisabled(ToolType tool, ResourceLocation recipeId)` | `boolean` | 该配方是否被数据包 / KubeJS 过滤器对本工具禁用（三张表任一命中即 `true`）。 |
| `recipeIdOf(Level level, Recipe<?> recipe)` | `@Nullable ResourceLocation` | 配方 ID 反查（1.20.1 的裸 `Recipe` 没有 `id()`）。带缓存、重载时失效，可放心在循环里调用；动态配方返回 `null`。 |

### 风箱介质

| 方法 | 返回 | 说明 |
|---|---|---|
| `bellowsMediaTypes(ItemStack stack)` | `List<ResourceLocation>` | 该物品能充当哪些风箱处理类型。可能为空列表，不返回 `null`。 |
| `isBellowsMedia(ItemStack stack)` | `boolean` | 该物品是否能作为风箱介质。 |
| `bellowsMediaItems(ResourceLocation typeId)` | `List<ItemStack>` | 某风箱处理类型对应的介质物品列表。 |
| `isBellowsTypeDisabled(ResourceLocation typeId)` | `boolean` | 某风箱处理类型是否被配置禁用。 |

### 灌注枪（组件读写）

| 方法 | 返回 | 说明 |
|---|---|---|
| `infusionGunCapacity()` | `int` | 容量（mB），当前 `1000`。 |
| `infusionGunContents(ItemStack gun)` | `InfusionGunContents` | 读枪内流体。**永不返回 `null`**；空枪返回 `new InfusionGunContents(FluidStack.EMPTY)`。 |
| `setInfusionGunContents(ItemStack gun, InfusionGunContents contents)` | `void` | 写枪内流体。`contents` 为 `null` 或空时移除 NBT（纯空枪不留空组件）。 |
| `fluidSourceAt(Level level, BlockPos pos)` | `FluidStack` | 读世界某位置的流体源，**只读**；非源方块返回 `FluidStack.EMPTY`，量为一个流体方块（1000 mB）。 |

### 研钵 / 碾钵（组件读写）

| 方法 | 返回 | 说明 |
|---|---|---|
| `mortarContents(ItemStack stack)` | `@Nullable MortarContents` | 读研钵内容物；空碗返回 `null`。 |
| `setMortarContents(ItemStack stack, @Nullable MortarContents contents)` | `void` | 写内容物；`null` 表示清空。 |
| `mortarProcessingTicks(boolean crusherMortar)` | `int` | 按配置算加工 tick（不含起手）。`true` 读碾钵段、`false` 读研钵段。取值走 `SyncedServerConfig`，客户端拿到的是服务端同步值。 |

### 各工具查询

| 方法 | 返回 | 说明 |
|---|---|---|
| `handSawRecipes(Level, ItemStack)` | `List<Recipe<?>>` | 手锯在某输入上的切削配方（含切石回退，顺序即游戏内优先顺序）。 |
| `findPressingRecipe(Level, ItemStack)` | `@Nullable Recipe<?>` | 冲压锤（置物台/传送带）压制配方。 |
| `findPointerRecipe(Level, ItemStack target, ItemStack tool)` | `@Nullable Recipe<?>` | 指杆应用配方。 |
| `isPointerApplicationRecipe(@Nullable Recipe<?>)` | `boolean` | 是否指杆可执行的应用配方。 |
| `findStirringRecipe(Level, BasinBlockEntity)` | `@Nullable Recipe<?>` | 搅拌杖混合配方。 |
| `bellowsCanProcess(Level, ItemStack, FanProcessingType)` | `boolean` | 风箱能否以该类型加工该物品（L3 优先、L1 兜底，与游戏内一致）。 |
| `fluidHandlerAt(Level, BlockPos)` | `@Nullable IFluidHandler` | 方块流体处理器；只读查询。 |

### 写操作（只在服务端调用）

| 方法 | 返回 | 副作用 |
|---|---|---|
| `pressBasin(Level, BlockPos, @Nullable FilterItemStack)` | `boolean` | 工作盆冲压：消耗输入、产出、粒子/音效 |
| `pressTransported(Level, BlockPos, @Nullable FilterItemStack)` | `boolean` | 传送带/置物台冲压（batchSize = 1 的重载） |
| `grindMortarOnce(Level, LivingEntity, ItemStack, @Nullable IItemHandler in, @Nullable IItemHandler out, @Nullable FilterItemStack, boolean crusherMortar)` | `boolean` | 研钵/碾钵一次性研磨：消耗输入、产出、扣耐久 |
| `injectItems(Level, BlockPos, LivingEntity, ItemStack, @Nullable FilterItemStack)` | `boolean` | 灌注枪注入：消耗枪内流体、转化传送带物品、扣耐久 |
| `storeFluid(Level, BlockPos, LivingEntity, ItemStack)` | `boolean` | 灌注枪存液进方块、扣耐久 |
| `extractFluidTick(Level, BlockPos, LivingEntity, ItemStack, int rate)` | `void` | 灌注枪抽液；首次成功抽取扣耐久 |
| `applyBellows(Level, BlockPos, FanProcessingType)` | `boolean` | 对目标应用鼓风，跨模组触发上游效果，可能转化物品 |
| `damageTool(ItemStack, LivingEntity)` | `void` | 扣 1 点耐久；实体是车万女仆时按好感度走免耐久（与女仆行为内部一致） |

> 需要 batchSize 版本的冲压或 `HandSawItem#executeCut` / `stripBlock` 这类更细的入口时，只能直接调工具类 public 方法，并自行承担"只在服务端调用""自行扣耐久""自行保证玩家行为约束"。

---

## 调用示例

### 查询某工具的全部可用配方

```java
for (Recipe<?> recipe : CreateHandMadeApi.recipesFor(ToolType.HAND_SAW, level)) {
    ResourceLocation id = CreateHandMadeApi.recipeIdOf(level, recipe);
    if (id != null && CreateHandMadeApi.isRecipeDisabled(ToolType.HAND_SAW, id)) {
        continue; // 被数据包 / KubeJS 禁用
    }
}
```

> 1.20.1 的裸 `Recipe` 没有 `id()`。**不要**在循环里对每条配方都 `getRecipeIds() + byKey()` 反查——那是 O(n²)，`recipeIdOf` 内部建了一张「配方 → ID」表并按 `RecipeManager` 缓存，资源重载时失效。

### 判断物品能否作为风箱介质

```java
ItemStack stack = new ItemStack(Items.POWDER_SNOW_BUCKET);
if (CreateHandMadeApi.isBellowsMedia(stack)) {
    List<ResourceLocation> types = CreateHandMadeApi.bellowsMediaTypes(stack);
    // 例如 [create_dragons_plus:freezing]
}
```

### 读 / 写灌注枪内容物

```java
InfusionGunContents contents = CreateHandMadeApi.infusionGunContents(gun);
if (!contents.isEmpty()) {
    FluidStack fluid = contents.fluid();
    int amount = fluid.getAmount();
    int remaining = contents.remaining();
}

CreateHandMadeApi.setInfusionGunContents(gun, null);                        // 清空
CreateHandMadeApi.setInfusionGunContents(gun,
        new InfusionGunContents(new FluidStack(Fluids.WATER, 500)));       // 写入
```

### 读研钵内容物

```java
MortarContents contents = CreateHandMadeApi.mortarContents(mortar);
if (contents != null) {
    ItemStack input = contents.stack();
    int batchCount = input.getCount();
    int ticks = CreateHandMadeApi.mortarProcessingTicks(false); // 研钵
}
```

---

## 1.20.1 与 1.21 的差异

跨版本移植时注意（"1.21" 列来自 1.21 分支，本仓库不分叉验证）：

| 项 | 1.21 NeoForge | 1.20.1 Forge |
|---|---|---|
| 工具枚举 | `HandMadeTool`（12 值） | `ToolType`（10 值） |
| `recipesFor` 返回 | `List<RecipeHolder<?>>` | `List<Recipe<?>>`，没有 `RecipeHolder`，不要 `.value()` |
| 配方 ID | `RecipeHolder.id()` | 裸 `Recipe` 无 `id()`，用 `HandMadeRecipePool.recipeIdOf` 反查 |
| `mortarProcessingTicks` | `(MortarContents, boolean)` | `(boolean)`，不看内容物 |
| `FluidStack.matches` | 存在 | 不存在，手写 `getFluid()` / `getAmount()` / `getTag()` 比较 |
| `ItemStack.isSameItemSameComponents` | 1.20.5+ | 1.20.1 用 `isSameItemSameTags` |
| 内容物存储 | `DataComponentType` | `ItemStack` NBT（`MortarContents` / `InfusionGunContents`） |
| 三个 AUTO 类别 | 独立 `HandMadeTool` 值，`getBaseRecipes` 能查到 | 走 L2 过滤，`getBaseRecipes` 不返回，JEI 各类别单独收集 |

### 签名差异补遗

| 1.21 | 1.20.1 | 处理 |
|---|---|---|
| `tryPressBasin(Level, BlockPos)` | `tryPressBasin(Level, BlockPos, @Nullable FilterItemStack)` | 1.20.1 多一个过滤器参数 |
| `tryPressTransported(Level, BlockPos)` | `tryPressTransported(Level, BlockPos, @Nullable FilterItemStack)` + `(..., int batchSize)` | 1.20.1 多过滤器参数 + 批量重载 |
| `onCriticalHit(CriticalHitEvent)` | `onLivingHurt(LivingHurtEvent)` | 事件类型与方法名都不同；**不要为了对齐改名**，改了会破坏事件注册 |

---

## 已知限制

1. `recipesFor` 的 L3 在 switch 之后统一收：不要在 switch 里给某个工具单独加 `TOOL_RECIPE` 查询，会重复。
2. 碾钵接受研钵的 L3：`CRUSHER_MORTAR` 会同时收 `ToolType.MORTAR` 的独占配方，这是设计行为。
3. `mortarProcessingTicks(boolean)` 没有三态：研钵与碾钵各有独立配置段（`[tools.mortar]` / `[tools.crusher_mortar]`）。
4. `fluidSourceAt` 只读：不消耗流体、不改方块状态。要实际抽取用 `extractFluidTick`（有副作用）。
5. `infusionGunContents` 永不返回 `null`：空枪返回 `new InfusionGunContents(FluidStack.EMPTY)`，判空用 `contents.isEmpty()`。
6. `mortarContents` **可能**返回 `null`（空碗），与 5 的语义不同；判空用 `contents == null || contents.stack().isEmpty()`。
7. `findRecipeFor` 对工作盆 / 搅拌杖 / 灌注枪 / 指杆 / 风箱返回 `null`——它们光看一件物品判不出配方，请用对应的专门入口。
8. `recipesFor` 不含 L2，`recipesForFiltered` 含；游戏内工具查询与 JEI 分类页都按 L2 过滤，三方口径一致。风箱没有 L2（无 L1 候选，靠 `fan_type` 匹配）。
