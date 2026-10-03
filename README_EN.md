# Create: Hand Made

[中文](README.md) · **English**

> Complete part of Create's processing chain — by hand.

[![MIT License](https://img.shields.io/badge/License-MIT-blue)](./LICENSE)
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-success)
![Mod Loader](https://img.shields.io/badge/Loader-NeoForge-red)
![CurseForge Downloads](https://img.shields.io/curseforge/dt/1712724?logo=curseforge&label=CurseForge%20Downloads&color=orange)

An addon for [Create](https://www.curseforge.com/minecraft/mc-mods/create) that adds a set of manual crafting tools, letting players complete part of the processing chain without building machines.

**This page describes the NeoForge 1.21.1 version.** [Click here for the Forge 1.20.1 branch](https://github.com/Mr-HorseDuck/create-hand-made/tree/1.20.1).

## Tools

| Tool | Function |
|---|---|
| **Press Hammer** | Hold right-click to charge, press items on a Basin / Depot / Belt |
| **Mortar** | Hand grinding (MILLING) |
| **Crusher Mortar** | Hand crushing (CRUSHING), falls back to milling |
| **Pointer** | Deploying (DEPLOYING / ITEM_APPLICATION), sneak + right-click to highlight a block |
| **Stirring Staff** | Hand mixing in a Basin (MIXING + shapeless + auto-brewing) |
| **Infusion Gun** | Extract / inject fluids, supports item filling recipes |
| **Bellows** | Apply fan processing via an offhand medium (blasting / smoking / haunting / splashing) |
| **Hand Saw** | Cutting, tree felling, stripping, scraping |

## Dependencies

| Dependency | Version | Type |
|---|---|---|
| Minecraft | 1.21.1 | Required |
| NeoForge | 21.1.250+ | Required |
| Create | 6.0.10+ | Required |
| JEI | 19.0.0+ | Optional |
| KubeJS | 2101.7.0+ | Optional (recipe scripting) |
| Sable | 2.0.5+ | Optional (rendering) |

## Installation

1. Install NeoForge 21.1.250+
2. Install Create 6.0.10+ and its dependencies (Ponder, Flywheel)
3. Drop this mod's jar into your `mods/` folder
4. Launch the game

## For Modpack Authors

This mod provides a three-layer recipe API, letting you modify what hand tools can craft **without affecting Create's machines**:

- **L2 · Filtering** — disable a specific Create recipe for one tool only
- **L3 · Exclusive recipes** — add recipes only hand tools can read

| Document | Contents |
|---|---|
| [Recipe API reference](https://github.com/Mr-HorseDuck/create-hand-made/blob/main/docs/RECIPE_API.md) | L1/L2/L3 syntax, datapack + KubeJS examples, field matrix |
| [Tool reference](https://github.com/Mr-HorseDuck/create-hand-made/blob/main/docs/TOOL_REFERENCE.md) | Recipe sources per tool, L2/L3 support, disable side effects, match order |

Both **datapack** and **KubeJS** are supported.

## Roadmap

- Possible compatibility with Touhou Little Maid

## Building (Developers)

```bash
./gradlew build