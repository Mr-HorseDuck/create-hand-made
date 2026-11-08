# Changelog

本模组所有值得记录的变更都写在这里。
版本号规则 `x.y.z-beta`；`beta` 期间对外 API 仍可能调整，但会尽量保持方法签名稳定。

---

## 0.6.2-beta — 2026-11-08

基准：`0.6.1-beta`（仓库 `1.20.1` 分支的上一个 release）。这一版以「把遗留问题修干净、把女仆兼容补完整」为主，没有推倒既有玩法。

> 说明：仓库里 `gradle.properties` 的 `mod_version` 在 0.6.1 发布时漏改了，代码是 0.6.1 但版本号仍写着 0.6.0，本次一并修正。

### 新增

- **女仆：灌注枪行为启用**（行为表第 9 项，优先级 7）。女仆手持灌注枪、且没有被指杆指定目标时，会自己找附近的置物台注液、工作盆存液；玩家用指杆指定过抽 / 注 / 过剩目标时自动让位给优先级 6 的液体搬运，避免两边抢同一个容器。可用 `maid.passive_infusion_gun = false` 关掉（默认开）。附近找不到目标时先扣冷却再扫描，不会每 tick 全量扫方块。
- **女仆：好感度加成真正生效**。冷却按好感度等级 ×0.925 / ×0.85 / ×0.775（即 −7.5% / −15% / −22.5%），另有 5% / 10% / 15% 概率不消耗工具耐久。此前 `adjustCooldown` 写好了却没有任何调用点，「好感度缩短冷却」实际一直没生效。
- **女仆：首次使用气泡**。女仆第一次拿某件手搓工具时冒一条提示，每个女仆每件工具只提示一次。
- **配置同步**：主配置的 14 个时间参数由服务端统一下发给客户端（见「变更」里的协议版本），多人服里客户端看到的就是服务端实际生效的值；退出世界时清空镜像。
- **对外 Java API 扩充**：`CreateHandMadeApi` 从 13 个方法扩到 31 个（新增 18 个，其中 8 个是带副作用的写操作：`pressBasin` / `pressTransported` / `grindMortarOnce` / `injectItems` / `storeFluid` / `extractFluidTick` / `applyBellows` / `damageTool`），并补上 `recipesForFiltered`（走 L2）、`findRecipeFor`、`recipeIdOf` 等；`API_VERSION` 仍为 1。
- **文档**：新增 `docs/RECIPE_API.md`（三层配方与数据包 / KubeJS 写法）、`docs/TOOL_REFERENCE.md`（每工具操作与配方来源速查），并重写 `docs/api.md`。

### 修复

- **安全**：女仆网络包补服务端校验（必须手持指杆 + 女仆属于发送者 + 距离 ≤ 64），此前伪造包可给任意玩家的女仆写标记；指杆标记同样补「手持指杆 + 够得着」校验。
- **手锯砍树**：改为带玩家上下文调用，保护类插件、统计、附魔掉落正常生效；耐久仍只扣 1 点。
- **灌注枪**：放置流体先后走 `mayInteract` 与流体放置事件（事件返回空气即中止）；吸取流体也做权限判断。
- **耐久**：白磨、整批零命中、空挥等无效操作不再扣工具耐久。
- **客户端状态**：退出世界时清空服务端配置镜像与搅拌追踪，避免从多人服退回单人后沿用上个服务端的参数。
- **女仆配置界面**：冲压锤冷却滑块误读成「指杆部署」值的问题。
- **`/chm reload`**：原来从 `.minecraft/config` 重载，而 SERVER 配置实际在存档的 `serverconfig/`，导致重载反而把内存里的值刷成默认；已改为正确路径。
- **配置读取时机**：初始化阶段读取尚未加载的 SERVER 配置，在开发环境会抛异常，现改为先判加载状态。
- **L2 过滤口径统一**：过滤表此前只作用于 L1，现在同时作用于 L3 独占配方（8 处工具查询 + JEI 分类页），禁用一条配方不再出现「工具里禁了、JEI 里还在」的情况。

### 变更

- **主配置改为 SERVER 类型**：文件位于存档的 `serverconfig/create_hand_made-server.toml`，14 个时间参数由服务端同步给客户端；单人可改、多人只读。
- **女仆配置拆分为两份**：运行时数值走 SERVER 配置（`serverconfig/create_hand_made-maid-server.toml`），`blocked_mods` 单独留在 `config/create_hand_made-maid.toml`（它要在模组初始化阶段读取，改动需重启）。女仆配置界面与主配置一致，非单人环境只读。
- **网络协议版本升到 2**：客户端与服务端需同一版本，否则无法连接。

### 内部

- 源码文件合并重构，`.java` 文件 `131 → 101`（131 含本版新增的 8 个文件；对比 0.6.1 的 123 个，实际净减 22）。同类的小类收进宿主文件的嵌套类，行为与注册逻辑不变。
- 删除死代码 `MaidCutHelper`（62 行，重构后已无引用）。
- 删除 resources 下重复错位的 `data/create_hand_made/data/create_hand_made/` 目录（12 个文件）。它的 namespace 实际是 `data`，从未被游戏加载过，正确路径上的同名文件一直存在。

### 升级注意

- 如果你在 `config/create_hand_made-maid.toml` 里调过冷却、搜索半径、粒子等级等，这些键在 0.6.2 之后不再生效：请进单人世界一次，改存档里的 `serverconfig/create_hand_made-maid-server.toml`（或直接在游戏内的模组配置界面改）。
- 旧版的客户端与服务端不能混用（协议版本已升）。

---

## 0.6.1-beta — 2026-11-01

补上本文件建立前就已经发布过的版本，方便对照。

### 修复

- 灌注枪、研钵内容物的 `equals` / `hashCode` 改为值语义，修复带内容物的工具被物流请求打包时静默失败。
- 手锯切回默认配方不再写 `NBT_RECIPE_INDEX = 0`，存量手锯进背包一 tick 自动自愈。
- 碾钵起手判断改用 `crusherMortarWindupTicks`，不再误读研钵配置。
- 研钵 / 碾钵的音效与粒子逻辑抽到 `MortarGrindEffects`，消除复制粘贴错位。
- 修复 Issues #2。

### 新增

- `CreateHandMadeApi` 稳定 API 入口（`API_VERSION = 1`，13 个方法）与 `docs/api.md`。
- 开放 6 个工具类的 `public static` 方法（`PointerItem.executeDepotProcess`、`PressHammerItem` 5 个）。
- 手锯 tooltip 显示当前切削配方序号。

### 变更

- 灌注枪液体层改由 BEWLR 渲染，仓储管理员的 3D 物品网格正常显示。
- 删除 `GuiGraphicsMixin`，mixin 列表只剩 `BasinBlockEntityMixin`。

---
