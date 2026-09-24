# Platform Plan

## Primary platform

iOS/iPadOS remains the primary implementation and device-testing target.

## Secondary platform

Android will be developed as a parallel implementation using Kotlin and Jetpack Compose.

## Shared behavior

Both platforms should implement the same verified:

- UDP discovery
- TCP connection
- packet framing
- binary data updates
- local-map updates
- database/object graph
- RPC envelope
- player/STAT model
- inventory model
- medical behavior
- DATA/MAP/RADIO data contracts
- demo/test data

## Native behavior

The following remain platform-specific:

- SwiftUI vs Jetpack Compose
- Network.framework vs Android networking
- iOS vs Android lifecycle/background behavior
- haptics
- audio APIs
- rendering details
- platform packaging/signing

## Build strategy

The repository should eventually build both platforms from GitHub Actions:

- iOS: macOS runner -> Xcode -> unsigned IPA
- Android: Linux/Android runner -> Gradle -> APK, with AAB added when distribution requires it

A protocol or data-model change should be designed against the shared contract first, then implemented/tested on both platforms.

## Current status

Android foundation is now mirrored through the current iOS feature boundary: project/build setup, locked icon reuse, boot presentation, protocol transport/decoder, database object graph, player/STAT model, inventory model, Auto-Stimpak controller, DATA, MAP and RADIO views are present. Android still requires device validation and deeper parity work for controls, full RPC actions, cache/Demo Mode, character animation, audio, haptics and full map rendering.
