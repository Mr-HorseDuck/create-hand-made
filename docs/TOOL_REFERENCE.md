# 手工工具参考表

面向整合包作者的速查表：8 个手工工具的属性、配方来源、以及"能不能被 L2 过滤 / 能不能被 L3 独占"。

> **数据来源**：所有字段都是读代码得到的（在括号里给了 `文件:行`），不是凭记忆。
> **`副手可用` / `兼容机械手` 两列一律为 `—`** —— 这两项需要游戏内实测，本表不做推断。
> **术语**：L1 = Create 自己的配方；L2 = 本模组的数据包/KubeJS 过滤层（`HandMadeRecipeFilters`）；L3 = 本模组的独占配方层（`create_hand_made:tool_recipe` / `bellows_recipe`）。

---

## 表 1 · 工具总览

| 工具 | item id | 初始耐久 | 副手可用 | 兼容机械手 | 对应 Create 机械 | 覆盖完整性 |
| --- | --- | --- | --- | --- | --- | --- |
| 冲压锤<br>Press Hammer | `create_hand_made:press_hammer` | 256 | — | — | 动力冲压机<br>`create:mechanical_press` | **部分** — 工作盆路径与压床同源（`BasinRecipe.match/apply`，**含热量校验**：`BasinRecipe.java:73-76`）；置物台路径同源。差异：手动单次触发（蓄力 15 tick）、成功一次扣 1 耐久，压床是方块循环、无消耗；额外有蓄力攻击（暴击强度 +2，`PressHammerItem.java:250`）与空挥扣耐久 |
| 研钵<br>Mortar | `create_hand_made:mortar` | 256 | — | — | 石磨<br>`create:millstone` | **部分** — 配方同为 `create:milling`、概率产物保留（`MortarItem.java:118`）；耗时被**强制 100 tick**、忽略配方的 `processing_time`（`MortarItem.java:40-44`），石磨则按 `getProcessingDuration()` 计时（`MillstoneBlockEntity.java:121`）。原料从**副手**取 1 个并暂存进物品数据组件（`MortarItem.java:69-86`），中途松手退还 |
| 碾钵<br>Crusher Mortar | `create_hand_made:crusher_mortar` | 512 | — | — | 粉碎轮 + 石磨（兜底）<br>`create:crushing_wheel` / `create:millstone` | **部分** — 优先级与粉碎轮一致（CRUSHING → MILLING 兜底，`CrusherMortarItem` 复刻 `CrushingWheelControllerBlockEntity.findRecipe`）；耗时同样强制 100 tick（`CrusherMortarItem.java:40-41`），粉碎轮按配方时长（`CrushingWheelControllerBlockEntity.java:368`） |
| 指杆<br>Pointer | `create_hand_made:pointer` | 512 | — | — | 机械手<br>`create:deployer` | **部分** — 配方过滤与机械手一致（`CAN_BE_AUTOMATED` 即 `_manual_only` 后缀，`PointerItem.java:320,327`）、序列组装优先（`:298-301`）、`keep_held_item` 语义一致（`:338-343`）。差异：只在**置物台/传送带**上由玩家右键触发，机械手是方块自动化；指杆扣自身耐久（`:183`），机械手不消耗自身 |
| 搅拌杖<br>Stirring Staff | `create_hand_made:stirring_staff` | 256 | — | — | 动力搅拌器（工作盆）<br>`create:mechanical_mixer` | **部分** — 对**工作盆**整体执行 Create 的 `BasinRecipe.apply`（`StirringStaffItem.java:112`），因此热量校验、容器残留等语义与搅拌器一致。差异：手动单次（蓄力 60 tick）、成功扣 1 耐久；额外支持"自动无序合成"与"自动酿造"两个场景（受 Create 配置门控） |
| 灌注枪<br>Infusion Gun | `create_hand_made:infusion_gun` | 256 | — | — | 注液器<br>`create:spout` | **部分** — 同为 `create:filling`、同样支持 Create 的通用物品注液 `GenericItemFilling`（`InfusionGunItem.java:653`）。差异：手持 + 枪内流体（数据组件）而非方块 + 流体输入；**不做** `CAN_BE_AUTOMATED` 过滤（`:818` 明确注明"改造前就没有，不新增"）；另有 3 项与配方无关的能力：从容器/流体源抽流体（`:714`）、把 1000mB 放成流体源（`:526`）、把枪内流体存进容器 |
| 风箱<br>Bellows | `create_hand_made:bellows` | 256 | — | — | 鼓风机<br>`create:encased_fan` | **部分** — 4 种鼓风类型与鼓风机共用同一批配方与粒子/生物效果（`BellowsItem.java:307-345`、`BellowsAirParticle.java:148` 调 `type.morphAirFlow`）。差异：只处理**置物台/传送带**上的物品、瞬发（蓄力 10 tick、扣 1 耐久），鼓风机按 config `fanProcessingTime` 计时且能处理世界掉落物（`FanProcessing.java:60-63`）；风箱还需要**副手介质**来决定鼓风类型；额外会推动射线上的实体。**L2 对风箱无效**（见下） |
| 手锯<br>Hand Saw | `create_hand_made:hand_saw` | 512 | — | — | 动力锯<br>`create:mechanical_saw` | **部分** — 配方同为 `create:cutting`、概率/多产物保留（`HandSawItem.java:361-364`）、`shouldIgnoreInAutomation` 过滤一致（`:409`）、序列组装优先（`:389-393`）。差异：手持 + 副手放原料、右键蓄力触发、消耗副手 1 个并扣 1 耐久；额外有斧头能力（去皮/刮蜡/除锈，`:453-469`）与整树砍伐（配置开关 `enableTreeFelling`，`:151,432`），这两项动力锯都没有 |

