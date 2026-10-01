# Create: Hand Made 配方 API

面向魔改包作者。本文只讲「怎么改本模组工具能读到的配方」，不涉及工具本身的合成表。

模组 id：`create_hand_made`

---

## 概览（三层模型）

本模组的配方读取分三层，**叠加顺序**是：L1 收集 → L3 追加 → L2 过滤。

| 层 | 作用 | 作者能做什么 | 影响范围 |
| --- | --- | --- | --- |
| **L1 继承层** | 工具的默认候选集就是 Create 的某几个 `RecipeType` | 增删改 Create 的配方 | 工具与 Create 机器**同时**受影响 |
| **L2 过滤层** | 把配方从**某个工具**的候选集里剔除 | 精确 id 或按 mod namespace 禁用 | 只影响本模组工具，Create 机器照常可用 |
| **L3 独占层** | 新增一条**只有本模组工具读得到**的配方 | 写 `create_hand_made:tool_recipe` 配方 | Create 机器**看不到**，只有指定的手工工具能用 |

三层的实现都在 `com.alben.createhandmade.recipe.HandMadeRecipePool`：
它先把 L1 的 Create 配方收进候选列表，再把 L3 独占配方追加进去，最后统一执行 L2 过滤。
因此 **L2 对 L1 和 L3 一视同仁**（见「L3 · 与 L2 的叠加」）。

JEI 的分类显示的是同一份候选集，所以上面三层的结果在 JEI 里同样可见。

---

## L1 · 默认继承 Create 配方

工具默认读取的 `RecipeType` 由 `HandMadeTool` 枚举与 `HandMadeRecipePool` 里对应的收集方法决定：

| tool_id | 工具 | 读取的 RecipeType |
| --- | --- | --- |
| `press_hammer_basin` | 冲压锤 · 工作盆 | `create:compacting` |
| `press_hammer_depot` | 冲压锤 · 置物台 / 传送带 | `create:pressing` |
| `press_hammer_auto_square` | 冲压锤 · 工作盆（4/9 合 1 自动摆放） | `minecraft:crafting`（可压缩的工作台配方） |
| `mortar` | 研钵 | `create:milling` |
| `crusher_mortar` | 碾钵 | `create:crushing`，匹配不到再兜底 `create:milling` |
| `hand_saw` | 手锯 | `create:cutting` |
| `stirring_staff` | 搅拌杖 · 工作盆 | `create:mixing` |
| `stirring_staff_auto_shapeless` | 搅拌杖 · 自动无序合成 | `minecraft:crafting`（无序、多原料、非 shaped、不可压缩） |
| `stirring_staff_auto_brewing` | 搅拌杖 · 自动酿造 | 运行时由 `PotionMixingRecipes` 生成，不是数据包配方 |
| `pointer` | 指杆 | `create:deploying`，再兜底 `create:item_application` |
| `infusion_gun` | 灌注枪 | `create:filling` |

风箱（`BellowsItem`）不在上表内：它走 Create 的 `FanProcessingType` / `AllFanProcessingTypes`（鼓风熔炼 / 烟熏 / 缠魂 / 洗涤），本来就是一个聚合入口，不受本模组配方 API 管辖。

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

---

## L3 · 独占层

独占配方活在一个**独立的自定义 `RecipeType`** 下：

```
create_hand_made:tool_recipe
```

Create 的机器查询的是 `create:compacting` / `create:mixing` 等自己的类型，因此**永远看不到**这些配方；只有 `tool` 字段指定的那个手工工具（以及对应的 JEI 类别）能读到。

### 覆盖范围（重要）

| | 工具 |
| --- | --- |
| ✅ 支持 | `press_hammer_basin`、`stirring_staff` |
| ❌ 暂不支持 | `press_hammer_depot`、`mortar`、`crusher_mortar`、`hand_saw`、`pointer`、`infusion_gun`（涉及 `create:pressing` / `create:milling` / `create:crushing` / `create:cutting` / `create:deploying` / `create:item_application` / `create:filling`） |
| ❌ 不涉及 | 风箱 4 类；`press_hammer_auto_square`、`stirring_staff_auto_shapeless`（这两类源自 `minecraft:crafting`）；`stirring_staff_auto_brewing`（运行时生成） |

