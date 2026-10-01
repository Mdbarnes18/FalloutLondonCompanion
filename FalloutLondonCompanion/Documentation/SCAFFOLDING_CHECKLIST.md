# Scaffolding Completion Gate

This checklist tracks the UI and architectural scaffolding that must be present on both iOS/iPadOS and Android before importing the next set of live `.pip` captures. A scaffold is complete when the screen, navigation, state boundary, empty/loading/error states, and platform-equivalent controls exist. It is not considered data-verified until tested against a fresh game capture.

## Platform-wide
- [x] Five primary tabs: STAT, INV, DATA, MAP, RADIO
- [x] ATTA-Boy shell and CRT/scanline presentation
- [x] Boot presentation and Team FOLON credit
- [x] Live connection boundary and reconnect behavior
- [x] Persistent database cache / Demo Mode foundation
- [x] Full database browser, copy-all, and TXT export
- [x] Medical controls: manual Stimpak/RadAway and configurable Auto-Stimpak
- [x] Settings screen with connection, demo, display, audio, haptics, and data-management sections
- [x] Shared screen-level loading, empty, disconnected, and error presentation conventions
- [ ] Consistent navigation and physical-control affordances across phone, tablet, portrait, and landscape

## STAT
- [x] HP/AP, level/XP, carry weight, SPECIAL, limb values, and medical controls foundation
- [x] Dedicated radiation meter and active-effects presentation
- [ ] Live active-effects extraction from the verified database structure
- [x] Perk list/detail scaffold and level-up state
- [ ] Live perk data and verified level-up RPC
- [x] Character reaction presentation states and animation host
- [ ] Character animation assets/playback

## INV
- [x] Category tabs, item list, selection/detail panel, favorite/equipped/legendary indicators
- [x] Complete item-card scaffold for all observed item-stat groups
- [x] Item action sheet scaffold (use, equip/unequip, drop, favorite, sort), gated by verified RPC support
- [ ] Verified item-action RPC argument handling
- [x] Search, sort, and filter controls with persistent selection
- [x] Empty, loading, and action-result states

## DATA
- [x] Quest/log/workshop/player summaries and full database browser/export
- [x] Quest detail/objective/marker presentation and completed/active sections
- [x] Notes, statistics, workshop, and miscellaneous data sections
- [x] Action feedback and stale-cache indication
- [ ] Command-response parsing and action result reconciliation

## MAP
- [x] Worldspace/player-coordinate/local-map metadata scaffold
- [x] Map canvas host with shared coordinate transform
- [x] Original / enhanced / topographical mode selector
- [x] Player, quest, discovered-location, and custom-marker overlay layer boundary
- [ ] Verified marker data extraction and placement
- [x] Local-map snapshot viewer and pan/zoom interaction scaffold
- [ ] Verified local-map pixel rendering

Map artwork, coordinate transforms, and marker placement remain data-verification tasks; do not invent London geography.

## RADIO
- [x] Station list and on/off command boundary
- [ ] Verified ToggleRadioStation RPC argument handling
- [x] Station detail/current-program panel and unavailable/out-of-range states
- [x] Volume/audio-output UI scaffold, only where platform/API supports it
- [ ] Platform audio playback/output implementation

## Physical ATTA-Boy / presentation
- [x] Shell, CRT, scanlines, boot sequence
- [x] Responsive shell sizing for iPhone, iPad, portrait, and landscape
- [x] Physical button/control visual-state boundary
- [ ] Platform haptic implementation
- [x] Audio playback/mute preference boundary
- [ ] Audio playback implementation
- [x] Reusable character animation host and state enum presentation

## Quality and release gate
- [ ] iOS CI build passes after each paired feature
- [ ] Android CI build passes after each paired feature
- [ ] No modification to locked AppIcon or project SVG artwork
- [ ] No private Discord/team correspondence or screenshots included in repository
- [ ] User reimports fresh `.pip` captures only after this gate is reviewed

## Workflow
Implement one coherent scaffold slice at a time on `main`, in iOS and Android parity. Keep protocol-dependent actions behind explicit capability boundaries until verified from the user's captures. Update this checklist as scaffolding is completed; do not mark a screen data-verified merely because its UI compiles.