> 耐久数值出处：`ModItems.java`（冲压锤 `:22`、研钵 `:49`、碾钵 `:55`、指杆 `:61`、搅拌杖 `:101`、风箱 `:133`、灌注枪 `:139`、手锯 `:144`）。
> 工具中英文名出处：`assets/create_hand_made/lang/zh_cn.json` 与 `en_us.json`（键名 `item.create_hand_made.<id>`）。

---

## 表 2 · 配方来源明细

`L2 可过滤` 的判断依据：该场景的候选集是否来自 `HandMadeRecipePool.getBaseRecipes(...)`（池的最后一步是 `HandMadeRecipeFilters.isDisabled`，`HandMadeRecipePool.java:111`）。
`L3 可独占` 的判断依据：是否有对应的 L3 配方家族类（`HandMadeToolRecipeSerializer.java:108-118` 的家族分派表）。

| 工具 | 使用场景 | 配方来源 | L2 可过滤 | L3 可独占 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 冲压锤 | 工作盆 | `create:compacting` | ✅ | ✅ basin 家族（`HandMadeToolRecipe`） | 与压床同源：`BasinRecipe.match` + `BasinRecipe.apply`（`PressHammerItem.java:319-320`），**含热量校验** |
| 冲压锤 | 工作盆 · 自动摆放 | `minecraft:crafting`（可压缩的 4/9 配方） | ✅ | ❌ | 判定与压床一致（`MechanicalPressBlockEntity.canCompress` + 配置 `allowShapedSquareInPress`，`HandMadeRecipePool.java:220-238`）。**运行时实时读 crafting**，所以禁掉某条 crafting 配方会连带影响本场景（但**不影响工作台本身**，L2 只在工具侧生效） |
| 冲压锤 | 置物台 / 传送带 | `create:pressing` | ✅ | ✅ pressing 家族（`HandMadePressingRecipe`） | 序列组装优先（`SequencedAssemblyRecipe.getRecipe`，`:338-341`）——**序列组装不在池里**，因此不受 L2 管辖 |
| 研钵 | 手持（副手放原料） | `create:milling` | ✅ | ✅ crushing 家族（`HandMadeCrushingRecipe`） | 耗时强制 100 tick，与配方的 `processing_time` 无关 |
| 碾钵 | 手持（副手放原料） | `create:crushing` | ✅ | ✅ crushing 家族（`HandMadeCrushingRecipe`） | 优先匹配 |
| 碾钵 | 手持（副手放原料） | `create:milling`（兜底） | ✅ | ✅ crushing 家族（`HandMadeCrushingRecipe`） | 仅当 CRUSHING 无匹配时才用 |
| 指杆 | 置物台 / 传送带 | `create:deploying` | ✅ | ✅ application 家族（`HandMadeApplicationRecipe`） | 优先级更高；叠加 `CAN_BE_AUTOMATED`（`_manual_only`）过滤 |
| 指杆 | 置物台 / 传送带 | `create:item_application` | ✅ | ✅ application 家族（`HandMadeApplicationRecipe`） | DEPLOYING 全部匹配失败后才查它（池保证 DEPLOYING 在前，`HandMadeRecipePool.java:447-465`） |
| 搅拌杖 | 工作盆 | `create:mixing` | ✅ | ✅ basin 家族（`HandMadeToolRecipe`） | 走 `BasinRecipe.apply`，含热量校验 |
| 搅拌杖 | 工作盆 · 自动无序 | `minecraft:crafting`（无序、原料 >1、不可压缩） | ✅ | ❌ | 受 Create 配置 `allowShapelessInMixer` 门控（`HandMadeRecipePool.java:390`）。**运行时实时读 crafting**，禁用会连带影响本场景 |
| 搅拌杖 | 工作盆 · 自动酿造 | `PotionMixingRecipes`（代码生成，**不在 RecipeManager**） | ❌ | ❌ | 运行时**刻意绕开配方池**（用 `sortRecipesByItem` 的物品索引，`StirringStaffItem.java:202-205`）→ L2 无效；受配置 `allowBrewingInMixer` 门控（`:206`）。配方池里的对应 case 只服务 JEI 展示 |
| 灌注枪 | 置物台 / 传送带上的物品 | `create:filling` | ✅ | ✅ filling 家族（`HandMadeFillingRecipe`） | **不做** `CAN_BE_AUTOMATED` 过滤（`InfusionGunItem.java:818`）；除配方外还支持通用注液 `GenericItemFilling`（`:662`） |
| 灌注枪 | （非配方）抽流体 / 放流体源 / 存流体 | — | — | — | 三项都不来自配方，因此 L2/L3 都不适用 |
| 风箱 | 置物台 / 传送带上的物品 | `create:blasting`（底层先 `minecraft:smelting`、后 `minecraft:blasting`，`AllFanProcessingTypes.java:121-137`） | ❌ | ❌ | **风箱不走配方池**（`BellowsItem` 全文没有 `HandMadeRecipePool` 引用），也没有 `HandMadeTool` 常量 → L2 结构上就够不到它。L3 侧：`create_hand_made:bellows_recipe` 类型**已存在但风箱尚未读取**（T6 批次 2 才接线） |
| 风箱 | 同上 | `create:smoking`（底层 `minecraft:smoking`，`:337-342`） | ❌ | ❌ | 同上 |
| 风箱 | 同上 | `create:haunting`（`AllRecipeTypes.HAUNTING`，`:221-224`） | ❌ | ❌ | 同上 |
| 风箱 | 同上 | `create:splashing`（`AllRecipeTypes.SPLASHING`，`:401-404`） | ❌ | ❌ | 同上 |
| 手锯 | 手持（副手放原料） | `create:cutting` | ✅ | ✅ cutting 家族（`HandMadeCuttingRecipe`） | 叠加 `shouldIgnoreInAutomation` 过滤（`:409`）；序列组装优先且不在池里 |

