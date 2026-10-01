# Create: Hand Made 配方 API

> **配套文档**：[工具速查表](TOOL_REFERENCE.md)

面向魔改包作者。本文只讲「怎么改手搓工具能读到的配方」，不涉及工具本身的合成表。

模组 id：`create_hand_made`

---

## 概览（三层模型）

本模组的配方读取分三层，**叠加顺序**是：L1 收集 → L3 追加 → L2 过滤。

| 层 | 作用 | 作者能做什么 | 影响范围 |
| --- | --- | --- | --- |
| **L1 继承层** | 工具的默认候选集就是 Create 的某几个 `RecipeType` | 增删改 Create 的配方 | 工具与 Create 机器**同时**受影响 |
| **L2 过滤层** | 把配方从**某个工具**的候选集里剔除 | 精确 id 或按 mod namespace 禁用 | 只影响手搓工具，Create 机器照常可用 |
| **L3 独占层** | 新增一条**只有手搓工具读得到**的配方 | 写 `create_hand_made:tool_recipe`（工具）或 `create_hand_made:bellows_recipe`（风箱）配方 | Create 机器**看不到**，只有指定的手搓工具能用 |

`tool_recipe` 的三层实现都在 `com.alben.createhandmade.recipe.HandMadeRecipePool`：
它先把 L1 的 Create 配方收进候选列表，再把 L3 独占配方追加进去，最后统一执行 L2 过滤。
因此 **L2 对 L1 和 L3 一视同仁**（见「L3 · 与 L2 的叠加」）。

`bellows_recipe`（风箱）**不走这个池**：它在 `BellowsItem` 里直接查询，顺序也是反的（L3 先、L1 后），
并且**不受 L2 管辖** —— 详见「风箱独占配方」一节。

JEI 的分类显示的是同一份候选集，所以上面三层的结果在 JEI 里同样可见。

> **L3 分两种**：`create_hand_made:tool_recipe`（**8 个 id / 7 个工具** —— `press_hammer_basin` 与 `press_hammer_depot`
> 是同一个物品"冲压锤"的两个使用场景）与 `create_hand_made:bellows_recipe`（**风箱**，独立 RecipeType、独立字段 `fan_type`）。
> 两种 L3 的**匹配顺序相反**，见「两种 L3 的匹配顺序（重要）」。

---

## L1 · 默认继承 Create 配方

工具默认读取的 `RecipeType` 由 `HandMadeTool` 枚举与 `HandMadeRecipePool` 里对应的收集方法决定：

| tool_id | 工具                        | 读取的 RecipeType |
| --- |-----------------------------| --- |
| `press_hammer_basin` | 冲压锤 · 工作盆             | `create:compacting` |
| `press_hammer_depot` | 冲压锤 · 置物台 / 传送带    | `create:pressing` |
| `press_hammer_auto_square` | 冲压锤 · 工作盆（4/9 合 1） | `minecraft:crafting`（可压缩的工作台配方） |
| `mortar` | 研钵                        | `create:milling` |
| `crusher_mortar` | 碾钵                        | `create:crushing`，匹配不到再兜底 `create:milling` |
| `hand_saw` | 手锯                        | `create:cutting` |
| `stirring_staff` | 搅拌杖 · 工作盆             | `create:mixing` |
| `stirring_staff_auto_shapeless` | 搅拌杖 · 自动无序合成       | `minecraft:crafting`（无序、多原料、非 shaped、不可压缩） |
| `stirring_staff_auto_brewing` | 搅拌杖 · 自动酿造           | 运行时由 `PotionMixingRecipes` 生成，不是数据包配方 |
| `pointer` | 指杆                        | `create:deploying`，再兜底 `create:item_application` |
| `infusion_gun` | 灌注枪                      | `create:filling` |

风箱（`BellowsItem`）没有 `tool_id`，不在上表内：它的 L1 走 Create 的 `FanProcessingType` / `AllFanProcessingTypes`
（鼓风熔炼 / 烟熏 / 缠魂 / 洗涤，底层读取的是 `minecraft:smelting` / `minecraft:blasting` / `minecraft:smoking`
与 `create:haunting` / `create:splashing`），L3 走自己的 `create_hand_made:bellows_recipe`（见「风箱独占配方」），
并且**不受 L2 管辖**。

