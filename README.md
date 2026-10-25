# Create: Hand Made — Fabric 26.1.2 移植版

把 [Mr-HorseDuck/create-hand-made](https://github.com/Mr-HorseDuck/create-hand-made)（NeoForge 1.21.1）
移植到 **Minecraft 26.1.2 + Fabric**，Create 前置由 **Create（Forge/NeoForge 版）** 换成
**Create Fly**（`com.zurrtum.create`，mod id 仍是 `create`）。

---

## 1. 目标环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 26.1.2 |
| 模组加载器 | Fabric Loader 0.20.1 |
| Fabric API | 0.155.3+26.1.2 |
| Create 前置 | Create Fly 26.1.2-6.0.9-4（mod id `create`，版本 `6.0.9-4`） |
| Java | 25 |
| Gradle | 9.8.2（Loom 1.19.3 要求 ≥ 9.8.0） |
| Fabric Loom | 1.19-SNAPSHOT → 解析为 1.19.3 |

26.1 起 Minecraft 不再混淆、使用官方 Mojang 映射，因此 Loom 配置里没有 `mappings` 段，
依赖统一用 `implementation` / `compileOnly`。

---

## 2. 构建

```powershell
# 1) 把 Create Fly 的 jar 放到 fabric/libs/ 下
#    create-fly-26.1.2-6.0.9-4.jar
# 2) 编译
gradle -p fabric build
# 3) 产物
#    fabric/build/libs/create_hand_made-0.5.0-beta.jar
```

`build.gradle` 通过 `implementation files("libs/create-fly-${create_fly_version}.jar")` 引用前置，
所以**必须先手动放好 jar**。JEI 是 `compileOnly`，不参与运行时。

---

## 3. 已移植的内容

### 3.1 八个工具（行为完整）

| 工具 | 功能 |
| --- | --- |
| 冲压锤 `press_hammer` | 长按冲压工作盆 / 置物台 / 传送带上的物品；蓄力暴击 + 击退 |
| 研钵 `mortar` | 副手供料，长按研磨（milling） |
| 碾钵 `crusher_mortar` | 优先粉碎（crushing），无配方退回研磨 |
| 指杆 `pointer` | 与置物台 / 传送带上的物品做装配（deploying）；潜行高亮方块 |
| 搅拌杖 `stirring_staff` | 长按搅拌工作盆（mixing / compacting / 无序合成 / 酿造） |
| 灌注枪 `infusion_gun` | 抽液 / 注液 / 存液 / 放流体源 / 吸走整格流体，1000 mB 容量 |
| 风箱 `bellows` | 副手介质鼓风（熔炼 / 烟熏 / 缠魂 / 洗涤）+ 气流推生物 |
| 手锯 `hand_saw` | 副手供料连续切削、潜行整树砍伐、去皮 / 刮铜 / 去蜡 |

### 3.2 配方体系（L1 / L2 / L3 三层）

- **L1 原版 Create 配方**：直接复用 Create Fly 的 `BasinRecipe`、`MillingRecipe`、
  `CrushingRecipe`、`PressingRecipe`、`CuttingRecipe`、`FillingRecipe`、
  `ItemApplicationRecipe`、`PotionRecipe` 等。
- **L2 风箱介质表**：`data/<ns>/bellows_media/*.json` 自定义 + 由 Create 的
  `FAN_PROCESSING_CATALYSTS_*` 方块/流体标签自动发现。文件格式：

  ```json
  {
    "type": "create:blasting",
    "ingredients": ["minecraft:lava_bucket", "#c:buckets/lava"]
  }
  ```

  `ingredients` 里每项是**字符串选择器**：`"ns:id"` 表示单个物品，`"#ns:tag"` 表示物品标签。
  这里没有用 `Ingredient.CODEC`，因为数据包重载走的是无注册表的 `JsonOps`，
  而 `Ingredient.CODEC` 内部的 `HolderSetCodec` 需要 registry 才能解析 id。
- **L3 独占配方**：本模组自己的 `RecipeType` / `RecipeSerializer`，Create 的机器看不到：
  - `create_hand_made:tool_recipe`（六个家族共用，按 `tool` 字段分派）
  - `create_hand_made:stonecutting_recipe`（手锯切石）
  - `create_hand_made:bellows_recipe`（风箱）

  对应的 record 类：`HandMadeBasinRecipe`、`HandMadeMillingRecipe`、
  `HandMadeCrushingRecipe`、`HandMadePressingRecipe`、`HandMadeCuttingRecipe`、
  `HandMadeFillingRecipe`、`HandMadeApplicationRecipe`、`HandMadeBellowsRecipe`、
  `HandMadeStonecuttingRecipe`。

### 3.3 配置

`ServerConfig` 用自建 JSON 实现（Fabric 没有 NeoForge 的 `ModConfigSpec`）：

```
config/create_hand_made-server.json
```

保留原来的 `.get()` 调用形式，路径与 NeoForge 版一致（`tools.hand_saw.windup_ticks` 等），
带 min/max 钳制。首次启动自动写出默认值。

### 3.4 研钵 / 碾钵的「钵 + 杵 + 动画」

原版用 Create 的 `CustomRenderedItemModelRenderer` 把「钵」「杵」当两个独立模型分别渲染。
Fabric 26.1 没有那套 API，改用 26.1 自己的扩展点：

| 层 | 内容 | 实现 |
| --- | --- | --- |
| 1 | 钵 | `assets/create_hand_made/models/item/mortar.json` 的 quads |
| 2 | 碗里的内容物 | 现搭一个 `ItemStackRenderState`（`FIXED` 变换），交给同一个 `SubmitNodeCollector` |
| 3 | 杵 | `models/item/mortar_pestle.json` 的 quads，独立位移 / 旋转 |

- 物品定义 `assets/create_hand_made/items/mortar.json` 用自定义类型
  `create_hand_made:mortar`，JSON 里给出 `bowl`（钵）与 `pestle`（杵）两个模型 id，
  由 `client/MortarItemModel.java` 的 `Unbaked` 解析；客户端初始化时通过
  `ItemModels.ID_MAPPER.put(...)` 注册这个类型。
- 动画只在**第一人称**播放：起手把 `item/generated` 侧对屏幕的姿态转正并前伸，
  之后杵按「抬 → 砸 → 贴合」循环、钵反向下沉做对撞。相位与音效共用
  `StickyUseSession.processingElapsedTicks`，所以「听到砸声」=「看到砸到底」。
- GUI / 第三人称 / 掉落物是静态合成（钵 → 内容物 → 杵），图标不会动。
- 视觉参数（基准位置、振幅、缩放、旋转、深度偏移）与原 NeoForge 版一一对应，
  写在 `MortarItemModel` 的 `MORTAR_PARAMS` / `CRUSHER_PARAMS` 里；
  时间参数（起手时长、捶捣周期）仍读 `ServerConfig`。
- **坐标系必须做一次共轭换算**（这里踩过坑，改参数前务必先读）：

  ```
  旧 NeoForge：Display · Windup · Part · T(-0.5)      // 自定义渲染器在 T(-0.5) 之前被调用
  新 26.1    ：Display · T(-0.5) · localTransform     // ItemTransform#apply 末尾就是 T(-0.5)
  ⇒ localTransform = T(+0.5) · Windup · Part · T(-0.5)
  ```

  直接把 `Windup` 当 `localTransform` 用，旋转支点会从「贴图中心」跑到「贴图左下角」，
  整体还会多偏一个 `T(-0.5)`。实测症状：钵比目标位置偏右约 7 像素（看起来「还在右手」）、
  杵因为绕角点转 45° 而整体偏左约 8 像素。代码里 `base` 以 `translate(0.5, 0.5, 0.5)` 开头、
  钵/杵两个图层末尾再 `translate(-0.5, -0.5, -0.5)` 就是为了抵掉这一层。
  **内容物图层例外**：它由 `ContentsRenderer` 现搭 `ItemStackRenderState`，
  里面的 `FIXED` 变换自带 `T(-0.5)`，所以那一层**不要**补尾部的 `T(-0.5)`。
- `baseX = -0.90` 不是拍脑袋的值：26.1 第一人称手臂把物品放在 `(±0.56, -0.52, -0.72)`，
  `item/generated` 的 firstperson 变换再给 `translation.x = 1.13`（×0.0625 = 0.0706）、
  `scale = 0.68`，所以水平居中需要 `-(0.56 + 0.0706) / 0.68 ≈ -0.93`；
  `-0.90` 只剩约 0.3 像素偏差。

### 3.5 手锯的 HUD 与第一人称动画

- **HUD 配方预览**：26.1 客户端拿不到全量配方（`Recipe#getResultItem()` 也已删除），
  所以由服务端在「主手手锯 + 副手供料」的签名变化时推 S2C 包
  `HandSawRecipesPacket(input, outputs)`；客户端存进 `item/HandSawRecipeCache`
  （按「物品 + 组件」匹配），`client/HandSawHudRenderer` 通过 Fabric
  `HudElementRegistry.addLast(...)` 在屏幕上方 1/3 处绘制配方格：
  18px 格子 + 4px 间距、最多 9 列 × 4 行，超出时用 `+N` 滑动窗口，当前配方描金边。
- **客户端判定策略**：`HandSawItem.hasCuttingRecipes()` 在客户端先查推送缓存，
  缓存里还没有这件原料时**乐观放行**（服务端才是权威），与搅拌杖「客户端只做结构判定」
  的既有范式一致。潜行右键切换配方、`use()/useOn()/onUseTick()` 全部走这条路径。
- **切削动画**：`client/HandSawItemModel`（物品定义类型 `create_hand_made:hand_saw`）
  把锯子按 `SAW_TARGET` 前伸并做小幅摆动；副手原料用一个
  `FixedItemRenderer`（`SpecialModelRenderer` + 子 `ItemStackRenderState`）预览。
  相位与音效共用 `StickyUseSession`，手别恒为 `MAIN_HAND`。
- **必须取消副手原版渲染**：26.1 里副手物品由 `ItemInHandRenderer.renderArmWithItem`
  绘制，方法是 private，Fabric 没有等价事件 → `mixin/client/ItemInHandRendererMixin`
  在 HEAD 处按同一条件 `cancel()`，否则预览层会和原版副手物品叠成两份。
- 坐标系仍按 3.4 的共轭公式；副手预览层是 `FIXED` 变换的子图层，
  自带 `T(-0.5)`，所以那一层**不补**尾部 `T(-0.5)`。
- 潜行左键整树砍伐、去皮 / 刮铜 / 去蜡仍走原逻辑（虚拟铁斧 + `AttackBlockCallback`）。

### 3.6 风箱 / 搅拌杖的第一人称动画

两者沿用 3.5 的同一套范式（自定义 `ItemModel` 类型 + 共轭坐标换算 + 必要时 mixin 取消副手渲染）。

**风箱 `create_hand_made:bellows`（`client/BellowsItemModel`）**

| 层 | 内容 | 实现 |
| --- | --- | --- |
| 1 | 风箱本体 | `models/item/bellows.json` 的 quads，局部变换 = 单位阵 |
| 2 | 副手介质预览 | `FixedItemRenderer` 子图层（`FIXED` 变换），画在本体之上 |

- 蓄力进度 = `getTicksUsingItem() / tools.bellows.charge_ticks`（运行时读配置），
  介质从画面外的 `START_*`（缩放 1.0）smoothstep 飞到 `TARGET_*`（缩放 0.2），
  朝向为 `YP 90° · XP 45°`（都乘 `modifier`），左手末尾再追加 180°。
- 只在「正在使用这一手的风箱 + 另一只手放着合法介质」时显示预览；
  `BellowsMediaRegistry.resolve()` 为空就不画。
- 介质那只手的原版渲染由 `ItemInHandRendererMixin` 取消（与 3.5 手锯共用同一个注入点），
  否则会看到两份介质。

**搅拌杖 `create_hand_made:stirring_staff`（`client/StirringStaffItemModel`）**

- 只有本体一层，靠改写 `localTransform` 做动画：
  - **起手**：位移插值到正前方加工位（`POS_X/POS_Y/POS_Z`）+ 6 次旋转把杖头从贴图对角线方向
    转成「斜指屏幕前方」；
  - **加工**：叠加一个水平小圆（`STIR_CIRCLE_RADIUS`），相位取「会话加工 tick」，
    周期 = `processing_ticks` 换算成「整数圈 / 件」（`STIR_CIRCLE_TARGET_TICKS = 20t ≈ 1 圈/秒`），
    每件边界相位回 0 不跳变，`STIR_CIRCLE_FADE_IN_TICKS` 做渐入。
- 镜像规律（实机定稿）：基准 `POS_X/POS_Y` 不乘 `modifier`、`POS_Z` 乘；
  圆偏移 X 乘 `−modifier`、Z 乘 `modifier`；两次 YP 与一次 XP 乘 `modifier`，两次 ZP 不乘。
- 时间参数（起手 / 单件加工时长）与 `StirringStaffItem` 共用 `ServerConfig` 与 `StickyUseSession`。

**灌注枪**：旧渲染器的旋转是注释掉的（只有 `pushPose` → 画原模型 → `popPose`），
等价于普通 JSON 模型，所以不需要自定义 `ItemModel`，现状与上游一致。

---

## 4. NeoForge → Fabric 的等价替换

| NeoForge | Fabric |
| --- | --- |
| `PlayerInteractEvent.RightClickBlock` | `UseBlockCallback`（命中时在回调内手动调用 `use()` / `startUsingItem()`，返回 `CONSUME` 短路整个 `useItemOn`） |
| `PlayerInteractEvent.LeftClickBlock` | `AttackBlockCallback`（只覆盖 START） |
| `BlockEvent.BreakEvent` | `PlayerBlockBreakEvents.AFTER` |
| `ServerTickEvent.Post` | `ServerTickEvents.END_SERVER_TICK` |
| `PlayerEvent.PlayerLoggedOutEvent` | `ServerPlayConnectionEvents.DISCONNECT` |
| `AttackEntityEvent` 扣耐久 | `DataComponents.WEAPON` |
| `CriticalHitEvent` / `LivingKnockBackEvent` | 在 `releaseUsing()` 命中实体分支内直接补伤害 + `knockback()` |
| `Capabilities.FluidHandler.BLOCK` | `FluidHelper.getFluidInventory(Level, BlockPos, Direction)` → `FluidInventory` |
| `ItemAbilities` / `getToolModifiedState` | 委托虚拟铁斧 `Items.IRON_AXE.useOn(...)` |
| `ModConfigSpec`（SERVER） | 自建 JSON（见上） |

---

## 5. 26.1 / Create Fly 带来的 API 变化（移植时踩过的坑）

- `Level.isClientSide` 字段 → `isClientSide()`；`Level.random` → `getRandom()`。
- `Item.use()` 返回 `InteractionResult`；`getUseAnimation()` 返回 `ItemUseAnimation`；
  `releaseUsing()` 返回 `boolean`；`getEnchantmentValue()` 移除 → `Item.Properties.enchantable(15)`。
- `InteractionResult` 无 `sidedSuccess`：客户端用 `SUCCESS`、服务端用 `SUCCESS_SERVER`；
  `CONSUME` 表示不挥手。
- `appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`。
- `ItemStack.getCraftingRemainingItem()` 移除 → `DataComponents.USE_REMAINDER`。
- `Recipe` 只有 `assemble(T)`（无 `registryAccess`、无 `getResultItem`）。
- **配方只在服务端可用**：26.1 不再把全量配方同步给客户端，客户端只拿 property set /
  切石配方。因此 `HandMadeRecipePool` 走 `level.getServer().getRecipeManager()`，客户端返回空列表；
  客户端只做结构判定，真正的配方校验放服务端。
- **Create Fly 在加载期已把序列组装展开成普通配方**，所以配方池天然覆盖序列组装，
  不需要 `SequencedAssemblyRecipe.getRecipe`。
- Create Fly 的配方全部改写成 `record` + `MapCodec` / `StreamCodec`：
  `StandardProcessingRecipe`、`ProcessingRecipe`、`ProcessingRecipeParams`、
  `IRecipeTypeInfo` 都不存在了。
- `BasinRecipe` 没有静态 `match` / `apply`。**不能**简单换成
  `recipe instanceof BasinRecipe` —— 原版 `ShapelessRecipe` / `ShapedRecipe`
  **不实现** `BasinRecipe`，旧版的静态入口内部会分派到
  `BasinRecipe.matchCraftingRecipe` / `applyCraftingRecipe`。直接写 `instanceof`
  会让「搅拌杖手工搅拌工作台无序配方」「冲压锤把工作台配方压成方块」静默失效
  （现象：右键工作盆完全没反应）。统一走 `recipe/BasinRecipeCompat` 的三路分派。
- `BasinBlockEntity` 没有 `inputInventory` → `basin.itemCapability`
  （`BasinInventory implements ItemInventory extends Container`）。
- `DepotBlockEntity.getHeldItem()` 返回 `@Nullable TransportedItemStack`（不是 `ItemStack`）。
- `FluidInventory.extract/insert` 返回 `int`（实际转移量）。
- **流体单位变了（灌注枪踩过的坑）**：NeoForge 版 Create 的 `FluidStack` 以 mB 计数
  （1 桶 = 1000 mB），Create Fly 改走 Fabric Transfer API，以 **droplets** 计数
  （**1 桶 = 81000 droplets，1 mB = 81**，1 瓶 = 27000）。所有 `FluidStack.getAmount()`、
  `FluidIngredient.amount()`、配方 JSON 的 `amount` 都是 droplets。
  本模组对外仍按 mB 表达（枪容量 1000 mB、单次抽取 25 mB），跨边界统一用
  `InfusionGunContents.DROPLETS_PER_MB` 换算。若直接沿用 1000，会出现
  「枪显示满 1000 但工作盆水量几乎不动」「吸一格水只存 12 mB」「12 mB 能放回一整格水」
  「注液配方永远凑不够一瓶 27000」等一串现象。
- `BasinBlockEntity` 的流体库存 `BasinFluidHandler` 有 4 个槽位：
  **0/1 = 输出罐，2/3 = 输入罐**，容量各 81000 droplets（1 桶）。`isValid()` 对输出槽返回
  false（禁止插入），所以 `insert` 只会进输入罐；`extract` 则从 0 开始扫，
  先抽输出罐。
- `AllDamageSources.get(level).fan_lava` / `.fan_fire`；`entity.hurt` → `entity.hurtServer(...)`。
- `CreateRegistries.FAN_PROCESSING_TYPE`（`CreateBuiltInRegistries` 里没有）。
- **26.1 的配方 JSON 格式变了**：`Ingredient` 现在是字符串，
  `{"item": "minecraft:stick"}` → `"minecraft:stick"`，
  `{"tag": "c:plates/iron"}` → `"#c:plates/iron"`。本模组所有数据文件已按新格式重写。
- `RecipeType.register(String)` 只会注册到 `minecraft:` 命名空间，
  自定义命名空间必须自己 `new RecipeType<>(){}` 再 `Registry.register(BuiltInRegistries.RECIPE_TYPE, id, ...)`。
- `ItemParticleOption` 构造器改为接受 `Item` 或 `ItemStackTemplate`（不再接受 `ItemStack`）。
- `ChunkPos(BlockPos)` 构造器移除 → `ChunkPos.containing(pos)`。
- `UseOnContext#getHitResult()` 变为 `protected`。
- **26.1 的物品渲染是「数据 → 提交」两段式**：`assets/<ns>/items/<id>.json` 解析成
  `ItemModel`，`update()` 只往 `ItemStackRenderState` 里装图层（quads / special model / 变换），
  真正绘制由 `SubmitNodeCollector` 在提交阶段完成。自定义模型类型用
  `ItemModels.ID_MAPPER.put(id, MAP_CODEC)` 注册（该字段已被
  `fabric-transitive-access-wideners-v1` 放宽为 public，且是 `LateBoundIdMapper`，
  客户端初始化时补注册即可，晚于原版 `bootstrap()` 也没问题）。
- **26.1 的物品模型多了一层「物品定义」**：`assets/<ns>/models/item/*.json` 只是模型本身，
  还必须为每个物品提供 `assets/<ns>/items/<id>.json` 来声明用哪个模型：

  ```json
  {
    "model": {
      "type": "minecraft:model",
      "model": "create_hand_made:item/press_hammer"
    }
  }
  ```

  缺这一层时物品会渲染成紫黑方块（"missing model"）。原版 26.1 有 1507 个这样的文件。
  另外模型里的 `parent` 建议写全命名空间（`minecraft:item/handheld`）。

---

## 6. 已知缺失 / 暂缓项

| 项 | 原因 | 影响 |
| --- | --- | --- |
| **KubeJS 支持** | KubeJS 在 Fabric 26.1.2 上不存在 | 已彻底移除：`kubejs` 包、`kubejs.plugins.txt`、`recipe_schema` 全部删除。L3 配方改为纯数据包 JSON。 |
| **JEI 插件** | Create Fly 没有原 JEI 类别基类，需要基于 `com.zurrtum.create.client.compat.jei.CreateCategory` 重写全部 13 个类别 | `fabric.mod.json` 的 `jei_mod_plugin` 入口已移除，JEI 里不会出现本模组的配方页（工具本身功能不受影响）。 |
| **灌注枪物品栏液体底图** | `GuiGraphicsMixin` 依赖已变更的 26.1 渲染 API（`RenderSystem.setShaderColor` 等） | 物品栏里的灌注枪图标不显示液面。 |
| **`ServerConfigPayload` 客户端同步** | 需要自建 S2C 包 | 目前两端各自读本地 JSON。单人游戏完全一致；多人游戏里客户端与服务器的计时参数若被改过可能不同步（默认值下无影响）。 |

Create 的 `CustomRenderedItemModelRenderer` 那一套在 Create Fly 里已删除，但**全部 3D 手持渲染都已恢复**：
研钵、碾钵、手锯、风箱、搅拌杖各自注册了自定义 `ItemModel` 类型（见 3.4 ~ 3.6）。
灌注枪的旧渲染器本身是空操作（旋转被注释掉，只有 `pushPose` → 画原模型 → `popPose`），
所以普通 JSON 模型与上游等价，不需要自定义类型。

启用的 mixin 共两个：

- `BasinBlockEntityMixin` —— 让手动搅拌时工作盆里的流体在客户端也动起来。
- `client/ItemInHandRendererMixin` —— 手锯切削 / 风箱蓄力时取消「已被预览副本替代」的那只手的原版渲染（见 3.5、3.6）。

---

## 7. 目录结构

```
fabric/
├─ build.gradle / gradle.properties / settings.gradle
├─ libs/create-fly-26.1.2-6.0.9-4.jar     ← 需自行放入
├─ src/main/java/com/alben/createhandmade/
│  ├─ CreateHandMade.java                 主入口 + 事件接线
│  ├─ CreateHandMadeClient.java           客户端入口（粒子工厂 + 网络接收器）
│  ├─ ServerConfig.java                   自建 JSON 配置
│  ├─ ModDataComponents.java
│  ├─ bellows/                            风箱介质表（数据包 + 自动发现）
│  ├─ client/                             客户端状态 + 5 个自定义 ItemModel + 手锯 HUD
│  ├─ item/                               8 个工具 + 注册 + 手锯配方缓存
│  ├─ mixin/BasinBlockEntityMixin.java
│  ├─ mixin/client/ItemInHandRendererMixin.java
│  ├─ network/                            6 个 S2C 包
│  ├─ particle/
│  ├─ recipe/                             配方池 / 类型 / 序列化器 / L3 record
│  └─ util/
├─ src/main/resources/
│  ├─ fabric.mod.json
│  ├─ create_hand_made.mixins.json
│  ├─ assets/create_hand_made/            模型 / 纹理 / 语言 / 粒子
│  └─ data/create_hand_made/              合成配方 + 风箱介质
└─ neo-source/                            原始 NeoForge 源码（移植参考，不参与构建）
```

`repo/` 是上游仓库的完整克隆，仅作对照。

---

## 8. 验证状态

- `gradle -p fabric build` → **BUILD SUCCESSFUL**，产出
  `create_hand_made-0.5.0-beta.jar`（约 407 KB）。
- 资源完整性静态校验：8 个 `assets/create_hand_made/items/<id>.json` 全部指向存在的
  `models/item/<id>.json`，10 个模型的 `parent`（`minecraft:item/handheld` / `generated`）
  与 `layer0` 纹理在合并后的 Minecraft 资源中均能解析；其中 5 个物品定义使用自定义
  `ItemModel` 类型（`mortar` / `hand_saw` / `bellows` / `stirring_staff`）。
- `gradle -p fabric runClient` → 客户端能正常启动、进入世界：
  - `Reloading ResourceManager: vanilla, create, create_hand_made, ...` 成功
  - 资源重载与进入世界后**没有任何** `Unable to load model` / 模型解析相关的 WARN/ERROR
  - 研钵/碾钵的「钵 + 杵 + 内容物」三层模型 + 第一人称捶捣动画已由人工进游戏目视确认
    （玩家实拍截图：钵水平居中于屏幕中心、杵立在碗中并随节奏上下捶捣、碗内原料正常显示）。
  - 手锯已由人工进游戏目视确认：副手供料时 HUD 配方浮层正常出现、潜行右键切换配方时
    高亮框跟随、切削时锯子前伸摆动、副手原料预览只有一份（无"分身"）、
    潜行左键整树砍伐与 GUI / 第三人称外观均正常。
  - 风箱 / 搅拌杖已由人工进游戏目视确认：风箱蓄力时介质从画面外飞到风箱前、介质那只手
    不再重复渲染；搅拌杖起手转正前伸、加工时绕盆心画圆，连续搅拌不重播起手。
  - 搅拌杖的加工逻辑已由 `[CHM-RECIPE]` 日志确认（见下）：对工作盆里的
    `minecraft:blue_dye + minecraft:white_dye` 命中原版无序配方
    `minecraft:light_blue_dye_from_blue_white_dye`，产物 `light_blue_dye`
    数量随每次搅拌 2 → 4 → 6 → 8 递增；无可匹配配方时正确输出 `no match`。
  - 灌注枪已由人工进游戏确认（修正液滴单位之后）：对工作盆长按可抽满 1 桶
    （1000 mB = 81000 droplets）且盆内水量同步清空、短按地面放出整格流体、
    长按水源方块整格吸取、短按置物台/传送带上的物品可正常注液。
- `gradle -p fabric runServer` → 服务端正常启动到 `Done`：
  - `Create: Hand Made (Fabric port) initialised`
  - `Loaded 3435 recipes`（含本模组 8 个合成配方，无解析错误）
  - 风箱介质表 4 个文件全部解析成功
  - `config/create_hand_made-server.json` 自动生成，18 个配置项齐全
- 灌注枪 / 指针 / 压锤的功能与配方均已人工进游戏确认
  （研钵、碾钵、手锯、风箱、搅拌杖、灌注枪全部实测通过）。
- **排查开关**：
  - `-Dcreate_hand_made.debugRecipes=true` → 搅拌杖每次尝试打印一行 `[CHM-RECIPE]`，
    包含工作盆坐标、盆内物品、`mixing/shapeless/brewing` 三类候选数量、命中的配方 id
    或 `no match` / `exception` —— 可快速区分「候选集为空」与「有候选但没匹配上」。
  - `-Dcreate_hand_made.debugInfusion=true` → 灌注枪每次抽取 / 存入 / 放置打印
    `[CHM-GUN]`，含工作盆 4 个槽位（0/1 输出罐、2/3 输入罐）在操作前后的真实内容，
    用来排查流体单位与槽位选择问题。

---

## 9. 许可

沿用上游 MIT 许可，见 `LICENSE`。
