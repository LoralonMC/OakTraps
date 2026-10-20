# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.0] - 2026-10-20

### Changed

- Targets Paper 26.3 (`api-version: '26.3'`, built against paper-api 26.3). This build no longer loads on 26.1.2.

### Added

- `capture.protected-tags`: PDC keys that make an entity untrappable, whatever
  `capture.allowed-entities` says. Ships with `oakpets:pet_owner`, so OakPets
  pets can never be captured. A live pet already couldn't trip a plate (OakPets
  cancels the interact event), but a pet that has leaked out of OakPets's
  bookkeeping looks like an ordinary pig, and could be lured onto a trap and
  sealed inside the item. Reported in-game with a trap reading `ᴄᴀʀʀʏɪɴɢ
  md_squirrel_pet`. A tag means the owning plugin manages that entity's
  lifetime, so a trap has to leave it alone. Invalid entries are skipped with a
  config warning.

- Particle burst when a trap springs, fired after `capture.trigger-delay-ticks`
  so it punctuates the capture rather than competing with the plate depressing.
  Configurable under `capture.particle` (`type`, `count`, `spread`, `speed`); set
  `type: NONE` to disable. Particles that require extra data (DUST, BLOCK, ITEM
  and friends) are rejected at load with a config warning instead of throwing.
- `release.confirm-seconds` (default 5): right-clicking a filled trap now asks
  for a second right-click before releasing, naming the captured mob. A filled
  trap is a barrel, so the natural assumption is that right-click opens a
  container; it does not, and with `break-on-release` on it also destroys the
  trap. Set to 0 for the previous one-click behaviour.
- `release.require-sneak` (default false): additionally require the player to be
  sneaking to release. Stacks with the confirmation.
- Messages `release-confirm`, `release-confirm-break` (used when
  `break-on-release` is on, so the player is warned the trap is spent) and
  `release-sneak-required`.

## [1.0.0] - 2026-06-18

### Added

- Initial release.
- Lure-based mob capture (modelled on NekoTraps): an armed trap is a flush
  pressure plate surrounded by a five-barrel block-display "cage". A mob that
  steps on the plate is captured; players can't trigger their own traps and
  disallowed mobs are ignored. A filled trap becomes a barrel — right-click to
  release the mob (and get the trap back), or break it to pick it up with the
  mob still inside.
- Full-fidelity capture/restore via Paper's entity serialization (equipment,
  trades, tamed state, AI, names, attributes) — no version-specific NMS.
- Filled cages can be broken to retrieve a portable filled trap item, placed to
  redeploy, or right-clicked to release the mob (the cage re-arms empty).
- `BlockDisplay` placement/capture flourish (disposable, configurable).
- Capture rules: allowed/denied entity list, untrappable types, tame toggle,
  per-world filter.
- Integration hooks (all optional): WorldGuard (placement protection), Lands
  (claim protection — no mob-stealing), Shopkeepers (never capture shopkeeper
  entities), MythicMobs (capture custom mobs with allow/deny by type).
- Optional crafting recipe for the empty trap (disabled by default).
- Commands: `/oaktraps give`, `/oaktraps reload`.
