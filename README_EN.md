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

## Handcrafted Assembly (hand-only production line)

The eight tools can also be chained into a multi-step line **only hand tools can advance**:

- Recipe type `create_hand_made:hand_sequenced_assembly` — **Create's machines cannot see it at all**
  (they only query `create:sequenced_assembly`); only the matching hand tool advances the
  transitional item step by step;
- Field-for-field identical to `create:sequenced_assembly` (`ingredient` / `transitional_item` /
  `sequence` / `results` / `loops`); steps are `create_hand_made:tool_recipe` entries, so migrating
  from the vanilla format is just a `type` change;
- Its own JEI category **"Handcrafted Assembly"**: centered step row, `+N` when a line has more than
  6 steps (hover to list the folded ones), and normalized chance percentages for probabilistic results;
- Want machines **and** hands mixed? Drop L3 steps straight into vanilla `create:sequenced_assembly`.

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
- **L3 · Container recipes** — chain several hand recipes into one production line with
  `create_hand_made:hand_sequenced_assembly`

| Document | Contents |
|---|---|
| [Recipe API reference](https://github.com/Mr-HorseDuck/create-hand-made/blob/main/docs/RECIPE_API.md) | L1/L2/L3 syntax, handcrafted assembly (sequenced), datapack + KubeJS examples, field matrix |
| [Tool reference](https://github.com/Mr-HorseDuck/create-hand-made/blob/main/docs/TOOL_REFERENCE.md) | Recipe sources per tool, L2/L3 support, disable side effects, match order |

Both **datapack** and **KubeJS** are supported.

## Roadmap

- **KubeJS sugar** (`event.recipes.create_hand_made.*`) — needs two missing KubeJS components first:
  mixed item/fluid ingredients, and a chance-aware output type
- **JEI markers for steps without an executor** (basin / machine steps currently only warn in the log)
- **Touhou Little Maid compatibility** (see the 1.20.1 branch implementation)

## Building (Developers)

```bash
./gradlew build