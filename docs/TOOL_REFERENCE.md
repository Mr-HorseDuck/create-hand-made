# 工具速查表

配合 [配方 API 参考](./RECIPE_API.md) 使用。本文回答"每个工具读哪些配方、L2/L3 怎么作用、禁一条会连带什么"。

**通用约定**

- 所有工具都是 **L3 优先、L1 兜底**（手锯例外，见下）。
- **L2 过滤对 L1 与 L3 一视同仁**，只作用于本工具自己的过滤表（L3 的归属看 JSON 的 `tool` 字段；碾钵读到的 `tool: mortar` 条目按 `crusher_mortar` 表过滤）。JEI 的分类页与工具用同一套口径。
- "禁用连带"指：禁用这个 tool id 的某条配方，会不会影响同一个物品的**另一条**路径。
- 所有时间参数在 `config/create_hand_made-server.toml` 的 `tools.*` 下，单位 tick（20 tick = 1 秒）。这些参数**服务端权威**：多人游戏里客户端拿到的是服务端同步下来的值。
- 女仆兼容的运行时参数在存档的 `serverconfig/create_hand_made-maid-server.toml` 里（同样是**服务端权威**，单人可改、多人只读）；只有 `blocked_mods` 留在 `config/create_hand_made-maid.toml`（它在 mod 初始化阶段就要读，改了要重启）。

---

## 冲压锤 Press Hammer

- **操作**：长按右键蓄力（`press_hammer.charge_ticks`，默认 15）→ 松手。对准实体：命中即暴击（×1.5）并附带额外击退；对准工作盆：按压整盆；对准置物台/传送带：按压上面的物品。
- **两条独立路径**（同一物品，不同 tool id）：

| 路径 | tool id | L1 | L3 `tool` | 匹配顺序 |
|---|---|---|---|---|
| 置物台 / 传送带 | `press_hammer_depot` | `create:pressing` | `press_hammer_depot` | L3 → L1 `PRESSING`（只收可自动化配方） |
| 工作盆 | `press_hammer_basin` | `create:compacting` + **可压缩的合成配方**（4→1 / 9→1） | `press_hammer_basin` | L3 → L1 `RecipeFinder(compacting \| 可压缩合成)` |

- **禁用连带**：
  - 禁 `press_hammer_depot` 的配方 → 只影响置物台/传送带路径，工作盆照旧。
  - 禁 `press_hammer_basin` → 同时影响 `create:compacting` 与"可压缩合成"两类候选（它们走同一张过滤表）。
  - 压缩合成路径受 Create 的 `recipes.allowShapedSquareInPress` 门控，关掉后连候选都不会出现。
- **批量**：附魔 **批量加工**（Batch Crafting）等级 n → 一次最多 2ⁿ 份（1→2、2→4、3→8）；某一份摇不出产物按"白磨"处理（消耗材料、不产出），整批零命中时不动原料也不扣耐久。
- **耐久**：只有真的按压成功才扣 1 点；挥空、对着空气不扣。

---

## 研钵 Mortar

- **操作**：副手放原料 → 主手研钵长按右键（起手 `windup_ticks` 默认 20，加工 `processing_ticks` 默认 100）。产物在加工完成时产出，可附 **批量加工**。
- **配方来源**：L3（`tool: mortar`）→ L1 `create:milling` → L2 过滤。
- **禁用连带**：`mortar` 表作用于研钵读到的全部候选（L1 `create:milling` + `tool: mortar` 的 L3）。碾钵也会读 `tool: mortar` 的 L3 条目，但那些是按 `crusher_mortar` 表过滤的——两张表互不影响。

- **配置**：`mortar.windup_ticks`(20) / `processing_ticks`(100) / `pound_cycle_ticks`(10，杵上下捶捣动画周期)。

---

## 碾钵 Crusher Mortar

- **操作**：同研钵（研钵 + 钻石合成）。
- **配方来源**：L3（`tool: crusher_mortar` **或** `tool: mortar`）→ L1 `create:crushing`，无命中回落 `create:milling` → L2 过滤（表 id 为 `crusher_mortar`）。
- **上限**：L3 单条最多 7 个产出（取"研钵 4"与"粉碎 7"的并集）。
- **禁用连带**：`crusher_mortar` 表覆盖它读到的全部候选（L1 `create:crushing` / `create:milling` + L3，含 `tool: mortar` 的条目）。
- **配置**：`crusher_mortar.windup_ticks`(20) / `processing_ticks`(100) / `pound_cycle_ticks`(10)。

---

## 搅拌杖 Stirring Staff

