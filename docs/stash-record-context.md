# Stash coordinates: server, dimension and observation times

Stash Finder now records the server address (or singleplayer save identity), full dimension identifier, first observation and latest observation with each coordinate. Repeated observations preserve the original first-seen time. Times use UTC and indicate when this client saw the storage, not when someone built it.

The list shows the current server, each record's dimension and last-seen time. Hover a row or open its details to see the full server and both timestamps. Chat notifications include the same context. Tracers are cleared on server/dimension changes, and Goto/chat navigation checks that the current server and dimension match the record.

Records are saved separately per server under `client33/stashes/by-server/<server-identity-hash>/stashes.json` and `stashes.csv`. The hash avoids collisions from replacing punctuation in server addresses. Server and dimension fields also remain in the records and CSV. Addresses used as aliases are separate identities; different backend worlds behind the same server address cannot be distinguished without additional server information.

Old JSON/CSV files are read when no new-format library exists. Their original files remain intact. Entries without provenance display `unknown` server/dimension and `Unknown (legacy)` times. The client does not assign the currently connected server or dimension to those historical coordinates. Reobserving a location creates a verified record separately. Navigation/tracers are unavailable for unknown legacy context.

Clear Chunks now persists the empty library, so reopening it does not reload deleted records from the old files. JSON and CSV use complete staging files before replacement. An unreadable library is preserved and automatic saves are blocked for that loaded library; the menu reports this state.

The current implementation applies to Stash Finder records. Normal waypoints already have a separate per-world/server library and dimension settings; this update does not migrate those libraries or add waypoint timestamps.

Verified with regression fixtures for cross-server/dimension identity, revisits, legacy JSON/CSV, quoted export fields, UTC formatting, clear/reload, atomic file cleanup and malformed input. Live switching between multiplayer servers and native rendering of the updated stash list have not been exercised in this sandbox.