**推论：删掉一条 Create 配方，对应工具就跟着失去它。** 例如移除 `create:milling/charcoal`，研钵也碾不出木炭了，这是 L1 的预期行为。若只想让工具失去、保留 Create 机器，用 L2。

---

## L2 · 过滤层

只做「配方集合过滤」，**不参与配方匹配**，也**不会**从 `RecipeManager` 里删掉任何东西 —— 被禁用的配方仍然完整存在，Create 的机器照常使用。

### 数据包方式

- 目录：`data/<你的命名空间>/create_hand_made/tool_filter/<tool_id>.json`
- **文件名就是 `tool_id`**（见文末清单），命名空间可以是任意包名
- 字段（都可不写）：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `disabled` | 字符串数组 | 精确匹配 recipe id |
| `disabled_by_mod` | 字符串数组 | 匹配 recipe id 的 namespace（整个 mod 的配方） |

完整示例，`data/mypack/create_hand_made/tool_filter/mortar.json`：

```json
{
  "disabled": [
    "create:milling/charcoal",
    "create:milling/lapis_lazuli"
  ],
  "disabled_by_mod": [
    "thermal"
  ]
}
```

含义：研钵不再读取「木炭」「青金石」两条研磨配方，并且不再读取 `thermal` 命名空间下的任何研磨配方。

解析失败的行为：文件名不是合法 `tool_id` → 记一条 warning 并跳过该文件；JSON 不是对象、字段类型不对、recipe id 写错 → 记 warning 并跳过对应条目。整个数据包重载不会因此失败。

### KubeJS 方式

写在 **server script** 里（例如 `kubejs/server_scripts/xxx.js`）：

```js
HandMadeEvents.toolFilter(event => {
    // 精确禁用：工具 id + 配方 id
    event.disable('mortar', 'create:milling/charcoal')
    event.disable('mortar', 'create:milling/lapis_lazuli')

    // 按 mod 整片禁用：工具 id + 命名空间
    event.disableByMod('stirring_staff', 'thermal')
})
```

- `disable(toolId, recipeId)` / `disableByMod(toolId, modId)`
- 工具 id 写错会**立刻抛异常**（`IllegalArgumentException`，异常信息里会列出全部合法 id），不会静默跳过 —— 这是脚本作者笔误，应当马上看见。
- 数据包与脚本是**两张独立的表**，查询时取并集；它们各自在自己的重载时机清空重建，互不干扰。

**注意：每次脚本重载都会重放回调。** 模组在 `afterScriptsLoaded` 时先清空脚本来源的过滤表，再重新执行所有 `toolFilter` 回调。所以回调应当是**纯登记**（只调用 `disable` / `disableByMod`），不要在回调里做有副作用的操作（计数、写文件、发消息等），否则每次 `/reload` 都会重做一遍。

> **L2 对风箱无效**：风箱的加工路径不经过配方池，而 L2 的两套 API 都以 `tool_id` 为键（`HandMadeTool` 枚举里没有风箱）。
> 想禁用风箱的鼓风配方只能从 `RecipeManager` 层面移除（会连带 Create 鼓风机），或者用 `bellows_recipe` 单独控制。

---

## L3 · 独占层

独占配方活在一个**独立的自定义 `RecipeType`** 下：

```
create_hand_made:tool_recipe
```

Create 的机器查询的是 `create:compacting` / `create:mixing` 等自己的类型，因此**永远看不到**这些配方；只有 `tool` 字段指定的那个手搓工具（以及对应的 JEI 类别）能读到。

### 覆盖范围（重要）

