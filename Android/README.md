# Android Companion

Reserved for the parallel Android implementation of Fallout London Companion.

## Direction

- Kotlin
- Jetpack Compose
- Android-native networking and lifecycle
- Android implementation of the same verified Fallout companion protocol
- Shared behavioral contract with the iOS implementation

The Android build is a secondary development track. The iOS/iPadOS implementation remains the primary active platform.

## Planned layers

- Protocol transport
- Binary packet decoder
- Database/object graph
- Player/STAT model
- Inventory model
- Medical/RPC layer
- DATA
- MAP
- RADIO
- Demo/cache
- ATTA-Boy presentation
- Android-specific audio, haptics and lifecycle handling

No Android UI or protocol behavior is being invented here before the corresponding behavior is verified on the shared project protocol boundary.
