# Create: Hand Made（1.20.1）

> 用双手完成机械动力的加工流程

[![MIT License](https://img.shields.io/badge/License-MIT-blue)](./LICENSE)
![Minecraft Version](https://img.shields.io/badge/Minecraft%20版本-1.21.1%7C1.20.1-success)
![Mod Loader Forge](https://img.shields.io/badge/Loader-Forge%7cNeoForge-red)
![CurseForge Downloads](https://img.shields.io/curseforge/dt/1712724?logo=curseforge&label=CurseForge%20%E4%B8%8B%E8%BD%BD%E9%87%8F&color=orange)

以下为forge1.20.1版本介绍，[点击跳转到neoforge1.21.1分支](https://github.com/Mr-HorseDuck/create-hand-made/tree/main)。

为 [Create](https://www.curseforge.com/minecraft/mc-mods/create) 添加一系列手工工具，
让玩家可以在没有机械的情况下完成部分加工流程。

## 工具列表

| 工具 | 功能 |
|---|---|
| **冲压锤** | 长按右键蓄力，冲压工作盆 / 置物台 / 传送带上的物品 |
| **研钵** | 手工研磨物品（MILLING 配方） |
| **碾钵** | 手工粉碎（CRUSHING），无配方时退回研磨 |
| **指杆** | 部署加工（DEPLOYING / ITEM_APPLICATION），潜行右键高亮方块 |
| **搅拌杖** | 手工搅拌工作盆（MIXING + 无序合成 + 自动酿造） |
| **灌注枪** | 抽取 / 注入流体，支持物品填充配方 |
| **风箱** | 用副手介质施加鼓风加工（熔炼 / 烟熏 / 缠魂 / 洗涤） |
| **手锯** | 切削、砍树、剥皮、刮铜 |

## 依赖

| 依赖 | 版本 | 类型 |
|---|---|---|
| Minecraft | 1.20.1 | 必须 |
| Forge | 47.3.0+ | 必须 |
| Create | 6.0.8+ | 必须 |
| JEI | 15.2.0+ | 可选 |
| KubeJS | 2001.6.5+ | 可选 |


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
| [配方 API 参考](https://github.com/Mr-HorseDuck/create-hand-made/blob/main/docs/RECIPE_API.md) | L1/L2/L3 完整写法、数据包 + KubeJS 示例、字段矩阵 |
| [工具速查表](https://github.com/Mr-HorseDuck/create-hand-made/blob/main/docs/TOOL_REFERENCE.md) | 每个工具的配方来源、L2/L3 支持、禁用连带、匹配顺序 |

支持**数据包**
**KubeJS**

## 未来展望 顺序越后优先级越低
- 支持女仆使用工具加工（可能会单独写一个模组）
- 支持瓦基的物理结构（跟1.21.1支持Sable类似）

## 构建（开发者）

```bash
./gradlew build