| | recipe id（= 工具 id） |
| --- | --- |
| ✅ 支持 | `press_hammer_basin`、`press_hammer_depot`、`stirring_staff`、`mortar`、`crusher_mortar`、`hand_saw`、`infusion_gun`、`pointer` —— 共 **8 个 id / 7 个工具**（`press_hammer_basin` 与 `press_hammer_depot` 属同一个物品：冲压锤） |
| ❌ 不涉及 | 风箱（它有自己的 `create_hand_made:bellows_recipe`，见下一节）；`press_hammer_auto_square`、`stirring_staff_auto_shapeless`（这两类源自 `minecraft:crafting`）；`stirring_staff_auto_brewing`（运行时生成） |

原因：

- **支持的那 8 个 id（7 个工具）**各有一个继承 Create 对应配方类的 L3 配方类，并用接受自定义 `IRecipeTypeInfo` 的构造把自己挂到 `create_hand_made:tool_recipe` 上：`HandMadeToolRecipe extends BasinRecipe`、`HandMadeCrushingRecipe extends AbstractCrushingRecipe`、`HandMadePressingRecipe` / `HandMadeCuttingRecipe` / `HandMadeFillingRecipe` / `HandMadeApplicationRecipe extends StandardProcessingRecipe`。Create 的 `BasinRecipe` 只把该构造留成 `protected`（子类可用），其余家族在 `StandardProcessingRecipe` / `AbstractCrushingRecipe` 上是 `public`；配方类型由本模组自己的 `IRecipeTypeInfo` 提供，所以 `getType()` 返回的是 `create_hand_made:tool_recipe`。
- **风箱不涉及 `tool_recipe`**：它没有 `tool_id`，独占配方走自己的 `create_hand_made:bellows_recipe`（`fan_type` 字段 + 副手介质匹配，见下一节）。
- **3 个 auto 类别不涉及**：`press_hammer_auto_square` 与 `stirring_staff_auto_shapeless` 是从 `minecraft:crafting` 派生的展示类别（原料来自普通工作台配方），`stirring_staff_auto_brewing` 由代码在运行时生成 —— 三者都不是数据包里的处理配方，所以没有"独占配方"可言。

### 数据包方式

- 目录：`data/<你的命名空间>/recipe/<任意文件名>.json`（与普通配方同目录）
- 完整示例，`data/mypack/recipe/handmade_diamond.json`：

```json
{
  "type": "create_hand_made:tool_recipe",
  "tool": "press_hammer_basin",
  "ingredients": [
    { "item": "minecraft:gold_ingot" },
    { "item": "minecraft:gold_ingot" }
  ],
  "results": [
    { "id": "minecraft:diamond", "count": 1 }
  ],
  "processing_time": 60,
  "heat_requirement": "heated"
}
```

> 示例里的配方内容只是占位，直接粘贴会得到一条「2 金锭 → 1 钻石」的配方 —— 请替换成你要的内容。

字段：

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `type` | ✅ | 字符串 | 固定 `create_hand_made:tool_recipe` |
| `tool` | ✅ | 字符串 | 归属工具，8 个支持 L3 的 id（7 个工具）之一（见上面的「覆盖范围」与「工具 ID 清单」） |
| `ingredients` | ✅ | 数组 | 原料，物品 `{ "item": ... }` / `{ "tag": ... }`，或流体 `{ "type": "neoforge:single", "amount": ..., "fluid": ... }` |
| `results` | ✅ | 数组 | 产物，物品 `{ "id": ..., "count": ... }` 或流体 `{ "id": ..., "amount": ... }` |
| `processing_time` | ❌ | 整数 | 处理耗时（tick），默认 `0`。**只有 basin / crushing / cutting 三个家族支持**（见下表） |
| `heat_requirement` | ❌ | 字符串 | `none`（默认）/ `heated` / `superheated`。**只有 basin 家族支持** |

**限制：**

- `tool` 写错工具 id，或写了不支持的工具：配方在加载时被拒绝，日志里会出现一条 `Parsing error loading recipe <你的配方 id>` 并附上原因（`Unknown tool id: ...` / `Tool '...' does not support exclusive recipes yet.`）。
- 该类型没有任何内置配方 —— 是否使用 L3 完全由整合包决定。

#### 字段 × 家族矩阵（重要）

不同家族支持的可选字段不一样 —— **写了不支持的字段会在加载期被拒**：