原因：独占配方的配方类必须让 `getType()` 返回自定义类型，而 Create 只给 `BasinRecipe` 留了接受自定义类型的 `protected` 构造（`BasinRecipe(IRecipeTypeInfo, ProcessingRecipeParams)`）；其余家族的配方类把 `RecipeType` 硬编码在自己的 `AllRecipeTypes` 里，**要支持它们必须先改工具侧代码**。

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
| `tool` | ✅ | 字符串 | 归属工具，**只接受 `press_hammer_basin` 或 `stirring_staff`** |
| `ingredients` | ✅ | 数组 | 原料，物品 `{ "item": ... }` / `{ "tag": ... }`，或流体 `{ "type": "neoforge:single", "amount": ..., "fluid": ... }` |
| `results` | ✅ | 数组 | 产物，物品 `{ "id": ..., "count": ... }` 或流体 `{ "id": ..., "amount": ... }` |
| `processing_time` | ❌ | 整数 | 处理耗时（tick），默认 `0` |
| `heat_requirement` | ❌ | 字符串 | `none`（默认）/ `heated` / `superheated` |

**限制：**

- `tool` 写错工具 id，或写了不支持的工具：配方在加载时被拒绝，日志里会出现一条 `Parsing error loading recipe <你的配方 id>` 并附上原因（`Unknown tool id: ...` / `Tool '...' does not support exclusive recipes yet.`）。
- 该类型没有任何内置配方 —— 是否使用 L3 完全由整合包决定。

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
- `results` 用 `item_stack` / `fluid_stack` 组件：可以写 `'2x minecraft:gold_ingot'` 计数简写。
- 链式函数：`.heated()`、`.superheated()`、`.processingTime(n)`，也可以直接写 `heat_requirement` 键。

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

---

## 工具 ID 清单

`tool_id` 就是 `HandMadeTool` 枚举常量名的小写下划线形式（`PRESS_HAMMER_BASIN` → `press_hammer_basin`）。用于 L2 的文件名/参数，以及 L3 的 `tool` 字段。

| tool_id | 说明 | L3 |
| --- | --- | --- |
| `press_hammer_basin` | 冲压锤 · 工作盆（压缩） | ✅ |
| `press_hammer_auto_square` | 冲压锤 · 工作盆（自动摆放） | ❌ |
| `press_hammer_depot` | 冲压锤 · 置物台 / 传送带 | ❌ |
| `mortar` | 研钵 · 研磨 | ❌ |
| `crusher_mortar` | 碾钵 · 粉碎 + 研磨 | ❌ |
| `hand_saw` | 手锯 · 切削 | ❌ |
| `stirring_staff` | 搅拌杖 · 混合 | ✅ |
| `stirring_staff_auto_shapeless` | 搅拌杖 · 自动无序合成 | ❌ |
| `stirring_staff_auto_brewing` | 搅拌杖 · 自动酿造 | ❌ |
| `pointer` | 指杆 · 应用 | ❌ |
| `infusion_gun` | 灌注枪 · 注液 | ❌ |

---

## 常见问题

### 为什么我的独占配方没生效？

按顺序检查：

1. **`tool` 字段**是不是合法 id（上面的清单）。写错会在日志里看到 `Unknown tool id: '<你写的>'`。
2. **工具是否在覆盖范围内** —— 目前只有 `press_hammer_basin` 与 `stirring_staff` 支持独占配方，其它工具写了会被拒绝，日志里是 `Tool '<id>' does not support exclusive recipes yet.`。
3. **是否被 L2 过滤掉了** —— 检查 `tool_filter/<tool_id>.json` 的 `disabled` / `disabled_by_mod`，以及 KubeJS 脚本里的 `toolFilter` 回调。`disabled_by_mod` 里如果写了你自己放独占配方的命名空间，会把它一起禁掉。
4. **日志里有没有 `Parsing error loading recipe <id>`** —— 字段缺漏（比如没写 `tool`）会在加载时被拒绝：`Missing required field 'tool' for create_hand_made:tool_recipe`。
5. **配方是否真的在数据包里** —— 目录必须是 `data/<ns>/recipe/`，文件名随意。

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

数据包里流体原料必须是 `{"type": "neoforge:single", "amount": ..., "fluid": ...}` 这个形状（顶层 `type` 是 Create 要求的）；
KubeJS 的 `flat_sized_fluid_ingredient` 组件写出的是 NeoForge 的
`{"fluid": ..., "amount": ...}`（**没有顶层 `type`**），模组的 serializer 会在解析前自动补上
`"type": "neoforge:single"`，所以两种写法都能用。用 tag 选流体则在数据包里写
`{"type": "neoforge:tag", "amount": 250, "tag": "c:milk"}`。

### `disabled_by_mod` 会不会误伤独占配方？

**会**，它按 recipe id 的 **namespace** 匹配，不看配方类型。所以：

- 如果你的独占配方放在 `data/mypack/recipe/`（id 形如 `mypack:xxx`），而你在某个工具的 `disabled_by_mod` 里写了 `mypack`，那这条独占配方会被一起禁用。
- 反过来，`disabled_by_mod` 里写 `create`，不会影响你放在自己命名空间下的独占配方。

要精确控制就用 `disabled` 写完整 id。