- **操作**：对着有料的工作盆长按右键，连续搅拌（起手 `windup_ticks` 默认 8，之后每 `processing_ticks` 默认 60 完成一次）。副手使用需要潜行。
- **配方来源**：L3（`tool: stirring_staff`，`BasinRecipe` 家族）→ L1 `create:mixing` + Create 允许的无序合成（受 `recipes.allowShapelessInMixer` 门控）→ L2 过滤。
- **禁用连带**：只影响手搓搅拌。Create 的机械搅拌器完全不受影响（L2 只作用于工具候选集）。
- **配置**：`stirring_staff.windup_ticks`(8) / `processing_ticks`(60)。

---

## 灌注枪 Infusion Gun

- **操作**：右键流体源抽取 → 右键目标方块/物品注入；也能直接执行填充配方。
- **配方来源**：L3（`tool: infusion_gun`，`FillingRecipe` 家族，**必须声明至少一个流体输入**，否则加载期报错）→ L1 序列组装 / `create:filling` → L2 过滤。
- **放置/抽取有保护检查**：放流体前走 `player.mayInteract` 与 `ForgeEventFactory.fireFluidPlaceBlockEvent`（被保护插件取消则放弃），抽流体只查 `mayInteract`。
- **禁用连带**：`infusion_gun` 表作用于灌注枪的填充路径（L1 与 L3 都算）；枪本身的"抽/注流体"是方块交互，不走配方，无法用 L2 禁用。
- **配置**：无专属时间参数（充液沿用填充配方自身的 `processing_time`）。

---

## 指杆 Pointer

- **操作**：右键方块执行部署加工（被加工物在方块上/置物台/传送带上）；潜行右键高亮方块；配套女仆时用于指定工作方块/目标。
- **配方来源**：L3（`tool: pointer`，`ItemApplicationRecipe` 家族，2 槽位匹配）→ L1 `create:deploying` + `create:item_application` → L2 过滤。
- **禁用连带**：`pointer` 表作用于指杆的 L1 与 L3 候选。`SequencedAssemblyRecipe` 动态配方没有稳定 id，禁不掉。
- **配置**：无专属时间参数。

---

## 手锯 Hand Saw

- **操作**：副手放原料 → 主手长按右键连续切削（起手 `windup_ticks` 默认 10，之后每 `cut_ticks` 默认 20 一次）；潜行左键整树砍伐（`enable_tree_felling`）；右键可剥皮/刮铜等（沿原版斧行为）。
- **配方来源（三段，注意短路）**：
  1. **L3**（`tool: hand_saw`）—— **命中就直接返回，不再看 L1**；
  2. L1：序列组装优先，否则 `create:cutting`（跳过 `shouldIgnoreInAutomation` 的配方）→ L2 过滤表 `hand_saw`；
  3. 以上都没有 → 切石回退：`minecraft:stonecutting` + `create_hand_made:stonecutting_recipe`，走独立的 L2 表 `hand_saw_stonecutting`。
- **列表顺序稳定**：结果按配方 id 排序（动态配方排最后），保证客户端/服务端选中同一条。
- **禁用连带**：
  - `hand_saw` 与 `hand_saw_stonecutting` 是两张表：禁切削不会影响切石回退；L3 切削按 `hand_saw` 表过滤。
  - 切石整条路径受 Create 的 `recipes.allowStonecuttingOnSaw` 门控（关闭时 L1 与 L3 切石都不出现）。
- **整树砍伐**：以玩家为上下文破坏方块（走 `BlockEvent.BreakEvent` 与玩家工具算掉落），每次砍伐只扣 1 点耐久。
- **配置**：`hand_saw.enable_tree_felling`(true) / `windup_ticks`(10) / `cut_ticks`(20)。

---

## 风箱 Bellows

- **操作**：主手风箱长按右键（蓄力 `bellows.charge_ticks` 默认 10），副手放"介质"决定鼓风类型；也可对置物台/传送带上的物品、或对实体吹。
- **没有 L1**：它不走 `applyFilter`，候选是
  1. L3 `create_hand_made:bellows_recipe`（按 `fan_type` + `matches` 匹配，L3 命中的产出优先）；
  2. 未命中则回落 Create 原生 `FanProcessingType`（`create:blasting` / `smoking` / `splashing` / `haunting`，含第三方注册的类型）。
- **介质来源**（`data/<ns>/bellows_media/*.json` + 自动发现 + 内置规则）：物品/标签 → 鼓风类型；服务器配置 `bellows.bellows_type_overrides` 可逐行覆盖。
- **禁用手段**：没有 L2。想删掉某条 L3 风箱配方，直接删/改那个数据包文件；想禁掉某个介质，用配置覆盖或数据包。
- **配置**：`bellows.charge_ticks`(10) / `bellows.bellows_type_overrides`（每行 `物品或标签=类型id`）。
- **耐久**：只有真吹到东西（加工了物品，或吹到实体且介质对实体有实际效果）才扣。

