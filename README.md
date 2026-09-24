# Fallout London Companion

Native SwiftUI iPhone/iPad companion for Fallout: London, built around the game's ATTA-Boy/Pip-Boy companion protocol.

<p align="center">
  <img src="docs/FalloutLondonCompanion.svg" alt="Fallout London Companion artwork" width="256">
</p>

## Current build

- iOS/iPadOS 18+
- iPhone + iPad
- Portrait + landscape
- Responsive SwiftUI layout
- ATTA-Boy shell and CRT presentation
- Physical-style boot sequence
- WITH THANKS TO TEAM FOLON boot credit
- UDP autodiscovery on port 28000
- TCP protocol transport on port 27000
- Binary database/update decoding
- Local-map packet decoding
- RPC command envelope
- Live STAT/player data foundation
- Inventory categories and item parsing
- Manual Stimpak/RadAway protocol paths
- Configurable Auto-Stimpak threshold
- GitHub Actions macOS IPA builds for SideStore testing

## Interface

- STAT — HP, AP, radiation/limb state, SPECIAL, XP and effects foundation.
- INV — Weapons, Apparel, Aid, Misc, Junk and Ammo with item metadata, favorites and inventory-state parsing.
- DATA — Quest, log, workshop and player database data foundation.
- MAP — Fallout: London worldspace/player-position data and local-map snapshot status.
- RADIO — Station state, frequency/text fields and active/in-range state when exposed by the game.

## Protocol

The implementation follows the verified Fallout 4 companion protocol structure:

- UDP 28000 autodiscovery
- TCP 27000
- little-endian packet framing
- heartbeat, connection, data, local-map, RPC and command-response packet types
- binary object/array/value database graph
- JSON RPC envelope

Protocol details are documented in FalloutLondonCompanion/Documentation/PROTOCOL.md.

London-specific behavior is only added when supported by captured Fallout: London data or verified research. Canonical boot text is not invented.

## Medical

Verified database references currently support:

- Stimpak object ID + validity flag
- RadAway object ID + validity flag
- inventory version
- item handle ID
- stack ID
- UseItem RPC

Auto-Stimpak is implemented as an app-side threshold controller with a default threshold of 35% HP.

Auto-Doc remains a research item until a supported protocol path is verified.

## Maps

The protocol layer decodes local-map snapshots and coordinate extents.

The planned map system uses one shared coordinate system for:

1. Original Fallout: London presentation
2. Enhanced readable/color presentation
3. Topographical presentation derived from verified London geography/data

The full renderer and curated London map data remain in development.

## ATTA-Boy boot

The boot presentation follows the physical-style sequence established for the project:

1. device off/black
2. device positioning/lean
3. CRT activation
4. initialization presentation
5. hardware indicators
6. Team FOLON credit
7. interface ready
8. protocol discovery/connection

The exact Fallout: London firmware text is not fabricated when source material does not provide it.

## Assets

The current app icon and project SVG artwork are locked user-approved assets. They must not be altered, regenerated, recolored, replaced, resized, converted, or otherwise modified unless explicitly requested.

Other project assets may be added or changed as implementation requires.

## Credits

WITH THANKS TO TEAM FOLON

Fallout: London is a Team FOLON project. This companion is a separate application and does not imply endorsement or affiliation.

Private correspondence or private Discord screenshots are not included in the repository.

## Branches and builds

- main — stable/release branch
- develop — active development branch

GitHub Actions builds the unsigned iOS application on macOS and packages:

Fallout London Companion.ipa

The IPA is intended for SideStore/device testing.

## Development status

### Implemented / verified foundation

- [x] SwiftUI iPhone/iPad project
- [x] iOS/iPadOS 18 deployment target
- [x] Responsive orientation support
- [x] ATTA-Boy boot presentation foundation
- [x] CRT/scanline presentation
- [x] Protocol framing and binary decoder
- [x] UDP/TCP connection foundation
- [x] Database object graph
- [x] STAT foundation
- [x] Inventory parsing foundation
- [x] Medical RPC foundation
- [x] Auto-Stimpak controller foundation
- [x] Local-map packet decoding
- [x] IPA validation in CI
- [x] SideStore-compatible unsigned IPA packaging
- [x] App icon PNG wired into the asset catalog

### In progress

- [ ] Full ATTA-Boy physical shell fidelity
- [ ] Physical controls, LEDs, gauge and haptics
- [ ] Detailed CRT/phosphor effects
- [ ] Character animation system
- [ ] Full inventory actions
- [ ] Quest/objective presentation
- [ ] Full map renderer and London map data
- [ ] Radio controls/RPCs
- [ ] Auto-Doc verification
- [ ] Audio integration
- [ ] Persistent cache / Demo Mode
- [ ] Settings
- [ ] Device testing and polish

## Development workflow

implement → CI build → IPA artifact → SideStore install → physical-device test → iterate

The app icon and project SVG remain fixed unless explicitly requested to change.