> 补充：**L3 独占配方本身同样受 L2 管辖** —— 池的最后一步过滤作用于合并后的整份列表（`HandMadeRecipePool.java:100-111`），所以数据包/KubeJS 可以禁用一条 `create_hand_made:tool_recipe`。

---

## 表 3 · 禁用连带说明

| 配方来源 | 连带对象（禁用后一起失去该配方） |
| --- | --- |
| `minecraft:blasting` | 原版**高炉**；Create **鼓风机**的"鼓风熔炼"（`create:blasting` 会读 `minecraft:blasting`，且**优先**读 `minecraft:smelting`） |
| `minecraft:smelting` | 原版**熔炉**；Create **鼓风机**的"鼓风熔炼"（优先读取的就是它） |
| `minecraft:smoking` | 原版**烟熏炉**；Create **鼓风机**的"鼓风烟熏" |
| `minecraft:crafting` | 原版**工作台**；Create **动力合成器**（`RecipeGridHandler.java:149`）、**动力冲压机**的自动摆放（`MechanicalPressBlockEntity.java:190`）、**动力搅拌器**的自动无序（`MechanicalMixerBlockEntity.java:264`）—— 以及本模组**冲压锤的自动摆放**与**搅拌杖的自动无序**（这两个场景运行时实时读 crafting） |