| 家族 | 支持的工具 id | `processing_time` | `heat_requirement` | 最大物品输入 | 最大物品输出 | 流体输入 / 输出 |
| --- | --- | --- | --- | --- | --- | --- |
| **basin** | `press_hammer_basin`、`stirring_staff` | ✅ | ✅ | 64 | 4 | 2 / 2 |
| **crushing** | `mortar`、`crusher_mortar` | ✅ | ❌ | 1 | 7 | 0 / 0 |
| **pressing** | `press_hammer_depot` | ❌ | ❌ | 1 | 2 | 0 / 0 |
| **cutting** | `hand_saw` | ✅ | ❌ | 1 | 4 | 0 / 0 |
| **filling** | `infusion_gun` | ❌ | ❌ | 1 | 1 | **1** / 0 |
| **application** | `pointer` | ❌ | ❌ | 2 | 4 | 0 / 0 |

- 每一格的来源：`processing_time` / `heat_requirement` 看家族类的 `canSpecifyDuration()` / `canRequireHeat()`，数量看 `getMaxInputCount()` / `getMaxOutputCount()` / `getMaxFluidInputCount()` / `getMaxFluidOutputCount()`。
  - basin 家族继承 Create 的 `BasinRecipe`（`BasinRecipe.java:196-222`：输入 64 / 输出 4 / 流体 2+2 / 允许时长 / 允许热量）。
  - crushing 家族继承 `AbstractCrushingRecipe`（输入 1、允许时长、不允许热量）+ 本模组的 `HandMadeCrushingRecipe.MAX_OUTPUT_COUNT = 7`（`HandMadeCrushingRecipe.java:42`）。
  - 其余四个家族的值直接写在自己类里：`HandMadePressingRecipe:44-60`、`HandMadeCuttingRecipe:45-57`、`HandMadeFillingRecipe:60-82`、`HandMadeApplicationRecipe:58-74`。
- **`❌` 的字段写了就会在加载期被拒**，报错文本：
  - `Recipe specified a duration. Durations have no impact on this type of recipe.`
  - `Recipe specified a heat condition. Heat conditions have no impact on this type of recipe.`
- **filling 家族必须带 1 个流体原料**，否则加载期被拒：
  `Exclusive recipe for tool 'infusion_gun' must declare at least one fluid ingredient (...)`。
- **模组不做下限检查**：`ingredients` 写 0 个也能加载成功，但这条配方永远匹配不上 —— 请至少写 1 个物品或流体原料。

### KubeJS 方式

模组自带 KubeJS recipe schema（`data/create_hand_made/kubejs/recipe_schema/tool_recipe.json`），所以可以直接用配方类型函数创建。写在 **server script** 里：

```js
ServerEvents.recipes(event => {
    // 冲压锤 · 工作盆：2 金锭 → 1 钻石，需要加热
    event.recipes.create_hand_made.tool_recipe(
        'press_hammer_basin',
        ['1x minecraft:diamond'],                         // results：支持 'Nx item'
        ['minecraft:gold_ingot', 'minecraft:gold_ingot']  // ingredients：写纯 id
    ).heated()

    // 搅拌杖：3 铁矿 → 1 绿宝石，120 tick，超热
    // results 也可以只给一个值，不必包成数组
    event.recipes.create_hand_made.tool_recipe(
        'stirring_staff',
        '1x minecraft:emerald',
        ['minecraft:iron_ingot', 'minecraft:iron_ingot', 'minecraft:iron_ingot']
    ).processingTime(120).superheated()

    // 流体原料 + 物品原料混用：1 金锭 + 100 mB 水 → 1 钻石
    event.recipes.create_hand_made.tool_recipe(
        'stirring_staff',
        ['1x minecraft:diamond'],
        [Fluid.of('minecraft:water', 100), 'minecraft:gold_ingot']
    )
})
```

写法要点：

