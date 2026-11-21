# 配方 API 参考（L1 / L2 / L3）

本文对应 `create_hand_made` 0.6.2。想"改手搓工具能做什么"，只需要看懂这里的 L2 与 L3。

---

## 0. 三层结构

| 层 | 是什么 | 由谁提供 | 改动方式 |
|---|---|---|---|
| **L1** | 工具默认继承的 **Create 原生**配方类型 | Create（本模组只是去查） | 无法改动，只能整体禁用某几条（L2） |
| **L2** | **过滤器**：从候选集里剔除配方 | 数据包 JSON + KubeJS 脚本 | 写 `handmade/filter/<tool>.json` 或监听 KubeJS 事件 |
| **L3** | 工具**独占**配方，走本模组自己的 `RecipeType` | 数据包 JSON | 写 `recipes/*.json`，`type` 用 `create_hand_made:*` |

**为什么必须分三层**：L1 是 Create 的机器配方，如果直接改它，Create 的锯子/压机/工作盆会跟着变。L2 只影响"手搓工具看到的候选集"，L3 提供手搓专属配方，两者都不碰 Create 的机器行为。

关键代码：

- `recipe/HandMadeRecipePool`：L2 的两张过滤表 + `getBaseRecipes`（L1+L3 收集）/ `applyFilter`（L2 剔除）
- `recipe/HandMadeRecipeLoader`：读 `data/<ns>/handmade/filter/<tool>.json`
- `recipe/HandMadeRecipeTypes`：L3 的 `RecipeType` / `RecipeSerializer` 注册
- `kubejs/HandMadeKubeJSPlugin`：KubeJS 事件入口

---

## 1. L1：工具 → Create 配方类型

`HandMadeRecipePool.getBaseRecipes(tool, level)` 的 switch 就是这张表。

| Tool id | L1 来源（Create RecipeType） | 备注 |
|---|---|---|
| `hand_saw` | `create:cutting` | 只收 `CuttingRecipe` |
| `hand_saw_stonecutting` | `minecraft:stonecutting` + `create_hand_made:stonecutting_recipe` | 受 Create 的 `recipes.allowStonecuttingOnSaw` 门控 |
| `press_hammer_depot` | `create:pressing` | 置物台 / 传送带路径 |
| `press_hammer_basin` | `create:compacting` | 按压时还额外识别"可压缩的合成配方"（4 份 → 1 份 / 9 份 → 1 份），见 `PressHammerItem.matchStaticFilters` |
| `mortar` | `create:milling` | |
| `crusher_mortar` | `create:crushing` + `create:milling` | 碾钵 = 研钵的升级版 |
| `stirring_staff` | `create:mixing` | 只收 `BasinRecipe` |
| `infusion_gun` | `create:filling` | |
| `pointer` | `create:deploying` + `create:item_application` | 只收 `ItemApplicationRecipe` |
| `bellows` | （无） | 风箱只有 L3 + 副手介质 → 鼓风类型 |

> `PRESS_HAMMER_BASIN` / `PRESS_HAMMER_DEPOT` 是同一个物品（冲压锤）的两条独立路径，拆成两个 id 才能分别写 L2 / L3。

**L1 是"全量拉取"**：`getBaseRecipes` 不调 `matches()`，与输入物品无关。想判断"这件物品能不能加工"请用 `CreateHandMadeApi.findRecipeFor(tool, level, stack)`。

---

## 2. L2：过滤器

### 2.1 数据包

路径：`data/<namespace>/handmade/filter/<tool_id>.json`

```json
{
  "remove": [
    "create:cutting/andesite_alloy",
    "minecraft:stonecutting/stone_button"
  ]
}
```

- `<tool_id>` 必须与 `ToolType.id` 完全一致（见上表 / `recipe/ToolType`）。写错只会在日志里出现 `Unknown tool in filter:`。
- `remove` 里是**完整配方 id**。全部条目合并进该工具的一张表，多个数据包/多个文件相加。
- 只影响**该 tool id 自己的候选集**：禁 `hand_saw` 的 `create:cutting/x`，Create 的机械锯仍然能用这条配方。
- **对 L1 与 L3 一视同仁**：工具查询时先过 L2 再匹配，被禁用的独占配方也不会被选中（0.6.2 起统一，此前 L2 只作用于 L1）。
- **JEI 类别同样按 L2 过滤**：被禁用的配方不会出现在手搓工具的分类页里（否则玩家照着摆料必然没反应）。风箱分类不受影响——风箱没有 L2。
- `SequencedAssemblyRecipe` 这类动态配方没有稳定 id，禁不掉。

### 2.2 KubeJS

