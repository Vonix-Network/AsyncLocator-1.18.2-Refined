# AsyncLocator 1.18.2-Refined

A maintenance fork of **[brightspark/AsyncLocator]** that backports the **Lootr-compatibility fix** from **[Alvaro842DEV/AsyncLocator-Refined]** to **Minecraft 1.18.2 Forge**.

[brightspark/AsyncLocator]: https://github.com/thebrightspark/AsyncLocator
[Alvaro842DEV/AsyncLocator-Refined]: https://github.com/Alvaro842DEV/AsyncLocator-Refined

---

## Why this fork exists

The original `thebrightspark/AsyncLocator` shipped a `1.18.2-1.1.0` release on CurseForge in December 2022, then moved on to Minecraft 1.19.x. The 1.18.2 source was never tagged, never released on GitHub, and never updated. The mod is still in heavy use by long-running 1.18.2 modpacks.

In 2026, `Alvaro842DEV/AsyncLocator-Refined` shipped a fix for a real, acknowledged-by-both-authors incompatibility between AsyncLocator and **[Lootr]**:

[Lootr]: https://github.com/LootrMinecraft/Lootr

> When a treasure map drops from a Lootr chest, AsyncLocator's locate task finishes and tries to write the result back to the chest by looking up an `IItemHandler` capability on the chest's block entity. **Lootr exposes no item-handler capability** on its chest block entities (this is by design — Lootr's per-player loot is held off the world and reconstructed on open), so the lookup fails. Vanilla logs `WARN: Async Locator -> Couldn't find item handler capability on chest LootrChestBlockEntity at BlockPos{...}` and the in-progress map never gets its target updated.
>
> See: [LootrMinecraft/Lootr#793](https://github.com/LootrMinecraft/Lootr/issues/793), [Alvaro842DEV/AsyncLocator-Refined#5](https://github.com/Alvaro842DEV/AsyncLocator-Refined/issues/5).

Refined fixed this on 1.21.1 NeoForge by walking the **owning player's open container** (and inventory) for the in-progress map *before* falling back to the block-entity capability lookup. The map gets updated through the menu/inventory the player is actually looking at — Lootr never gets asked for a capability it doesn't expose.

This fork ports that idea to 1.18.2 Forge.

## What the fix does on 1.18.2

The 1.18.2 line is actually **simpler** to fix than the 1.21.1 line, because on 1.18.2 the in-progress `ItemStack` is the same JVM object instance the player sees in their slot — reference equality holds, no UUID-tracking component is needed.

When a locate task completes:
1. The map-update path first walks every **online player's open container menu** for that exact `ItemStack` reference. If found → broadcast the slot change → done.
2. Otherwise walks every **online player's inventory** (held, offhand, hotbar, main). If found → mark dirty → done.
3. Only if neither finds it does it fall back to the legacy block-entity item-handler capability lookup (and that lookup is now demoted to DEBUG-level logging on miss, since with the new fallback chain a capability-less BE is no longer a failure condition worth spamming WARNs over).

The same logic applies to the invalidate-map path (when no structure is found, the map is replaced with a blank).

## Compatibility & behaviour

- **Strict superset of 1.1.0 behaviour** for every player scenario that 1.1.0 already supported (player has the source chest open when the locate completes).
- **Net-new behaviour** for two scenarios 1.1.0 silently dropped:
  - The chest is a Lootr chest (or any other mod's BE with no item-handler cap).
  - The player closed the source chest before the locate task completed but is still carrying the in-progress map.
- **No config changes.** All existing config options still apply.
- **No new dependencies.** Pure additive change to the existing logic class.
- **Builds against the same Forge 1.18.2-40.1.20 baseline** as upstream `1.18.2-1.1.0`.

## Installation

Drop the jar into your server's `mods/` folder and **remove** the old `asynclocator-1.18.2-1.1.0.jar`. Versions are otherwise drop-in compatible — same mod ID, same config schema, same package paths.

Single-side: server-only mod. Clients do not need it.

## Building from source

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew build
# → build/libs/asynclocator-1.18.2-1.2.0.jar
```

Requirements: JDK 17, Gradle 7.4 (wrapper included), internet access for ForgeGradle to download the 1.18.2-40.1.20 MDK on first run.

## Credits

- **bright_spark** ([thebrightspark/AsyncLocator]) — original mod, MIT-licensed.
- **Alvaro842DEV** ([Alvaro842DEV/AsyncLocator-Refined]) — the fix idea this port is based on, MIT-licensed.
- **Vonix-Network** — this 1.18.2 backport.

## License

MIT — see [LICENSE](LICENSE). Original copyright `Copyright (c) 2022 bright_spark` retained.

## Status

Maintenance fork. The original 1.18.2 mod is unmaintained; this fork exists to keep it working in long-running 1.18.2 packs (notably **Isle of Berk / Claws of Berk** on the Vonix Network). Pull requests for other Lootr-style incompatibilities welcome.