- 位置参数顺序是 `(tool, results, ingredients)`。
- `ingredients` 用 `either` 组件（`ingredient` / `flat_sized_fluid_ingredient` 二选一）：
  - 物品写**纯 id**（`'minecraft:iron_ingot'`）或 **tag**（`'#create:pulpifiable'`），**不能**写 `'2x ...'` 计数简写；要两份就重复写两个元素（和 Create 自己的配方文件一致）。
  - 流体写 `Fluid.of('minecraft:water', 100)`，可以和物品混在同一个数组里。
    `Fluid` 是 **KubeJS 的全局 API**（脚本里直接可用，不需要 `Java.loadClass` 或任何 import）。
- `results` 用 `item_stack` / `fluid_stack` 组件：可以写 `'2x minecraft:gold_ingot'` 计数简写。
- 链式函数：`.heated()`、`.superheated()`、`.processingTime(n)`，也可以直接写 `heat_requirement` 键。
  注意 `.processingTime(n)` 只对 basin / crushing / cutting 有效（见上面的矩阵），其它家族写了会在加载期报错。

### 与 L2 的叠加

独占配方进入候选集的**最后一步**才是 L2 过滤，所以 L2 的两套 API 对它同样有效：

```js
HandMadeEvents.toolFilter(event => {
    // 禁用某个工具下的一条独占配方
    event.disable('press_hammer_basin', 'mypack:handmade_diamond')
    // 按命名空间整片禁用（会连带该命名空间下的独占配方）
    event.disableByMod('stirring_staff', 'mypack')
})
```

数据包侧的 `tool_filter/<tool_id>.json` 同理，`disabled` 里直接写独占配方的 id 即可。

### 两种 L3 的匹配顺序（重要）

`tool_recipe` 与 `bellows_recipe` 的匹配顺序是**相反**的：

| | `tool_recipe`（8 个 id / 7 个工具） | `bellows_recipe`（风箱） |
| --- | --- | --- |
| 走哪条路 | `HandMadeRecipePool.getBaseRecipes(...)` | `BellowsItem.applyToTarget` 里的显式查询 |
| 候选集顺序 | **L1 在前、L3 在后**（池先收 Create 候选、再追加独占配方） | **L3 在前**：先查独占配方，未命中才回落 L1 |
| 谁优先 | **L1 优先** | **L3 优先** |
| 想让 L3 生效要做什么 | 必须先用 L2 把 L1 那条让开（见下） | 不需要任何操作，L3 直接生效 |

为什么不同：`tool_recipe` 复用配方池的"收集 + 追加"流程（顺序由池决定，且 L2 要能过滤整份列表）；
`bellows_recipe` 不在池里，是 `BellowsItem` 自己写的两段式查询（`BellowsItem.java:474-500`）。

#### `tool_recipe`：L1 优先

工具匹配配方时，候选集的顺序是 **Create 候选（L1）在前、L3 独占配方在后**，而工具取**第一个匹配**的。
也就是说：

> **如果某个输入已经有匹配的 Create 配方，你的独占配方在手搓工具里永远不会被选中。**

这是设计使然 —— 同一个输入不应该有两条都匹配的配方（那属于配方设计错误），
优先级规则只是为了给出确定的行为。

想让独占配方真正生效，作者要自己把 L1 那条让开：

1. **用 L2 禁用对应的 Create 配方**：
   ```js
   HandMadeEvents.toolFilter(event => {
       event.disable('press_hammer_depot', 'create:pressing/iron_ingot')
   })
   ```
   （数据包方式同理：在 `tool_filter/press_hammer_depot.json` 的 `disabled` 里写 `create:pressing/iron_ingot`）
2. **再用 L3 添加独占配方**（或直接用 Create 那条改名后的变体）。

反过来，如果某个输入 Create 根本没有配方（例如给置物台喂一个苹果），L3 独占配方会直接生效，
不需要任何额外操作。

> 提示：选测试/示例配方时，最好挑一个 Create 没有对应配方的输入，
> 这样不必依赖 L2 禁用就能看到 L3 的效果。

> 对比：**风箱的 `bellows_recipe` 不需要这一步** —— 它是 L3 优先，独占配方直接生效。

---

## 风箱独占配方（`create_hand_made:bellows_recipe`）

风箱（Bellows）**不走** `tool_recipe`，而是自己的 RecipeType：

