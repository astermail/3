# Client fixes and automatic RTP (Minecraft 26.2)

## Automatic storage search

Open **Random Teleport / RTP**, click **Configure** while disabled, and set **Mode** to **Find Storage**. Enable the module to start. The default minimum is **31 storage blocks in one chunk**, meaning more than 30.

- Set **Command** to the server's RTP command, with any required arguments. The server must support it and allow you to use it.
- Set **Request interval seconds** at least as high as the server's cooldown. The default is 30 seconds.
- A request waits for an observed position change of at least **Arrival distance**, or a dimension change. Normal long-distance travel can also satisfy that check; avoid moving during a queued request.
- After arrival, the module scans nearby received chunks for **Scan seconds**, then waits for the command interval before requesting again. Adjust scan time for slow chunk loading.
- **Storage blocks** selects which types count. Each block entity counts once; both halves of a double chest count separately. The threshold is per chunk, not the combined count across the scan radius.
- On a match, searching stops and records the coordinates in Stash Finder with server/dimension and observation times. Stash Finder does not need to be enabled.
- **Stop search**, disabling the module, death, disconnection, a teleport timeout, or the maximum-request limit ends the search. A timeout requires a new activation after resolving the server command/queue/cooldown.
- **Once** retains the single-request behavior. Session timing/threshold settings are captured at activation; restart after changing them.

The client searches only block entities in chunks the server has delivered. Increasing scan radius does not reveal unloaded terrain or whole-map bases. Storage count indicates observed containers and selected utility blocks, not proof of a player base.

## Menu and HUD

Disabled modules now offer **Configure** so settings can be prepared without activating gameplay behavior. Keybind and feedback controls remain available while disabled. Other disabled features keep their contextual settings hidden until explicitly configured.

**Detach status** adds a movable HUD monitor and opens the HUD editor. Reopen the same module and select **Reattach status** to remove its monitor. Repeated detaching no longer creates duplicates. Alternatively, hover the panel in the HUD editor and press **Delete**. Pinning locks movement; it does not prevent removal.

Nested GUI clip bounds now intersect all four edges correctly, preventing overflowing text/content when a child starts outside its parent.

## Storage persistence

Stash discovery batches snapshots once per 20 client ticks and writes on one background worker. Disabling, leaving a world, switching servers, deleting and clearing submit pending changes immediately. Pending saves are coalesced by server folder and kept in order. Reopening before a write completes sees the newest snapshot instead of stale disk records. Files use staged replacement; failures are logged and retain the pending snapshot for later reads/saves.

Previously selectable types such as brewing stands, crafters, bookshelves and decorated pots now contribute to totals under **Other selected storage**. JSON and CSV preserve these counts; previous CSV formats remain readable.

## Weapon selection

Auto Weapon supports Sword, Axe, Mace, Spear, Trident and Any preferences for ordinary melee attacks. It checks the candidate's actual attack range, durability and estimated fully charged damage. A missing preferred weapon no longer leaves an available alternative unselected, and the preference threshold includes equality. Equal candidates preserve the current hotbar slot.

Damage prediction removes the held item's actual attack modifiers before applying a candidate, preserving other effects and preventing custom weapon modifiers from stacking incorrectly. This does not automate spear charging/throwing or guarantee damage outcomes on modified servers.

KillAura now considers compatible hotbar weapons before rejecting a target for the held item's shorter range. Auto switch selects a weapon that reaches the primary target, shield breaking applies to that target, and queued attacks recheck the actual held weapon's reach.

## Clean client shutdown

The optional Baritone integration now creates daemon workers so its perpetual chunk-cache loops cannot hold Java open after Minecraft exits. A shutdown hook allows submitted cache saves up to five seconds to finish. Very large cache exports can exceed this bounded grace; it is not a guarantee that every in-flight chunk is packed at exit. Normal world-unload saving remains enabled.

## Verification

The full Gradle build and regression suite passed. Native Minecraft26.2 checks confirmed configuration while disabled, detach/reattach captions and removal, a local teleport search stopping at30barrels+1crafter, saved coordinate context, denied-command timeout, Survival Bot crafting five diamond tools/weapons and four armor pieces from supplied materials, Auto Weapon selecting an available axe when a preferred sword was absent, and normal world save/Quit after the Baritone shutdown fix.

These local fixtures do not establish multiplayer RTP permissions/cooldowns, full survival resource gathering or every module's gameplay behavior. Large cache exports, frame-time guarantees and exhaustive client verification remain untested.