**L2 的过滤只对工具侧生效，不影响原版机器与 Create 机器。**
被 L2 禁用的配方仍然完整留在 `RecipeManager` 里，只是从本模组工具的候选集中被移除（`HandMadeRecipePool.java:109-111` 的注释也写明了这一点）。所以：

- 想让"手搓独占"生效（同一输入既有 Create 配方又有 L3 独占配方时），可以用 L2 禁掉 Create 那条 —— 机器不受影响；
- 反过来，如果你用数据包/KubeJS 从 **RecipeManager** 层面**删除**配方（不是 L2 禁用），那机器也会一起失去它。

---

## 表 4 · 两个容易踩的点

1. **风箱完全不受 L2 管辖。** 两条独立证据：`BellowsItem` 没有任何 `HandMadeRecipePool` / `HandMadeRecipeFilters` 引用；`HandMadeRecipeFilters.isDisabled(HandMadeTool, ResourceLocation)`（`HandMadeRecipeFilters.java:122`）以 `HandMadeTool` 为键，而风箱**不在** `HandMadeTool` 枚举里（`HandMadeTool.java:13-15` 明确说明）。
   → 想禁用风箱的鼓风配方，目前只能从 RecipeManager 层面移除（会连带鼓风机），或者等 T6 批次 2 决定是否把风箱接进池。
2. **`create:deploying` / `create:item_application` 不是运行时从 crafting 转换来的。** 它们是 Create 自带的数据包配方（`data/create/recipe/item_application/*.json` 8 条、`data/create/recipe/deploying/*.json` 167 条，各自独立的 `RecipeType`）；Create 里唯一相关的"转换"是 `ManualApplicationRecipe.asDeploying(...)`，而它**只被 JEI 显示路径调用**（`CreateJEI.java:265`）。
   → **禁用 `minecraft:crafting` 不会影响指杆**；要禁指杆的某条应用配方，请直接禁对应的 `create:item_application/<id>` 或 `create:deploying/<id>`。

---

## 附：本表的统计口径

- 工具数：8 个物品；`HandMadeTool` 枚举有 **11** 个常量（冲压锤 3 + 研钵/碾钵 2 + 手锯 1 + 搅拌杖 3 + 指杆 1 + 灌注枪 1），因为部分工具有多个"配方场景"。
- L3 支持面：**8 个工具 id / 8 个工具**可写独占配方（家族分派见 `HandMadeToolRecipeSerializer.java:108-118`）；风箱是第 9 条路径（`bellows_recipe`），当前仅配方层就位。
- 本表只描述"配方读取"；工具的战斗/工具属性（攻击力、挖掘速度、交互距离）见 `ModItems.java`，不在本表范围。