```
create_hand_made:bellows_recipe
```

两个原因：风箱不属于 `HandMadeTool` 体系（没有 tool_id），字段集也不同（`fan_type` 必填、禁止流体）。
Create 的鼓风机查的是 `create:blasting` / `create:smoking` / `create:haunting` / `create:splashing`，
永远看不到这个类型 —— 与 L3 一样是"手搓独占"。

> **状态：运行时已接通。** `BellowsItem.applyToTarget` 会**先查本类型的独占配方**
> （`fan_type` 与副手介质的鼓风类型匹配），未命中才回落到 Create 的鼓风逻辑（L1）。
> 命中 L3 后**介质不消耗**（与 L1 一致），风箱自身仍扣 1 点耐久。

### 字段

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `type` | ✅ | 字符串 | 固定 `create_hand_made:bellows_recipe` |
| `fan_type` | ✅ | 字符串 | `blasting` / `smoking` / `haunting` / `splashing` 之一 |
| `ingredients` | ✅ | 数组 | **恰好 1 个物品**（`{ "item": ... }` 或 `{ "tag": ... }`）；**不支持流体** |
| `results` | ✅ | 数组 | 1–12 个物品：`{ "id": ..., "count": ..., "chance": ... }`（`count` 缺省 1、`chance` 缺省 1.0） |

**限制（写错会在**加载期**被拒，日志里是 `Parsing error loading recipe <id>` + 原因）：**

- ❌ `processing_time` —— 风箱是**瞬发**的（松手一次结算，没有 tick 累计）。
  报错：`Recipe specified a duration. Durations have no impact on this type of recipe.`
- ❌ `heat_requirement` —— 风箱没有热源概念。
  报错：`Recipe specified a heat condition. Heat conditions have no impact on this type of recipe.`
- ❌ 流体原料 —— 风箱的"介质"是**副手物品**，由 `bellows_media` / 触媒标签决定，不在配方里。
  报错：`Recipe has more fluid inputs (1) than supported (0).`
- ❌ 多于 1 个物品原料。报错：`Recipe has more item inputs (2) than supported (1).`
- ⚠️ **独占配方不受 L2 过滤管辖**（设计决定，不是临时状态）：风箱的加工路径不经过配方池，而 `HandMadeRecipeFilters` 以 `HandMadeTool` 为键、风箱不在该枚举里，所以 L2 的两套 API 对风箱**永远无效**。
  想禁用风箱的鼓风配方，只能从 `RecipeManager` 层面移除那条 Create 配方（会**连带 Create 鼓风机**），或者用本类型的独占配方独立控制。

### `fan_type` 的 4 个取值

| `fan_type` | 含义 | 需要的副手介质（触媒） | 对应的 Create 鼓风类型 |
| --- | --- | --- | --- |
| `blasting` | 鼓风熔炼 | 岩浆类（岩浆桶、岩浆块…） | `create:blasting` |
| `smoking` | 鼓风烟熏 | 营火类 | `create:smoking` |
| `haunting` | 鼓风缠魂 | 灵魂营火类 | `create:haunting` |
| `splashing` | 鼓风洗涤 | 水类 | `create:splashing` |

> 介质清单不是写死的：它与风箱的介质表（`bellows_media` 数据包 + Create 的触媒标签自动发现）一致，
> 与现有 4 个风箱 JEI 类别里显示的介质相同。

### 数据包方式

目录：`data/<你的命名空间>/recipe/<任意文件名>.json`

最简形式：

```json
{
  "type": "create_hand_made:bellows_recipe",
  "fan_type": "blasting",
  "ingredients": [
    { "item": "minecraft:iron_ingot" }
  ],
  "results": [
    { "id": "minecraft:gold_ingot", "count": 1 }
  ]
}
```

带概率的多产物（`chance` 是**每个产物各自独立**掷的，与 Create 的其它处理配方一致）：