服务端事件 `HandMadeEvents.toolFilter`（实现全在 `kubejs/HandMadeKubeJS.java`：插件入口 + 事件组 `Events` + 事件对象 `ToolFilterEvent`）：

```js
HandMadeEvents.toolFilter(event => {
  // 精确禁用一条
  event.disable('hand_saw', 'create:cutting/oak_log')

  // 禁用某工具下、某个 mod 的全部配方（按 namespace 匹配）
  event.disableByMod('mortar', 'some_other_mod')
})
```

- 脚本重载（`/reload` 或 KubeJS 重载）时会先清空 KubeJS 过滤表再重新派发事件，所以**每次都必须在事件里重新声明**，不要指望增量。
- tool id 写错会直接抛 `IllegalArgumentException: Unknown tool id`。

### 2.3 重载语义（重要）

数据包与 KubeJS 是**两条独立的重载链路**，`/reload` 时不保证先后：

- `HandMadeRecipePool.resetData()` 只清数据包表 —— 由 `HandMadeRecipeLoader#apply` 调
- `HandMadeRecipePool.resetKubeJS()` 只清 KubeJS 表 —— 由 `HandMadeKubeJSPlugin#onServerReload` 调
- `reset()` 是两者都清，仅供调试

如果哪天要自己加一层过滤来源，**不要**调 `reset()`，否则会把另一条链路刚填进去的内容静默抹掉。

---

## 3. L3：独占配方

### 3.1 `create_hand_made:tool_recipe`（通用容器）

一个 `RecipeType` 收纳所有"工厂家族"，靠 JSON 里的 **`tool` 字段**分派到对应家族的 `ProcessingRecipeSerializer`。

```json
{
  "type": "create_hand_made:tool_recipe",
  "tool": "mortar",
  "ingredients": [
    { "item": "minecraft:iron_ore" }
  ],
  "results": [
    { "item": "create:crushed_raw_iron", "count": 1 }
  ],
  "processingTime": 100
}
```

`tool` 取值与对应家族 / 配方类：

| `tool` | 家族基类 | 实现类 | 额外约束 |
|---|---|---|---|
| `hand_saw` | `CuttingRecipe` | `HandMadeRecipes.Cutting` | |
| `press_hammer_depot` | `PressingRecipe` | `HandMadeRecipes.Pressing` | |
| `mortar` | `AbstractCrushingRecipe` | `HandMadeRecipes.Crushing` | 输出上限 7 |
| `crusher_mortar` | 同上（共用） | `HandMadeRecipes.Crushing` | 输出上限 7 |
| `infusion_gun` | `FillingRecipe` | `HandMadeRecipes.Filling` | **必须至少一个流体输入** |
| `pointer` | `ItemApplicationRecipe` | `HandMadeRecipes.Application` | 2 槽位匹配（被加工物 + 手持物） |
| `press_hammer_basin` | `BasinRecipe` | `HandMadeRecipes.Tool` | 整盆匹配 |
| `stirring_staff` | `BasinRecipe` | `HandMadeRecipes.Tool` | 整盆匹配 |

其余字段沿用 Create 的 `ProcessingRecipe` 序列化格式（`ingredients` / `results` / `processingTime` / `heatRequirement` / `fluidIngredients` / `fluidResults`）。

> ⚠️ 注意是 **camelCase**：Create 的 `ProcessingRecipeSerializer` 只读 `processingTime` / `heatRequirement`，写成 `processing_time` 会被静默忽略（时长退回默认 100 tick）。

**`exclusive_chance`（布尔，可选，默认 `false`）** —— 把 `results` 当"互斥权重表"而不是 Create 默认的"每条独立摇"：

```json
{
  "type": "create_hand_made:tool_recipe",
  "tool": "press_hammer_depot",
  "ingredients": [{ "item": "minecraft:book" }],
  "results": [
    { "item": "patchouli:guide_book",
      "nbt": { "patchouli:book": "create_hand_made:handbook" }, "chance": 0.9 },
    { "item": "minecraft:leather", "chance": 0.05 },
    { "item": "minecraft:paper",   "chance": 0.05 }
  ],
  "exclusive_chance": true
}
```

- 默认（`false`）= 原版语义：每个产物各自按 `chance` 摇一次，**可能同时出好几个**；
- 打开后一次只摇出**一个**：把各条 `chance` 当权重累加，摇一次落在哪段就出哪个；
- 权重总和 < 1 时，剩下那段表示"什么都没摇到"（料照扣，与概率配方摇空一致）；
- 目前**只有 `press_hammer_depot` 家族支持**（`HandMadeRecipes.Pressing`），别的 `tool` 写了只会打一条 warning 并被忽略。
- 模组自带的例子：[`handbook_press.json`](../src/main/resources/data/create_hand_made/recipes/handbook_press.json)（砸书 → 90% 手册 / 5% 皮革 / 5% 纸，外层包 `forge:conditional` 只在装了 Patchouli 时加载）。想关掉它，用 L2 过滤或 KubeJS 禁用即可。

