# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
