# Create: Hand Made Fly 0.5.1-beta（26.2 / Fabric）

> 环境：Minecraft 26.2 · Fabric Loader 0.20.1 · Fabric API 0.161.0+26.2 · Create Fly 26.2-6.0.9-2 · Java 25
> 可选：JEI 30.48.0.244（`jei-26.2-fabric`）

**26.2 线的首个发布。** 内容与 [26.1.2 线的 0.5.1-beta](https://github.com/Mr-HorseDuck/create-hand-made/releases/tag/v0.5.1-beta-26.1.2) 完全一致：
8 件手工工具 + 14 个 JEI 配方类别，玩法、配方、语言文件都没有改动。这一版做的只是跟着 26.2 的 API 走一遍。

基于 [机械动力：手搓学（NeoForge 1.21.1）](https://github.com/Mr-HorseDuck/create-hand-made/tree/main) 的配方体系移植。

---

## 🧰 内容

| 工具 | 功能 |
|---|---|
| 冲压锤 `press_hammer` | 长按冲压工作盆 / 置物台 / 传送带上的物品；蓄力暴击 + 击退 |
| 研钵 `mortar` | 副手供料，长按研磨 |
| 碾钵 `crusher_mortar` | 优先粉碎，无配方退回研磨 |
| 指杆 `pointer` | 与置物台 / 传送带上的物品做装配；潜行高亮方块 |
| 搅拌杖 `stirring_staff` | 长按搅拌工作盆（混合 / 打包 / 无序合成 / 酿造） |
| 灌注枪 `infusion_gun` | 抽液 / 注液 / 存液 / 放流体源 / 吸走整格流体，1000 mB 容量 |
| 风箱 `bellows` | 副手介质鼓风（熔炼 / 烟熏 / 缠魂 / 洗涤）+ 气流推生物 |
| 手锯 `hand_saw` | 副手供料连续切削、潜行整树砍伐、去皮 / 刮铜 / 去蜡 |

JEI 里能看到 14 个配方类别（手锯切削 / 切石、冲压锤置物台 / 工作盆、研钵研磨、碾钵粉碎、
搅拌杖混合 / 自动酿造、指杆应用、风箱熔炼 / 烟熏 / 缠魂 / 洗涤、灌注枪注液），
配方页 UID 与 1.21.1 / 1.20.1 线**完全一致**，收藏与书签不会失效。

## 🔧 26.1.2 → 26.2 改了什么

业务代码只动了四处，其余全是版本号：

- **实体类型常量搬家**：`EntityType.SNOW_GOLEM` / `.BLAZE` → 新类 **`EntityTypes`**（风箱洗涤判定用到）。
- **击退必须带伤害来源**：`knockback(power, x, z)` 在 26.2 已被移除，改为
  `knockback(power, x, z, DamageSource, float)`。冲压锤蓄力击退的强度**不变**——
  方法内部只把 power 乘 `1 - 击退抗性`，新增的那个 float 不参与。
- **手中渲染方法改名**：`ItemInHandRenderer.renderArmWithItem` → **`submitArmWithItem`**（参数表一致）。
  这一处**编译期发现不了**，不改就会在进游戏时崩，属于本次升级最容易漏的一条。
- **前置版本下限放宽**：Create Fly 26.2 线的 build 号从 1 重排，版本是 `6.0.9-2`，
  数值上比 26.1.2 线的 `6.0.9-4` **更小**。沿用旧下限会让 Fabric Loader 直接拒绝加载，
  所以 `depends.create` 改成 `>=6.0.9-2`。

**JEI 从 29.x 跳到 30.x，但插件代码一行没改**：`Internal.getClientSyncedRecipes()`、
`IRecipeCategory` 的 6 个抽象方法、`IRecipeType.create(Identifier, Class)` 全部保留，
Create Fly 26.2 的 JEI 兼容层签名也与 26.1.2 逐字一致。

## ⚠️ 已知限制

- **两个类别没做**：「自动摆放」（工作台 4/9 合 1）与「自动无序合成」。26.2 的 `BasinRecipe` 同样没有
  `convertShaped` / `convertShapeless`，做不出可显示的配方对象。游戏内这两条加工路径**本身不受影响**，
  只是 JEI 里查不到对应页。
- **灌注枪的「转换配方」**（上游 `SpoutCategory.consumeRecipes`）在新版 Create 里已无该入口，未做。
- **JEI 页面依赖客户端配方快照**：客户端没有 `RecipeManager`，JEI 用的是它自己同步的 `RecipeMap`。
  若发现某个类别是空的，先确认装的是 Fabric 版 JEI（`jei-26.2-fabric`），
  再把 `com.alben.createhandmade` 日志级别调到 debug 看 `JeiRecipeSource` 的输出。
- **开发环境不会自动加载 JEI**：Loom 1.19.4 没有 `modLocalRuntime`，想 `runClient` 里目视 JEI
  需要把 JEI 发布 jar 丢进 `run/mods/`。
- `libs/create-fly-26.2-6.0.9-2.jar`（前置 Create Fly）在仓库里，自行编译无需另外下载。

## 📦 安装

1. Minecraft **26.2** + Fabric Loader 0.20.1 + Fabric API 0.161.0+26.2；
2. 装 **Create Fly 26.2-6.0.9-2**（mod id 仍是 `create`）；
3. 把 `create_hand_made-0.5.1-beta.jar` 放进 `mods/`；
4. 想要配方查询再装 **JEI 30.48.0.244（Fabric 版）**。不装也能正常进游戏。

**别和 26.1.2 线的同名 jar 混用**：两者都叫 `create_hand_made-0.5.1-beta.jar`，但 Minecraft 版本不同。
按你所在的服务端 / 客户端版本择一安装即可；装错会在加载期被 `minecraft` 依赖区间拦下。

**单人与多人一致**：JEI 是纯客户端模组，服务端不装也能用。

## ✅ 验证状态

- 构建：`gradlew build` 通过（Gradle 9.8.2 + Loom 1.19.4 + JDK 25），产物含 24 个 JEI 相关 class；
- 产物检查：jar 内**不含** JEI 与 Create Fly 的类；`fabric.mod.json` 的 `minecraft` 为 `~26.2`、
  `create` 为 `>=6.0.9-2`、含 `jei_mod_plugin`；
- 两个 mixin 的目标已在 26.2 的字节码里逐一核对存在：
  `ItemInHandRenderer.submitArmWithItem` 描述符匹配、`BasinBlockEntity.lazyTick` 内仍有
  `setAreFluidsMoving` 调用；
- **尚未做**：进游戏目视（物品模型是否正常、14 个类别的槽位与装饰、两个 mixin 的实际效果、
  卸掉 JEI 启动一次）。26.2 若改动了物品模型 JSON 格式，可能出现紫黑方块——这一点静态查不出来。
  如遇到问题请附 `logs/latest.log` + 模组列表。

---

# English

**Environment** Minecraft 26.2 · Fabric Loader 0.20.1 · Fabric API 0.161.0+26.2 · Create Fly 26.2-6.0.9-2 · Java 25
**Optional** JEI 30.48.0.244 (`jei-26.2-fabric`)

First release for the 26.2 line. Content is identical to the
[26.1.2 line's 0.5.1-beta](https://github.com/Mr-HorseDuck/create-hand-made/releases/tag/v0.5.1-beta-26.1.2):
8 manual tools plus 14 JEI recipe categories. Gameplay, recipes and language files are unchanged —
this release only follows the 26.2 API.

**What changed for 26.2** (four source-level edits, everything else is version bumps):

- `EntityType.SNOW_GOLEM` / `.BLAZE` moved to the new `EntityTypes` class
- `knockback` now requires a `DamageSource`; the added float does not scale power
  (power is still multiplied by `1 - knockback resistance`), so the press hammer's charge
  knockback is unchanged
- `ItemInHandRenderer.renderArmWithItem` was renamed to `submitArmWithItem` with an identical
  parameter list — invisible at compile time, and a `required: true` mixin would crash at launch
- `depends.create` relaxed to `>=6.0.9-2`: the 26.2 line restarts its build numbers at 1, so
  `6.0.9-2` sorts *below* the 26.1.2 line's `6.0.9-4` and the old bound would make the loader refuse to start

**JEI went from 29.x to 30.x with zero plugin changes**: `Internal.getClientSyncedRecipes()`, the six
abstract `IRecipeCategory` methods and `IRecipeType.create(Identifier, Class)` are all still there,
and Create Fly 26.2's JEI compat layer has the same signatures as 26.1.2.

**Not included**: the "auto square" and "auto shapeless" categories (26.2's `BasinRecipe` has no
`convertShaped` / `convertShapeless`); in-game crafting is unaffected. The infusion gun's conversion
recipes are likewise not shown.

**Verified**: build passes; jar contains no JEI / Create Fly classes; both mixin targets confirmed
present in the 26.2 bytecode. In-game visual inspection (item models, category layouts, mixin
behaviour, launching without JEI) is still pending.