# Shared Project Contract

This directory documents behavior that must remain consistent across the iOS and Android implementations.

## Shared contract

- Fallout companion discovery and connection behavior
- Packet framing and message types
- Binary database/object graph semantics
- Local-map packet semantics
- RPC command definitions and argument ordering
- Fallout: London data vocabulary verified from captures
- Player, inventory, quest, map and radio data models
- Demo/test data and protocol captures
- Requirement and verification status

Platform-specific UI, networking APIs, lifecycle handling, audio, haptics and rendering remain native to each platform.

The iOS implementation is currently the primary implementation. Android is a parallel secondary track.