```json
{
  "type": "create_hand_made:bellows_recipe",
  "fan_type": "haunting",
  "ingredients": [
    { "tag": "minecraft:sand" }
  ],
  "results": [
    { "id": "minecraft:clay_ball", "count": 1 },
    { "id": "minecraft:gold_nugget", "count": 1, "chance": 0.25 }
  ]
}
```

### KubeJS 方式

模组自带 schema（`data/create_hand_made/kubejs/recipe_schema/bellows_recipe.json`）：

```js
ServerEvents.recipes(event => {
    // 位置参数顺序：(fan_type, results, ingredients)
    event.recipes.create_hand_made.bellows_recipe(
        'blasting',
        ['1x minecraft:gold_ingot'],
        ['minecraft:iron_ingot']
    )
})
```

- 与 `tool_recipe` 一致：`ingredients` 写纯 id（`'minecraft:iron_ingot'`）或 tag（`'#minecraft:sand'`），
  **不能**写 `'2x ...'`；`results` 支持 `'Nx item'` 简写。
- ⚠️ **KubeJS 目前写不了产物概率**：KubeJS 的 `item_stack` 组件产出的 JSON 里没有 `chance`
  （实测：`{ id: 'minecraft:gold_nugget', count: 1, chance: 0.25 }` 进去，配方里读回来的 `chance` 仍是 `1.0`）。
  要概率请用上面的**数据包 JSON**。（后续可以给本模组的 KubeJS 插件注册一个带 `chance` 的组件来补上；
  届时 `tool_recipe` 也能一起受益。）

### JEI 里怎么看

独占配方会**混进现有的 4 个风箱类别**（鼓风熔炼 / 烟熏 / 缠魂 / 洗涤）里，与 L1 配方并列显示，
**没有**单独的"风箱独占"类别。

- `fan_type: haunting / splashing`：多产物与概率都会显示（类别遍历 `getRollableResults()`，每个产物带概率 tooltip）。
- `fan_type: blasting / smoking`：类别用的是 `AbstractCookingRecipe`（只有**一个**产物槽），所以 L3 配方写了多产物/概率时，
  JEI 里**只显示第一个产物**（游戏内结算仍然按配方完整执行）。

---

## 工具 ID 清单

`tool_id` 就是 `HandMadeTool` 枚举常量名的小写下划线形式（`PRESS_HAMMER_BASIN` → `press_hammer_basin`）。用于 L2 的文件名/参数，以及 L3 的 `tool` 字段。

| tool_id | 说明 | L3 |
| --- | --- | --- |
| `press_hammer_basin` | 冲压锤 · 工作盆（压缩） | ✅ |
| `press_hammer_auto_square` | 冲压锤 · 工作盆（自动摆放） | ❌ |
| `press_hammer_depot` | 冲压锤 · 置物台 / 传送带 | ✅ |
| `mortar` | 研钵 · 研磨 | ✅ |
| `crusher_mortar` | 碾钵 · 粉碎 + 研磨 | ✅ |
| `hand_saw` | 手锯 · 切削 | ✅ |
| `stirring_staff` | 搅拌杖 · 混合 | ✅ |
| `stirring_staff_auto_shapeless` | 搅拌杖 · 自动无序合成 | ❌ |
| `stirring_staff_auto_brewing` | 搅拌杖 · 自动酿造 | ❌ |
| `pointer` | 指杆 · 应用 | ✅ |
| `infusion_gun` | 灌注枪 · 注液 | ✅ |

> 上表共 11 个 tool_id，其中 **8 个支持 `tool_recipe` 独占配方（对应 7 个工具物品）**：
> `press_hammer_basin` 与 `press_hammer_depot` 是同一个物品"冲压锤"的两个场景。
> 风箱（鼓风熔炼 / 烟熏 / 缠魂 / 洗涤）**不在本表内** —— 它没有 tool_id，独占配方走 `create_hand_made:bellows_recipe`（`fan_type` 字段），
> L2 的 `tool_filter/<tool_id>.json` 也对它无效。

---

## 常见问题

### 为什么我的独占配方没生效？

按顺序检查：

