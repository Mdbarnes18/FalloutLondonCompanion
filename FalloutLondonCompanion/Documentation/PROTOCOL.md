# Protocol

The current implementation follows the established Fallout 4 Pip-Boy companion protocol as a compatibility boundary.

- UDP discovery: port 28000
- TCP connection: port 27000
- packet header: little-endian UInt32 payload size + one-byte message type
- type 3: data update
- type 4: local map update
- type 5: JSON command request
- type 6: command response

Known command IDs from the verified reference implementation include UseItem (0), DropItem (1), SetFavorite (2), SortInventory (4), ToggleQuestActive (5), SetCustomMapMarker (6), FastTravel (9), ToggleRadioStation (12), RequestLocalMapSnapshot (13), and ClearIdle (14).

The Swift object-graph decoder remains deliberately isolated until each London data path is verified against the supplied captures. No London-specific packet behavior is invented here.
