# AsyncLocator 1.18.2-Reforged

[![CI](https://github.com/Vonix-Network/AsyncLocator-1.18.2-Reforged/actions/workflows/build.yml/badge.svg)](https://github.com/Vonix-Network/AsyncLocator-1.18.2-Reforged/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft: 1.18.2](https://img.shields.io/badge/Minecraft-1.18.2-green.svg)](#)
[![Forge: 40.x](https://img.shields.io/badge/Forge-40.x-orange.svg)](#)
[![Java: 17](https://img.shields.io/badge/Java-17-blue.svg)](#)

A production-hardened **maintenance fork** of [`brightspark/AsyncLocator`][upstream] for
**Minecraft 1.18.2 Forge**. AsyncLocator moves structure-location searches (treasure maps,
eyes of ender, `/locate`, villager cartographer trades, dolphin treasure) off the main
server thread onto a dedicated executor, so a single distant locate does not stall the
tick loop.

This **Reforged** line continues the 1.18.2 branch that upstream stopped maintaining at
`1.18.2-1.1.0` (Dec 2022). It ports the Lootr-compatibility fix from
[`Alvaro842DEV/AsyncLocator-Refined`][refined] (a NeoForge 1.21.1 fork) back to 1.18.2,
backports unreleased upstream improvements, and adds a production-grade hardening pass
suitable for long-running modded servers.

[upstream]: https://github.com/thebrightspark/AsyncLocator
[refined]:  https://github.com/Alvaro842DEV/AsyncLocator-Refined

> **Naming note.** This fork is named "Reforged" — the Vonix-Network 1.18.2 lineage.
> It is **not** the same project as `Alvaro842DEV/AsyncLocator-Refined`, which targets
> NeoForge 1.21.1. We credit Alvaro's Lootr-fix idea but the implementation, scope, and
> support boundary are independent.

---

## Table of contents

- [Why this fork exists](#why-this-fork-exists)
- [What's in the box](#whats-in-the-box)
- [Compatibility & requirements](#compatibility--requirements)
- [Installation](#installation)
- [Upgrading from 1.1.0](#upgrading-from-110)
- [Configuration](#configuration)
- [How it works](#how-it-works)
- [Project layout](#project-layout)
- [Building from source](#building-from-source)
- [Logging](#logging)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [Versioning & changelog](#versioning--changelog)
- [Credits](#credits)
- [License](#license)

---

## Why this fork exists

The original `thebrightspark/AsyncLocator` shipped its last 1.18.2 build
(`1.18.2-1.1.0`) to CurseForge in December 2022, then moved on to 1.19+. The 1.18.2
source was never tagged on GitHub, never released there, and never updated again. The
mod remained in heavy use by long-running 1.18.2 modpacks — and accumulated three
unresolved problems in production:

1. **Lootr incompatibility.** When a treasure map drops from a Lootr-managed chest, the
   async locate path can't find an `IItemHandler` capability on the Lootr block entity
   (by Lootr's design, every player sees their own inventory). The result: continuous
   `Couldn't find item handler capability on chest` WARN spam, never-finalising
   placeholder maps, and under load, chunk-load deadlocks.
2. **All-or-nothing scope.** If one async path (e.g. dolphin treasure) misbehaves in
   your pack, your only knob is to remove the entire mod.
3. **Executor lifecycle assumes a perfect world.** Exceptions in the worker can leak the
   placeholder map, the executor doesn't drain in-flight tasks on server stop, and
   workers aren't daemonised — leading to NPEs in shutdown logs and occasional JVM exit
   hangs.

Reforged fixes all three.

## What's in the box

| Area | Upstream `1.1.0` | Reforged (`1.3.1`) |
|---|---|---|
| Lootr-chest async map updates | ❌ WARN spam, map never finalises | ✅ Walks player containers/inventories first |
| Per-feature enable/disable | ❌ All-or-nothing | ✅ Five independent toggles |
| Block-pickup of in-progress maps | ❌ Player-yank orphans the task | ✅ `SlotMixin` keeps it pinned (creative bypass) |
| Loot-table `SetName` preservation | ❌ Stuck on `Locating...` forever | ✅ Stashed in NBT, applied on completion |
| Server-stop drain | ❌ NPE-on-stop in log | ✅ 10 s `awaitTermination`, then `shutdownNow` |
| `findNearestMapFeature` throw isolation | ❌ Future leaks, placeholder stuck | ✅ Completes `null`, invalidates cleanly |
| `LocateTask.then[OnServerThread]` errors | ❌ Silently swallowed | ✅ Logged at `WARN` |
| Pending-map detection | ❌ Hover-name string match | ✅ NBT tag `asynclocator_pending` |
| Daemon worker threads | ❌ Can block JVM exit | ✅ |
| SLF4J `Marker` on log output | ❌ | ✅ `"AsyncLocator"` |
| Config back-compat from 1.1.0 | n/a | ✅ Flat layout preserved, no value reset |

Full version history: **[CHANGELOG.md](CHANGELOG.md)**.

## Compatibility & requirements

- **Minecraft:** 1.18.2
- **Loader:** Forge 40.x (40.1.20 MDK used for development)
- **Java:** 17
- **Side:** Server-side only. Clients do **not** need the mod installed. The mod ships
  with `displayTest = "IGNORE_SERVER_VERSION"` to avoid the version-mismatch warning on
  vanilla clients.
- **Mod ID:** `asynclocator` — unchanged from upstream, drop-in compatible.
- **Known good companions:** Lootr (1.18.2 builds), Apotheosis, FTB Chunks, Create.

## Installation

Drop the jar into your server's `mods/` folder. If you're upgrading, **remove** the old
`asynclocator-1.18.2-1.1.0.jar` (or any earlier build) first — same mod ID, only keep
one.

```text
mods/
├── asynclocator-1.18.2-1.3.1.jar        ← from GitHub Releases
└── lootr-forge-1.18.2-0.3.30.74.jar     ← unchanged
```

Server-side only. Clients do not need the jar.

## Upgrading from 1.1.0

Reforged is a **strict superset** of `1.18.2-1.1.0` behaviour:

- Mod ID, package paths, and jar artifact name are unchanged.
- Existing `config/asynclocator-server.toml` files written by 1.1.0 upgrade cleanly with
  zero loss of customisation — the original flat layout (`asyncLocatorThreads`,
  `removeMerchantInvalidMapOffer`) is preserved. Only new keys (per-feature toggles)
  live under a new `[Features]` section.
- World data is unaffected. No new block/item/entity registrations.

After upgrading you can adopt the new features at your leisure (or not at all).

## Configuration

`config/asynclocator-server.toml`, generated on first launch:

```toml
# Number of threads to use for the executor that does the async locating
asyncLocatorThreads = 1

# Whether to remove an invalid map from the merchant offer when a villager-trade
# locate fails (rather than leaving a "Locating..." placeholder forever).
removeMerchantInvalidMapOffer = false

[Features]
# Each toggle independently enables/disables the async path for that game event.
# Disabling falls through to vanilla synchronous behaviour for ONLY that event.
dolphinTreasureEnabled  = true
eyeOfEnderEnabled       = true
explorationMapEnabled   = true
locateCommandEnabled    = true
villagerTradeEnabled    = true
```

Feature toggles are the **recommended troubleshooting lever**: if a specific event
misbehaves on your pack, disable just that one rather than removing the mod entirely.

## How it works

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│  Game event (treasure map drop, /locate, eye of ender, villager trade,      │
│  dolphin pathfind)                                                          │
└────────────────────────┬────────────────────────────────────────────────────┘
                         │  Mixin redirects to async path
                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  AsyncLocator.locate(...) → submits LocateTask to ExecutorService           │
│   • daemon worker thread, UncaughtExceptionHandler                          │
│   • exception in findNearestMapFeature → future completes with null         │
└────────────────────────┬────────────────────────────────────────────────────┘
                         │  Result (BlockPos or null) → main server thread
                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Per-feature Logic class updates the placeholder map (or invalidates it)    │
│   1. Walk every online player's open menu + inventory + carried cursor      │
│      (Lootr-compat fix — reference equality on the ItemStack)               │
│   2. Fall back to block-entity item-handler capability                      │
│   3. Apply deferred SetName from NBT; clear the asynclocator_pending tag    │
└─────────────────────────────────────────────────────────────────────────────┘
```

On server stop, `stopExecutor` waits up to 10 s for in-flight tasks to drain via
`awaitTermination`, then forces termination. Locate calls received outside the server
lifecycle throw a clear `IllegalStateException` instead of NPE.

## Project layout

```
src/main/
├── java/brightspark/asynclocator/
│   ├── AsyncLocator.java             ← public API, executor lifecycle, LocateTask
│   ├── AsyncLocatorConfig.java       ← Forge ConfigSpec (General + Features)
│   ├── AsyncLocatorMod.java          ← @Mod entrypoint, server-start/stop hooks
│   ├── logic/                        ← per-feature implementations
│   │   ├── CommonLogic.java          ← shared map-update primitives
│   │   ├── ExplorationMapFunctionLogic.java  ← treasure maps (incl. Lootr fix)
│   │   ├── MerchantLogic.java        ← villager cartographer trades
│   │   ├── EnderEyeItemLogic.java    ← /locate command + ender eye
│   │   ├── EyeOfEnderData.java       ← thrown-eye state container
│   │   └── LocateCommandLogic.java   ← /locate vanilla command
│   └── mixins/                       ← all game-event interception points
│       ├── ExplorationMapFunctionMixin.java
│       ├── TreasureMapForEmeraldsMixin.java
│       ├── DolphinSwimToTreasureGoalMixin.java
│       ├── EyeOfEnderMixin.java / EnderEyeItemMixin.java
│       ├── LocateCommandMixin.java
│       ├── SetNameFunctionMixin.java         ← deferred map naming
│       ├── SlotMixin.java                    ← block-pickup of pending maps
│       └── *Access.java                      ← @Accessor mixins for private fields
└── resources/
    ├── META-INF/mods.toml
    ├── asynclocator.mixins.json
    ├── logo.png
    ├── pack.mcmeta
    └── assets/asynclocator/lang/en_us.json
```

## Building from source

```bash
git clone https://github.com/Vonix-Network/AsyncLocator-1.18.2-Reforged.git
cd AsyncLocator-1.18.2-Reforged
JAVA_HOME=/path/to/jdk17 ./gradlew build
# → build/libs/asynclocator-1.18.2-1.3.1.jar
```

Requirements:

- **JDK 17** (any vendor — Adoptium/Temurin is what CI uses)
- **Gradle 7.4** — wrapper included, no separate install
- Internet access on first run for ForgeGradle to download the 1.18.2-40.1.20 MDK and
  Parchment mappings (`2022.05.02-1.18.2`)

CI builds every push and PR via `.github/workflows/build.yml`. Released jars are
attached to GitHub Releases tagged `vX.Y.Z`.

## Logging

All mod output uses an SLF4J `Marker` named `"AsyncLocator"`, so log4j2 / logback
pipelines can route or filter it independently. Example log4j2 filter to send all mod
output to its own file:

```xml
<MarkerFilter marker="AsyncLocator" onMatch="ACCEPT" onMismatch="DENY"/>
```

Log levels in production:
- `INFO`  — startup, shutdown, config load
- `WARN`  — locate failure, callback throw, unusual state
- `DEBUG` — per-event lifecycle (enable for diagnosis)

## Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| `Couldn't find item handler capability on chest` WARN spam | Pre-Reforged build still installed | Upgrade to ≥ `1.18.2-1.2.0` |
| Map stuck on `Locating...` after server upgrade | Old placeholder from pre-Reforged build | Break/replace the map; new locates will resolve |
| One specific event hangs | A pack mod interacting with that path | Toggle the corresponding `[Features]` key off |
| Server hangs ~10 s on stop | Expected — `awaitTermination` drain | Tune to taste; default is conservative |
| NPE on `/locate` immediately after `/stop` | Locate received outside server lifecycle | Now throws `IllegalStateException` with clear message |

## Contributing

PRs welcome — particularly compatibility patches for other 1.18.2 mods that interact
with the locate path (treasure-map loot tables, custom merchants, custom dolphins).

- Open an issue first for non-trivial changes so we can agree on scope.
- Keep mod ID (`asynclocator`) and config keys stable — Reforged's compatibility
  contract is **drop-in upgrade from 1.1.0**.
- Add an entry to `CHANGELOG.md` under `[Unreleased]` describing the change.
- New mixins go under `src/main/java/brightspark/asynclocator/mixins/` and must be
  registered in `asynclocator.mixins.json`.

## Versioning & changelog

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
[Semantic Versioning](https://semver.org/) **relative to the `1.18.2-*` line**. Versions
are `1.18.2-MAJOR.MINOR.PATCH`:

- `PATCH` — backwards-compatible fix
- `MINOR` — backwards-compatible feature
- `MAJOR` — breaking change (config schema, mod ID, behavioural contract)

See **[CHANGELOG.md](CHANGELOG.md)** for the full history.

## Credits

- **bright_spark** ([thebrightspark/AsyncLocator][upstream]) — original mod, MIT-licensed.
- **Alvaro842DEV** ([Alvaro842DEV/AsyncLocator-Refined][refined]) — the Lootr-fix idea
  this port is based on, MIT-licensed. Note: that project is the NeoForge 1.21.1 line
  and is independent from this Reforged 1.18.2 lineage.
- **Vonix-Network** — this 1.18.2 backport + production-grade hardening pass (the
  Reforged line).

## License

MIT — see [LICENSE](LICENSE). Original copyright `Copyright (c) 2022 bright_spark`
retained.

## Status

Actively maintained. Production-deployed on Vonix-Network 1.18.2 servers.
