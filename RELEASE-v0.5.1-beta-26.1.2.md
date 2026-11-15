# Create: Hand Made Fly 0.5.1-beta（26.1.2 / Fabric）

> 环境：Minecraft 26.1.2 · Fabric Loader 0.20.1 · Fabric API 0.155.3+26.1.2 · Create Fly 6.0.9-4 · Java 25
> 可选：JEI 29.47.0.111（`jei-26.1.2-fabric`）

**这一版给 26.1.2 线补上了 JEI 支持**：JEI 里现在能看到 14 个配方类别，每个类别对应一件手工工具；
配方页的 UID 与 1.21.1 / 1.20.1 线**完全一致**，所以从其它版本迁移过来的收藏、书签不会失效。
游戏内玩法与配方**没有任何改动**，不装 JEI 时模组行为与 0.5.0 相同。

基于 [机械动力：手搓学（NeoForge 1.21.1）](https://github.com/Mr-HorseDuck/create-hand-made/tree/main) 的配方体系移植。

---

## 🎨 JEI：14 个配方类别

| 类别 | 内容 |
|---|---|
| 手锯 · 切削 | Create 切削配方 + 本模组 L3 切削 |
| 手锯 · 切石 | 原版切石 + 本模组 L3 切石（5×3 槽，超过 15 条并入同槽） |
| 冲压锤 · 置物台 | Create 冲压配方 + L3 冲压 |
| 冲压锤 · 工作盆 | Create 打包配方 + L3 工作盆配方 |
| 研钵 · 研磨 | Create 研磨配方 + L3 碾磨（研钵支） |
| 碾钵 · 粉碎 | Create 粉碎配方（自动去掉被研磨覆盖的条目）+ L3 |
| 搅拌杖 · 混合 | Create 混合配方 + L3 工作盆配方 |
| 搅拌杖 · 自动酿造 | Create 酿造配方 |
| 指杆 · 物品应用 | Create 部署 / 手动应用配方 + L3 |
| 风箱 · 熔炼 / 烟熏 | 原版熔炼→高炉→烟熏三级去重 + L3 风箱配方（按介质类型分流） |
| 风箱 · 缠魂 / 洗涤 | Create 缠魂 / 洗涤配方 + L3 |
| 灌注枪 · 注液 | Create 注液配方 + L3 灌注 |

- 点亮「手锯 / 研钵 / 搅拌杖 / 风箱 / 灌注枪 / 指杆 / 冲压锤」等物品可以直接反查对应类别。
- 类别里保留 Create 原来的槽位布局，只把「机械装置」的 3D 装饰换成对应手工工具的图标。

## 🔧 构建链同步现代化

如果你要自己编译这一版，注意构建要求变了（对玩家无影响）：

- Gradle wrapper 钉 **9.8.2**（原来 9.2.1 撑不起 Loom 1.19.x）；
- Loom 从 `1.19-SNAPSHOT` 改为钉死 **1.19.4**（快照会随上游漂移）；
- 新增 `gradle/gradle-daemon-jvm.properties` 钉 Gradle 守护进程用 **JDK 25** —— Loom 1.19.x 的插件字节码要求 JVM 25，
  只设 `java.toolchain` 不够，用 JAVA_HOME 里的 JDK 21 会在配置阶段直接失败；
- `settings.gradle` 加 foojay resolver，缺 JDK 25 时自动下载（换机 / CI 不用手配 JAVA_HOME）；
- `fabric.mod.json` 的 `depends` 补上 `fabric-api`、`fabricloader` 下限提到 0.20.1；
- JEI 依赖指向**完整 mod jar**（`jei-26.1.2-fabric`）且仅 `compileOnly` —— `-fabric-api` 薄壳里没有
  `mezz.jei.common.Internal`，而客户端配方快照只能用那个入口取。

## ⚠️ 已知限制

- **两个类别没做**：「自动摆放」（工作台 4/9 合 1）与「自动无序合成」。26.1 的 `BasinRecipe` 只剩
  `matchCraftingRecipe` / `applyCraftingRecipe`（运行期 `BasinInput` 入口），**没有** `convertShaped` /
  `convertShapeless`，做不出可显示的配方对象。游戏内这两条加工路径**本身不受影响**，只是 JEI 里查不到对应页。
- **灌注枪的「转换配方」**（上游 `SpoutCategory.consumeRecipes`）26.1 已无该入口，未做。
- **JEI 页面里的配方依赖客户端配方快照**：26.1 起客户端没有 `RecipeManager`，JEI 用的是它自己同步的
  `RecipeMap`。若你发现某个类别是空的，先确认装的是 Fabric 版 JEI（`jei-26.1.2-fabric`），
  再把 `com.alben.createhandmade` 的日志级别调成 debug 看 `JeiRecipeSource` 的输出。
- **开发环境不会自动加载 JEI**：Loom 1.19.4 没有 `modLocalRuntime`，想 `runClient` 里目视 JEI
  需要把 JEI 的发布 jar 丢进 `run/mods/`。
- `libs/create-fly-26.1.2-6.0.9-4.jar`（前置 Create Fly）在仓库里，自行编译无需另外下载。

## 📦 安装

1. Minecraft 26.1.2 + Fabric Loader 0.20.1 + Fabric API；
2. 装 **Create Fly 6.0.9-4**（mod id 仍是 `create`）；
3. 把 `create_hand_made-0.5.1-beta.jar` 放进 `mods/`；
4. 想要配方查询再装 **JEI 29.47.0.111（Fabric 版）**。不装也能正常进游戏。

**单人与多人一致**：JEI 是纯客户端模组，服务端不装也能用；配方页内容以客户端收到的配方为准。

## ✅ 验证状态

- 构建：`gradlew build` 通过（Gradle 9.8.2 + Loom 1.19.4 + JDK 25），产物含 24 个 JEI 相关 class；
- 产物检查：jar 内**不含** JEI 与 Create Fly 的类（都是 `compileOnly` / 本地前置），`fabric.mod.json` 含 `jei_mod_plugin`；
- 静态检查：类别数 = `addRecipes` 数 = 催化剂数 = 14；16 个配方类型 UID 与其它版本分支逐字一致；
  未装 JEI 时主入口对 `compat.jei` 零引用；
- **尚未做**：人工目视（槽位是否重叠、装饰图标位置、空类别、反查）与「卸掉 JEI 启动一次」。
  如遇到显示问题请附 `logs/latest.log` + 模组列表。

---

# English

**Environment** Minecraft 26.1.2 · Fabric Loader 0.20.1 · Fabric API 0.155.3+26.1.2 · Create Fly 6.0.9-4 · Java 25
**Optional** JEI 29.47.0.111 (`jei-26.1.2-fabric`)

Adds **JEI support** to the 26.1.2 line: 14 recipe categories, one per manual tool. Recipe-type UIDs match the
1.21.1 / 1.20.1 lines exactly, so bookmarks carried over from other branches stay valid.
Gameplay and recipes are unchanged; without JEI the mod behaves exactly like 0.5.0.

**Not included**: the "auto square" and "auto shapeless" categories — 26.1's `BasinRecipe` has no
`convertShaped` / `convertShapeless` to turn a crafting-table recipe into a displayable basin recipe.
In-game crafting is unaffected; only the JEI page is missing. The infusion gun's conversion recipes are
likewise not shown (the upstream `consumeRecipes` entry point no longer exists).

**Build** (only relevant if you compile it yourself): Gradle wrapper 9.8.2, Loom pinned to 1.19.4,
Gradle daemon JVM pinned to 25 via `gradle/gradle-daemon-jvm.properties`, foojay toolchain resolver,
`fabric-api` declared in `depends`, and JEI referenced as `compileOnly` against the **full** mod jar
(the `-fabric-api` shell lacks `mezz.jei.common.Internal`, which the client recipe snapshot needs).

**Verified**: build passes, jar contains no JEI / Create Fly classes, 14 categories wired in three places,
all 16 recipe-type UIDs unchanged. Manual in-game inspection of the category layouts is still pending.
