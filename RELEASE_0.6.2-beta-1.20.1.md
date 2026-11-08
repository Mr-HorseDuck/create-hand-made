# Create: Hand Made 0.6.2-beta（1.20.1 / Forge）

> 继 0.6.1 之后的一次大整理：**女仆兼容补完整、配置改成服务端说了算、修掉几个安全与耐久问题**。
> 配方系统、JEI 分类、工具玩法都没有破坏性改动，老存档可以直接升。

**下载** `create_hand_made-0.6.2-beta.jar`
**环境** Minecraft 1.20.1 · Forge 47.3.0+ · Create 6.0.8+
**可选** JEI 15.2.0+ · KubeJS 2001.6.5+ · 车万女仆 1.5.3+ · Cloth Config 11.1.136+

---

## 🧹 女仆：手搓工具行为补到 9 种

0.6.1 里灌注枪的行为类写好了，但**没有注册进行为表**，所以女仆拿着灌注枪什么都不会做。这一版补上了：

| 优先级 | 行为 | 说明 |
|---|---|---|
| 5 | 冲压锤 | 冲压附近工作盆 / 置物台 |
| 6 | 液体搬运 | 指杆指定过目标的抽 / 注 / 过剩输出 |
| **7** | **灌注枪（新启用）** | 未指定目标时自动找附近置物台注液、工作盆存液 |
| 8 | 搅拌杖 | 搅拌工作盆 |
| 9 | 手锯 | 切削副手物品 |
| 10 | 研钵 | 研磨 |
| 11 | 碾钵 | 碾磨 |
| 12 | 风箱 | 鼓风 |
| 13 | 指杆部署 | 部署加工 |

- **7 号会给 6 号让位**：玩家用指杆指定过抽 / 注 / 过剩目标时，灌注枪行为自动停手，避免两边抢同一个容器、注到玩家没指定的地方。
- **新增开关 `maid.passive_infusion_gun`**（默认 `true`）：服主关掉它就只保留「指杆指定目标」这一条路径，防止女仆和玩家手搓抢同一个置物台。
- **不会卡性能**：附近找不到目标时先扣冷却再扫描，不会每 tick 全量扫方块。

## ✨ 女仆：好感度加成真正生效

- **冷却缩短**：按好感度等级 ×0.925 / ×0.85 / ×0.775（即 −7.5% / −15% / −22.5%）。
- **免耐久**：5% / 10% / 15% 概率不消耗工具耐久。

以前 `adjustCooldown` 方法写好了却**没有任何调用点**，「好感度越快干活」实际上一直没生效——这次所有行为统一走 `cooldownFor` 把它接上了。

另外新增**首次使用气泡**：女仆第一次拿某件手搓工具时冒一条提示，每个女仆每件工具只提示一次。

## ⚙️ 配置：改成服务端说了算

- **主配置改为 SERVER 类型**，文件落在存档的 `serverconfig/create_hand_made-server.toml`。
- 14 个时间参数由服务端**统一下发**给客户端，多人服里客户端界面显示的就是服务端实际生效的值，不再是「各看各的本地默认值」；退出世界时清空镜像，避免从多人服退回单人后沿用上个服的参数。
- **女仆配置拆成两份**：运行时数值走 SERVER 配置（`serverconfig/create_hand_made-maid-server.toml`），`blocked_mods` 单独留在 `config/create_hand_made-maid.toml`（它要在模组初始化阶段读取，改动需重启）。
- 配置界面（Cloth Config）与游戏内一致：单人可改、多人只读。

## 🔒 安全与稳定性修复

