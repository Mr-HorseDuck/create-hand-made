# Create: Hand Made（1.20.1）

> 用双手完成机械动力的加工流程

[![MIT License](https://img.shields.io/badge/License-MIT-blue)](./LICENSE)
![Minecraft Version](https://img.shields.io/badge/Minecraft%20版本-1.21.1%7C1.20.1-success)
![Mod Loader Forge](https://img.shields.io/badge/Loader-Forge%7cNeoForge-red)
![CurseForge Downloads](https://img.shields.io/curseforge/dt/1712724?logo=curseforge&label=CurseForge%20%E4%B8%8B%E8%BD%BD%E9%87%8F&color=orange)

以下为Forge1.20.1版本介绍，[点击跳转到Neoforge1.21.1分支](https://github.com/Mr-HorseDuck/create-hand-made/tree/main)。

为 [Create](https://www.curseforge.com/minecraft/mc-mods/create) 添加一系列手工工具，
让玩家可以在没有机械的情况下完成部分加工流程。

## 工具列表

| 工具 | 功能 |
|---|---|
| **冲压锤** | 长按右键蓄力，冲压工作盆 / 置物台 / 传送带上的物品 |
| **研钵** | 手工研磨物品（MILLING 配方） |
| **碾钵** | 手工粉碎（CRUSHING），无配方时退回研磨 |
| **指杆** | 部署加工（DEPLOYING / ITEM_APPLICATION），潜行右键高亮方块/给女仆指定工作方块 |
| **搅拌杖** | 手工搅拌工作盆（MIXING + 无序合成 + 自动酿造） |
| **灌注枪** | 抽取 / 注入流体，支持物品填充配方 |
| **风箱** | 用副手介质施加鼓风加工（熔炼 / 烟熏 / 缠魂 / 洗涤） |
| **手锯** | 切削、砍树、剥皮、刮铜 |

## 功能列表
- 女仆现可支持使用手搓工具进行加工（9 种行为：冲压锤 / 液体搬运 / 灌注枪 / 搅拌杖 / 手锯 / 研钵 / 碾钵 / 风箱 / 指杆部署）
- 需要指杆指定工作方块（Shift+右键）Ctrl+滚轮切换模式 Shift+右键应用于女仆
- **只标工作方块即可**：不取料、不搬产物，只做加工那一步（取料和产物搬运交给机械臂 / 传送带 / 物流）
- 液体模式只需标输入盆 + 输出，过剩输出可选（不标则输出塞不下就停手）
- 两类标记互斥：一只女仆要么听加工目标，要么听液体搬运目标
- 有附魔「批量加工」可以一次性加工多个物品 一级2个 二级 4个 三级8个
- /chm reload可以热重载

## 依赖

| 依赖 | 版本 | 类型 |
|---|---|---|
| Minecraft | 1.20.1 | 必须 |
| Forge | 47.3.0+ | 必须 |
| Create | 6.0.8+ | 必须 |
| JEI | 15.2.0+ | 可选 |
| KubeJS | 2001.6.5+ | 可选 |
| TLM | 1.5.3+ | 可选 |
| Cloth Config API | 11.1.136+ | 可选 |

## 补充
- 和女仆流水线有联动

## 安装

1. 安装 Forge 47.3.0+
2. 安装 Create 6.0.8+ 及其依赖（Ponder、Flywheel）
3. 把本模组 jar 放进 `mods/` 文件夹
4. 启动游戏

## 面向整合包作者

本模组提供三层配方 API，让你修改手搓工具能做什么，**不影响 Create 机器**：

- **L2 · 过滤层** —— 单独禁用某工具的某条 Create 配方
- **L3 · 独占层** —— 添加只有手搓工具能读的配方

| 文档 | 内容 |
|---|---|
| [配方 API 参考](https://github.com/Mr-HorseDuck/create-hand-made/blob/1.20.1/docs/RECIPE_API.md) | L1/L2/L3 完整写法、数据包 + KubeJS 示例、字段矩阵 |
| [工具速查表](https://github.com/Mr-HorseDuck/create-hand-made/blob/1.20.1/docs/TOOL_REFERENCE.md) | 每个工具的配方来源、L2/L3 支持、禁用连带、匹配顺序 |
| [Java API](https://github.com/Mr-HorseDuck/create-hand-made/blob/1.20.1/docs/api.md) | `CreateHandMadeApi` 的 31 个方法、写操作副作用、1.20.1 / 1.21 差异 |
| [更新日志](https://github.com/Mr-HorseDuck/create-hand-made/blob/1.20.1/CHANGELOG.md) | 每个版本的新增、修复、破坏性变更与升级须知 |

支持**数据包**
**KubeJS**

## 未来展望
- 支持帕秋莉手册

## 构建（开发者）

```bash
./gradlew build
