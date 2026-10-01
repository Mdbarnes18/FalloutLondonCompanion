# Fallout London Companion

Native companion app for Fallout: London, with native clients for iPhone, iPad, Android phones/tablets, Windows, and Linux. The clients share the same verified Fallout 4/Fallout: London companion-protocol behavior while using platform-native UI and build pipelines.

<p align="center">
  <img src="docs/FalloutLondonCompanion.svg" alt="Fallout London Companion artwork" width="256">
</p>

## Current build

### iOS / iPadOS
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

### Android
- Android phones + tablets
- Kotlin + Jetpack Compose
- Portrait + landscape
- Responsive platform-native layout
- ATTA-Boy shell and CRT presentation
- Physical-style boot sequence
- WITH THANKS TO TEAM FOLON boot credit
- UDP/TCP protocol connection foundation
- Binary database/update decoding
- Local-map packet decoding
- RPC command envelope
- Live STAT/player data foundation
- Inventory categories and item parsing
- Manual Stimpak/RadAway protocol paths
- Configurable Auto-Stimpak threshold
- GitHub Actions APK builds

### Windows
- Windows desktop client
- Kotlin + Compose Multiplatform
- Same five-tab ATTA-BOY information architecture
- STAT, INV, DATA, MAP and RADIO foundations
- Database browser with search
- Copy-all database export
- TXT database export
- Persistent cached-data / Demo Mode
- Auto-Stimpak and manual Stimpak/RadAway protocol paths
- Windows installer (MSI)
- Portable Windows EXE

### Linux
- Linux desktop client
- Kotlin + Compose Multiplatform
- Same five-tab ATTA-BOY information architecture
- STAT, INV, DATA, MAP and RADIO foundations
- Database browser with search
- Copy-all database export
- TXT database export
- Persistent cached-data / Demo Mode
- Auto-Stimpak and manual Stimpak/RadAway protocol paths
- AppImage
- Flatpak
- DEB
- RPM

## Interface

Both clients follow the same core interface:

- **STAT** — HP, AP, radiation/limb state, SPECIAL, XP and effects foundation.
- **INV** — Weapons, Apparel, Aid, Misc, Junk and Ammo with item metadata, favorites and inventory-state parsing.
- **DATA** — Quest, log, workshop and player database data foundation.
- **MAP** — Fallout: London worldspace/player-position data and local-map snapshot status.
- **RADIO** — Station state, frequency/text fields and active/in-range state when exposed by the game.

Platform-specific presentation can differ where Android and Apple UI conventions require it, but game data, protocol behavior, and supported functionality are kept aligned.

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

The map system uses one shared coordinate system for:

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

## Platform flow

The project runs as native mobile and desktop targets while keeping the game-facing behavior aligned:

**iPhone / iPad**
1. Develop the shared game/protocol behavior in the SwiftUI client.
2. Push changes to GitHub.
3. GitHub Actions builds the unsigned iOS/iPadOS application on macOS.
4. CI validates and packages `Fallout London Companion.ipa`.
5. Install the IPA through your preferred iOS device installation method.
6. Test on a physical iPhone or iPad.
7. Fix, refine, commit, push, and repeat.

**Android phone / Android tablet**
1. Mirror the same verified protocol, data, and gameplay-facing behavior in the Kotlin/Compose client.
2. Push changes to GitHub.
3. GitHub Actions builds the Android APK.
4. CI validates and uploads the APK artifact.
5. Install the APK on an Android phone or tablet for device testing.
6. Test the same game-facing flows against Fallout: London.
7. Fix, refine, commit, push, and repeat.

**Windows / Linux desktop**
1. Mirror the same verified protocol, data, and gameplay-facing behavior in the Compose Multiplatform desktop client.
2. Push changes to GitHub.
3. GitHub Actions builds the Windows and Linux packages.
4. CI validates the installer/portable Windows build and Linux AppImage/Flatpak/DEB/RPM packages.
5. Install on the target desktop platform for device testing.
6. Test the same game-facing flows against Fallout: London.
7. Fix, refine, commit, push, and repeat.

The intended development loop is therefore:

**implement → GitHub → CI build → install on the target device → physical-device test → iterate**

The iOS/iPadOS IPA and Android APK are separate native builds, while the protocol and data contract remain aligned between them.

## Builds

### iOS / iPadOS build

GitHub Actions builds the unsigned iOS/iPadOS application on macOS and packages:

`Fallout London Companion.ipa`

The IPA is intended for iOS/iPadOS device testing.

### Android build

GitHub Actions builds the Android application and uploads:

`Fallout London Companion.apk`

The APK is intended for Android phone/tablet device testing.

### Windows build

GitHub Actions builds two Windows packages:

- `Fallout London Companion Windows Installer.msi`
- `Fallout London Companion Windows Portable.exe`

### Linux build

GitHub Actions builds four Linux packages:

- `Fallout London Companion.AppImage`
- `Fallout London Companion.flatpak`
- `Fallout London Companion.deb`
- `Fallout London Companion.rpm`

## Development status

### iOS / iPadOS foundation
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
- [x] Unsigned IPA packaging
- [x] App icon PNG wired into the asset catalog

### Android foundation
- [x] Android phone/tablet project
- [x] Kotlin + Jetpack Compose foundation
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
- [x] Android APK CI build

### Cross-platform work still being built out
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
- [x] Persistent cache / Demo Mode
- [x] Settings
- [x] Database browser and TXT export
- [x] Windows installer + portable EXE packaging
- [x] Linux AppImage + Flatpak + DEB + RPM packaging
- [x] Combined numbered releases across all platform builds
- [ ] Device testing and polish

The checklists describe the current implementation boundary; they are not a promise that an item has been fully production-tested.

## Development workflow

**implement → GitHub → CI build → device install → physical-device test → iterate**

The app icon and project SVG remain fixed unless explicitly requested to change. Platform packaging may create format-specific copies when required by a target package, but the locked source artwork is not altered.


## Open source

Fallout London Companion is an open-source project. The source code, protocol research, documentation, and platform implementations are available in this repository so the project can be inspected, learned from, tested, and improved by the community.

Contributions, bug reports, improvements, and platform-specific work are welcome. Please keep Fallout: London/Team FOLON assets, third-party assets, and any separately licensed material subject to their respective permissions and licenses.