1. **`tool` 字段**是不是合法 id（上面的清单）。写错会在日志里看到 `Unknown tool id: '<你写的>'`。
2. **工具是否在覆盖范围内** —— 只有上面「覆盖范围」里那 8 个 id（7 个工具）支持 `tool_recipe` 独占配方（`press_hammer_basin`、`press_hammer_depot`、`stirring_staff`、`mortar`、`crusher_mortar`、`hand_saw`、`infusion_gun`、`pointer`），其它 id（含 3 个 auto 类别）写了会被拒绝，日志里是 `Tool '<id>' does not support exclusive recipes yet.`。风箱不用 `tool` 字段 —— 它写 `fan_type`（见「风箱独占配方」）。
3. **字段与家族是否匹配** —— 例如给 `pointer` 写 `processing_time` 会在加载期被拒（见「字段 × 家族矩阵」）。
4. **是不是被 L1 抢先匹配了** —— `tool_recipe` 是 **L1 优先**：同一输入如果 Create 已有配方，你的独占配方永远不会被选中，必须先用 L2 让开（见「两种 L3 的匹配顺序」）。（风箱相反：它是 L3 优先，不需要让开。）
5. **是否被 L2 过滤掉了** —— 检查 `tool_filter/<tool_id>.json` 的 `disabled` / `disabled_by_mod`，以及 KubeJS 脚本里的 `toolFilter` 回调。`disabled_by_mod` 里如果写了你自己放独占配方的命名空间，会把它一起禁掉。
6. **日志里有没有 `Parsing error loading recipe <id>`** —— 字段缺漏（比如没写 `tool` / `fan_type`）会在加载时被拒绝：`Missing required field 'tool' for create_hand_made:tool_recipe`。
7. **配方是否真的在数据包里** —— 目录必须是 `data/<ns>/recipe/`，文件名随意。

### 流体原料怎么写？

**数据包与 KubeJS 都可以。** 两种写法最终等价（脚本写出来的东西由模组自己归一化成下面的形状）。

数据包写法（KubeJS 也可以直接这样写，只是更啰嗦）：

```json
{
  "type": "create_hand_made:tool_recipe",
  "tool": "stirring_staff",
  "ingredients": [
    { "item": "minecraft:gold_ingot" },
    { "type": "neoforge:single", "amount": 100, "fluid": "minecraft:water" }
  ],
  "results": [
    { "id": "minecraft:diamond" }
  ]
}
```

KubeJS 写法：

```js
event.recipes.create_hand_made.tool_recipe(
    'stirring_staff',
    ['1x minecraft:diamond'],
    [Fluid.of('minecraft:water', 100), 'minecraft:gold_ingot']
)
```

（`Fluid` 是 KubeJS 的全局 API，脚本里直接可用，不需要 import。）

数据包里流体原料必须是 `{"type": "neoforge:single", "amount": ..., "fluid": ...}` 这个形状（顶层 `type` 是 Create 要求的）；
KubeJS 的 `flat_sized_fluid_ingredient` 组件写出的是 NeoForge 的
`{"fluid": ..., "amount": ...}`（**没有顶层 `type`**），模组的 serializer 会在解析前自动补上
`"type": "neoforge:single"`，所以两种写法都能用。用 tag 选流体则在数据包里写
`{"type": "neoforge:tag", "amount": 250, "tag": "c:milk"}`。

> 注意：只有 **basin 家族**（`press_hammer_basin` / `stirring_staff`）和 **filling 家族**（`infusion_gun`）能用流体原料；
> 其余家族的流体输入上限是 0，写了会在加载期被拒（见「字段 × 家族矩阵」）。风箱的 `bellows_recipe` 完全不支持流体。

### `disabled_by_mod` 会不会误伤独占配方？

**会**，它按 recipe id 的 **namespace** 匹配，不看配方类型。所以：

- 如果你的独占配方放在 `data/mypack/recipe/`（id 形如 `mypack:xxx`），而你在某个工具的 `disabled_by_mod` 里写了 `mypack`，那这条独占配方会被一起禁用。
- 反过来，`disabled_by_mod` 里写 `create`，不会影响你放在自己命名空间下的独占配方。

要精确控制就用 `disabled` 写完整 id。