**为什么注液必须校验流体输入**：Create 的 `validate()` 只检查上限、不检查下限，没有流体输入的灌注配方能加载成功，然后在工具侧 `getRequiredFluid()` 抛 `IllegalStateException` 运行时崩。这里在**加载期**就拒绝。

### 3.2 `create_hand_made:stonecutting_recipe`

结构等同原版 `minecraft:stonecutting`，只是 `RecipeType` 独立（供手锯的切石路径单独过滤）：

```json
{
  "type": "create_hand_made:stonecutting_recipe",
  "group": "",
  "ingredient": { "item": "minecraft:stone" },
  "result": { "item": "minecraft:stone_slab", "count": 2 }
}
```

### 3.3 `create_hand_made:bellows_recipe`

风箱专属：在 Create 的 `ProcessingRecipe` 之上多一个**必填**的 `fan_type`。

```json
{
  "type": "create_hand_made:bellows_recipe",
  "fan_type": "create:blasting",
  "ingredients": [
    { "item": "minecraft:raw_iron" }
  ],
  "results": [
    { "item": "minecraft:iron_ingot" }
  ]
}
```

- `fan_type` 必须是**带命名空间的完整 id**（`create:blasting`、`create:smoking`、`create:splashing`、`create:haunting`，或任何第三方注册进 `CreateBuiltInRegistries.FAN_PROCESSING_TYPE` 的类型）。
- 旧短名（`blasting`）已不再接受，会抛 `Invalid fan_type ... Must be a full resource location`。
- 输入上限 1、输出上限 12，不能指定 `processingTime`（风箱按次结算）。
- 同一物品同时命中 L3 与 Create 原生鼓风配方时，**L3 优先**（`BellowsItem.findL3Results` 先查 L3，未命中回落到 `FanProcessingType.canProcess`）。

### 3.4 `bellows_media/*.json`（副手介质 → 鼓风类型）

路径：`data/<namespace>/bellows_media/*.json`。它不属于三层配方，但决定"风箱副手拿什么才能吹出什么效果"。

```json
{
  "type": "create:blasting",
  "ingredients": [
    { "item": "minecraft:lava_bucket" }
  ]
}
```

字段（都可单独使用，展开后合并）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | 完整 id | **必填**，鼓风类型 |
| `ingredients` | Ingredient 列表 | 原料（物品 / 标签） |
| `catalysts` | 单个标签字符串 | 如 `"#create:fan_catalysts/blasting"`，展开时扫 block/item/fluid 三个注册表 |
| `fluids` | 流体 id 列表 | 无桶流体的显式声明 |
| `priority` | 整数 | 覆盖该类型优先级（数字大的优先） |

解析失败只记 `Failed to parse bellows media:` 日志，不会中断其他文件。

---

## 纯手搓序列组装（`create_hand_made:hand_sequenced_assembly`）

只有本模组的手搓工具能推进的流水线；**原版机器连读都读不到**。字段与 `create:sequenced_assembly`
完全一致（1.20.1 同样是 NBT 进度，物品 NBT 键 `SequencedAssembly:{id,Step,Progress}`），把 `type` 改掉即可迁移。

| | 本类型 | `create:sequenced_assembly` + L3 步骤 |
|---|---|---|
| 谁能读到 | 只有 6 个手搓工具 | Create 机器（只看得到自己的步骤）+ 手搓工具 |
| 步骤 | 只能是 `create_hand_made:tool_recipe` | Create 步骤与 L3 步骤可混用 |
| JEI | 独立类别「手搓流水线」 | Create 的序列组装类别 |

```json
{
  "type": "create_hand_made:hand_sequenced_assembly",
  "ingredient": { "item": "minecraft:iron_ingot" },
  "transitionalItem": { "item": "minecraft:paper" },
  "results": [ { "item": "minecraft:iron_block" } ],
  "loops": 2,
  "sequence": [
    {
      "type": "create_hand_made:tool_recipe",
      "tool": "press_hammer_depot",
      "ingredients": [ { "item": "minecraft:paper" } ],
      "results": [ { "item": "minecraft:iron_nugget" } ]
    }
  ]
}
```

**约束**