---

## 女仆兼容（可选，需 TLM）

女仆可用的工具（`TaskHandMade` 注册的行为表，数字为优先级）：冲压锤 5、液体转移 6、灌注枪就近注液 7、搅拌杖 8、手锯 9、研钵 10、碾钵 11、风箱 12、指杆部署 13。

> 灌注枪有两条路径，**互斥**：玩家用指杆指定过抽/注/过剩目标 → 走 6 号行为（按指定目标抽、注）；没有指定 → 走 7 号行为，自己在 `search_radius` 内找附近的置物台注液 / 工作盆存液，冷却用 `infusion_gun_cooldown`，并且带节流（找不到目标时按冷却窗口重扫，不会每 tick 扫一遍范围）。指杆的潜行高亮属于客户端交互，与女仆无关。

**只加工模式（只标工作方块）**

用指杆 MARK 模式给女仆指定标记时，**只有工作方块是必需的**：

| 标记情况 | 女仆行为 |
|---|---|
| 工作方块 + 输入 + 输出 | 完整物流：自己从输入取料 → 在工作方块上加工 → 产物搬到输出 |
| **只标工作方块** | **只加工**：只处理玩家自己放在工作方块上的物品，**不取料、不搬产物**（冲压锤 / 手锯 / 风箱的 Depot 分支与 Belt 分支、冲压锤的 Basin 分支都支持） |
| 只标输入 + 输出（无工作方块） | 研钵、碾钵仍可用（它们只需要输入与输出容器） |

适用场景：想让女仆只负责"敲 / 锯 / 吹"这一步，取料和产物搬运交给别的机械（机械臂、传送带、物流）时，只标工作方块即可。指杆部署与液体搬运**仍然要求完整标记**，因为它们的职责本身就是物流。应用标记时聊天栏会提示当前是「完整」还是「只加工模式」。

- 配置文件与主配置分开，节名 `maid`：`search_radius`(8.0 格)、`passive_infusion_gun`(true，"没有指杆目标时是否就近自动注液/存液"——关掉可避免女仆与玩家手搓抢同一个置物台)、各工具冷却（`press_hammer_cooldown` 16 / `infusion_gun_cooldown` 16 / `hand_saw_cooldown` 20 / `bellows_cooldown` 16 / `pointer_deploy_cooldown` 16 / `stirring_staff_cooldown` 16 / `mortar_cooldown` 60，单位 tick）、`particle_level`(3, 1–5)、禁用兼容的模组名单。
- 冷却会按女仆好感度缩短（每档 −7.5%，最多 −22.5%）。
- 女仆用工具不消耗工具耐久（走女仆规则）。
- 首次把某把工具装到女仆主手时会弹一次"气泡"，之后按 30 秒冷却 + 好感度分档 + 耐久/连续加工上下文说话。

---

## 快速对照表

| 工具 | L1 | L2 表 | L3 `tool` | 匹配顺序 | 主要配置 |
|---|---|---|---|---|---|
| 冲压锤 · 置物台 | `create:pressing` | `press_hammer_depot` | `press_hammer_depot` | L3 → L1 | `press_hammer.charge_ticks` / `charge_window_ticks` |
| 冲压锤 · 工作盆 | `create:compacting` + 压缩合成 | `press_hammer_basin` | `press_hammer_basin` | L3 → L1 | 同上 |
| 研钵 | `create:milling` | `mortar` | `mortar` | L3 → L1 | `mortar.*` |
| 碾钵 | `create:crushing` + `milling` | `crusher_mortar` | `crusher_mortar` / `mortar` | L3 → CRUSHING → MILLING | `crusher_mortar.*` |
| 搅拌杖 | `create:mixing` + 无序合成 | `stirring_staff` | `stirring_staff` | L3 → L1 | `stirring_staff.*` |
| 灌注枪 | `create:filling` | `infusion_gun` | `infusion_gun`（必须带流体输入） | L3 → L1 | — |
| 指杆 | `deploying` + `item_application` | `pointer` | `pointer` | L3 → L1 | — |
| 手锯 · 切削 | `create:cutting` | `hand_saw` | `hand_saw` | **L3（短路）→ L1** | `hand_saw.*` |
| 手锯 · 切石 | `minecraft:stonecutting` + `stonecutting_recipe` | `hand_saw_stonecutting` | — | 切削全空时回退 | `hand_saw.enable_tree_felling` |
| 风箱 | 无 | 无 | `bellows_recipe`（`fan_type`） | L3 → Create 鼓风类型 | `bellows.*` |