- **女仆网络包伪造**：`ApplyToMaid` 包此前只按 entityId 找实体，改包就能给**任何玩家**的女仆写标记。现在服务端校验：必须手持指杆 + 女仆属于发送者 + 距离 ≤ 64。指杆标记同样补上「手持指杆 + 够得着」校验。
- **手锯砍树**：改为带玩家上下文调用，保护类插件、统计、附魔掉落正常生效；耐久仍只扣 1 点。
- **灌注枪放流体**：先后走 `mayInteract` 与流体放置事件（事件返回空气即中止）；吸取流体也做权限判断。
- **耐久不再白扣**：白磨、整批零命中、空挥等无效操作不再消耗耐久。
- **`/chm reload` 重载错目录**：原来从 `.minecraft/config` 读，而 SERVER 配置实际在存档的 `serverconfig/`，导致重载反而把内存里的值刷成默认值。已改为正确路径。
- **初始化期读配置**：初始化阶段读取尚未加载的 SERVER 配置，在开发环境会抛异常，现改为先判加载状态。
- **L2 过滤口径统一**：过滤表此前只作用于 L1（继承的 Create 配方），现在同时作用于 L3 独占配方（8 处工具查询 + JEI 分类页）。不再出现「工具里禁了、JEI 里还在」。

## 🔌 对外 API：13 → 31 个方法

`CreateHandMadeApi` 从 13 个方法扩到 31 个（`API_VERSION` 仍为 1），新增 18 个：

- **8 个写操作**：`pressBasin` / `pressTransported` / `grindMortarOnce` / `injectItems` / `storeFluid` / `extractFluidTick` / `applyBellows` / `damageTool`
- **查询补充**：`recipesForFiltered`（走 L2 过滤）、`findRecipeFor`、`recipeIdOf`、`handSawRecipes`、`findPressingRecipe`、`findPointerRecipe`、`findStirringRecipe`、`bellowsCanProcess`、`fluidHandlerAt` 等

文档同步：新增 `docs/RECIPE_API.md`（三层配方与数据包 / KubeJS 写法）、`docs/TOOL_REFERENCE.md`（每工具操作与配方来源速查），并重写 `docs/api.md`。

---

## ⚠️ 升级须知

1. **配置键搬家了**。如果你在 `config/create_hand_made-maid.toml` 里调过冷却、搜索半径、粒子等级等，这些键在 0.6.2 之后**不再生效**。请进单人世界一次，改存档里的 `serverconfig/create_hand_made-maid-server.toml`，或直接在游戏内的模组配置界面改。
2. **客户端与服务端必须同版本**。网络协议版本升到 2，旧版客户端连新版服务端（或反过来）会被拒。
3. **`blocked_mods` 仍在 `config/`**，改完需要重启游戏才生效。
4. 老存档 / 老工具不需要额外处理，没有物品或方块 ID 变更。

## 已知限制

- `API_VERSION` 仍是 1，`beta` 期间 API 可能继续调整（会尽量保持方法签名稳定）；写操作目前没有 dry-run，调用即生效。
- `recipesFor` 不含 L2 过滤，要过滤后的结果请用 `recipesForFiltered`。
- `findRecipeFor` 对工作盆 / 搅拌杖 / 灌注枪 / 指杆 / 风箱返回 `null`——它们光看一件物品判不出配方，请用对应的专门入口。
- 风箱没有 L2（它没有 L1 候选，靠 `fan_type` 匹配）。
- 女仆兼容需要车万女仆（TLM）；未安装时相关代码整体不加载，不影响其它功能。

## 内部

- 源码文件合并重构：同类的小类收进宿主文件的嵌套类，`.java` 文件 **131 → 101**（131 里含本版新增的 8 个文件，对比 0.6.1 实际净减 22 个）。行为与注册逻辑不变。
- 删除死代码 `MaidCutHelper`。
- 删除 resources 下重复错位的 `data/create_hand_made/data/create_hand_made/`（namespace 写成 `data`，从未被加载过）。

---

完整变更见仓库根目录 [`CHANGELOG.md`](https://github.com/Mr-HorseDuck/create-hand-made/blob/1.20.1/CHANGELOG.md)。
遇到问题请附 `logs/latest.log` 与模组列表反馈。