- 步骤 `type` 必须是 `create_hand_made:tool_recipe`；写机器步骤（`create:pressing` 等）→ **加载期直接报错**（1.20.1 这份实现比 1.21 更严：宁可加载失败，也不要"能加载但永远不前进"）。
- **步骤的 `results[0]` 会被覆盖成「前进后的过渡物品」**（`enforceNextResult`），所以按惯例要写成过渡物品本身；第 2 个及之后的产物作为**副产物**保留。
- `results` 的 `chance` 是**权重**，不是独立概率（Create 也是先求和再抽一个产物），JEI 显示归一化后的百分比。
- L2 过滤（`disabled` / `disabled_by_mod`）**管不到流水线步骤**：步骤是内联配方，没有自己的 id。

**提示文本**：过渡物品的工具提示里，「下一步」显示成 `<工具> · <附加物/流体>`
（例如 `指杆 · 燧石`、`灌注枪 · 250 mB 水`），与 Create 的 “deploying item: …” 同义。

**过渡物品可以就是起始原料本身**：进度只存在物品 NBT 的 `SequencedAssembly` 里，物品身份不变也能被判为
「已在流程中」，所以"同一个东西原地被砸 N 下"不需要额外造过渡物品（`ingredient` 与 `transitionalItem`
写同一个物品即可，外观不变、只有工具提示显示「第 N / M 步」）。代价是它和普通同种物品不堆叠。
想改成看得见的东西，把 `transitionalItem` 换成别的物品即可（步骤的 `results[0]` 不用跟着改，反正会被覆盖）。

**步数 = `sequence.size()` × `loops`**，收尾判定 `(step + 1) / size >= loops`：单步 + `loops: 3` 就是连做 3 下出产物。

**玩家怎么推进**：把过渡物品放到介质上，再用该步的工具操作 ——
置物台 / 传送带（冲压锤、指杆、灌注枪）、副手 / 直接使用（手锯、研钵、碾钵）。

**KubeJS**：用原始 JSON（`event.custom`），**不提供** `event.recipes.create_hand_made.*` schema ——
KubeJS 的产物组件表达不了 `chance`，`ingredients` 也只认纯物品（我们的配方是物品 / 流体混合数组），
注册 schema 会让带流体的步骤整条解析失败。

**JEI**：类别页刻意做成和 Create 自己的序列组装**同一套版式**（180×115、罗马数字标步、
长箭头 + `xN` 圈数图标、随机产物的 `?` 底纹），差别只有产物是权重池 ——
主槽画权重最大的那条、其余注册成**隐藏产物**（看不见但「用途查询」能反查到），
鼠标停在整个产物槽上会列出池里每一条的归一化百分比；最多画 5 列，多出的在右上角显示 `+N`，
悬停可看被折叠步骤的工具与附加物/流体。

---

## 4. Java API

对外请统一绑 `com.alben.createhandmade.api`，不要直接依赖 `item` / `recipe` 内部类（签名视为不稳定）。

```java
// 查询
List<Recipe<?>> all   = CreateHandMadeApi.recipesFor(ToolType.MORTAR, level);          // L1 + L3，不含 L2
List<Recipe<?>> usable = CreateHandMadeApi.recipesForFiltered(ToolType.MORTAR, level);  // 已剔 L2
Recipe<?> one         = CreateHandMadeApi.findRecipeFor(ToolType.MORTAR, level, stack); // 按输入查
boolean disabled      = CreateHandMadeApi.isRecipeDisabled(ToolType.MORTAR, recipeId);

// 配置（读到的是"服务端权威值"，客户端也能安全调用）
int charge = HandMadeConfig.pressHammerChargeTicks();
boolean fell = HandMadeConfig.isTreeFellingEnabled();
```

`API_VERSION` 目前为 `1`：新增方法不升版本，破坏性变更才升。

---

## 5. 常见坑

1. **tool id 拼错不会有明显报错**：过滤器里写错只打 warning；`tool_recipe` 里写错会抛 `Unknown tool`。
2. **`recipesFor` 不等于"游戏里能做什么"**：它不含 L2。要最终口径用 `recipesForFiltered`。
3. **`findRecipeFor` 只覆盖"看一件物品就能判"的工具**：工作盆 / 搅拌杖 / 灌注枪 / 指杆 / 风箱返回 `null`，各自有专门入口（`pressBasin` / `findStirringRecipe` / `bellowsCanProcess` 等）。
4. **动态配方（`SequencedAssemblyRecipe`）没有稳定 id**，L2 与按 id 查找都管不到它。
5. **改 L1 是改不了的**：想"取消某个 Create 配方对手搓工具可见"，只能 L2 禁用；想"多出一条手搓专属配方"，写 L3。
