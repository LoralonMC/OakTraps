# OakTraps

Lure-based mob capture cages for Paper. Place a trap, lure a mob onto it to
capture it, then store, relocate, or release the mob with full fidelity.

## Features

- **Lure-based capture** — place an empty trap (a single barrel cage); a mob
  lured onto it is captured. Players never trigger traps, and disallowed mobs are
  ignored.
- **Full-fidelity storage** — captured mobs keep their health, equipment, trades,
  tamed state, AI, custom name, and attributes. No version-specific NMS, so it
  survives Paper updates.
- **Portable** — break a filled cage to get a trap item holding the mob; replace
  it elsewhere, or right-click a placed cage to release the mob (it re-arms).
- **Capture rules** — allow/deny lists by entity type, untrappable types, a
  tamed-mob toggle, and a per-world filter.
- **Integrations** (all optional) — WorldGuard, Lands, Shopkeepers, MythicMobs.

## Requirements

- Paper **26.1.2** (or compatible)
- Java **25**

## Installation

1. Drop `OakTraps-x.y.z.jar` into your server's `plugins/` folder.
2. Restart the server.
3. Edit `plugins/OakTraps/config.yml` and `messages.yml` to taste, then
   `/oaktraps reload`.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/oaktraps` | Show usage | `oaktraps.use` |
| `/oaktraps give <player> [amount]` | Give empty trap items | `oaktraps.give` |
| `/oaktraps reload` | Reload configuration | `oaktraps.reload` |

Alias: `/traps`.

## Permissions

| Node | Description | Default |
|---|---|---|
| `oaktraps.use` | Use trap cages (place, capture, retrieve, release) | everyone |
| `oaktraps.give` | Give trap items to players | op |
| `oaktraps.reload` | Reload configuration | op |
| `oaktraps.*` | All of the above | op |

## Configuration

Key settings in `config.yml`:

- **trap-item** — the trap item's material, name, glow, and lore (empty/filled).
- **capture** — allowed-entity list/mode, untrappable types, `can-trap-tamed`,
  and per-world filtering.
- **cage** — the cage block + pressure-plate trigger, and the display animation.
- **protection** — toggle WorldGuard / Lands enforcement and whether cages may
  be placed in protected areas.
- **mythicmobs** — allow/deny which MythicMobs can be captured.
- **crafting** — optional recipe for the empty trap (off by default).

Player-facing text lives in `messages.yml`. See the config files for the full,
commented reference.

## Integrations

All are optional and activate only when the plugin is installed:

- **WorldGuard** — traps can only be placed where the player can build.
- **Lands** — a cage won't capture mobs in claims the placer isn't trusted in.
- **Shopkeepers** — shopkeeper entities are never captured.
- **MythicMobs** — capture custom mobs, filtered by the `mythicmobs` allow/deny list.
