# 33 feature guide — Minecraft 26.2

## Start here

Open the in-game GUI and search **All** modules. Search and commands accept names with or without spaces/hyphens, including the aliases below. Configure a module before enabling it; many features need a block/item list or keybind. Click a rounded module card to open settings. Its switch enables it; the star marks a favorite. Right-click also opens settings. HUD elements are added under **HUD → Edit → Add**; they are separate from modules.

This client targets Fabric Minecraft **26.2** and **Java 25**. Server rules still control reach, movement, potion effects, damage, and block placement. No feature is guaranteed undetectable. Compilation and headless regressions have been checked; live gameplay, visual appearance, multiplayer timing, and building completion speed require in-game testing.

## Combat

| Requested feature | Where to find it | Behavior / setup |
| --- | --- | --- |
| AimAssist | Aim Assist | Smooth, bounded camera aim toward visible nearby non-friend players. Hold attack by default. |
| AutoClicker | Auto Clicker | Hold-to-click is on by default; pauses in screens. Left Press mode; right clicks disabled by default. Old profiles retain saved modes. |
| Reach | Reach | Client interaction range settings; the server decides whether an action is accepted. |
| HitSelect | Hit Select | Paces manual entity clicks using weapon cooldown. Optional visible hurt-time filter. |
| Velocity | Velocity | Existing knockback scaling controls. Server corrections can override client movement. |
| Sprint | Sprint | Existing sprint automation. Strict mode is the default. |
| WTap | W Tap | Brief forward/sprint release after an attack while physically holding forward. |
| SilentAura | Silent Aura | Bounded server aim without moving the camera. Visible players, friends excluded, full weapon cooldown and held attack by default. Pauses for item use, potion sessions, Freecam and enabled Kill Aura. |
| Kill Aura | Kill Aura | Eating/potion helpers pause attacks without toggling it off; module info explains waiting reasons. |

Prefer one aim/attack automation module at a time. Increasing reach or click frequency cannot bypass server validation or weapon cooldown.

## Render

| Requested feature | Where to find it | Behavior / setup |
| --- | --- | --- |
| Search | Block ESP (alias Search) | Select blocks to outline through terrain. Loaded chunks only. |
| NameTags | Nametags | Entity labels, equipment and other configurable details. |
| Tracers | Tracers | Lines toward selected entity types. |
| StorageESP | Storage ESP | Storage block outlines. |
| ESP | ESP | Boxes and animated Hologram/Constellation/Telemetry styles. |
| AntiDebuff | Anti Debuff | Suppresses harmful effect checks on your local player. Does not remove server effects or server damage. |
| Arrows | Arrows | Direction indicators for loaded players outside the screen. Friends use friend colors. |
| Chams | Chams | Entity coloring/through-wall rendering controls. |
| Fullbright | Fullbright | Client brightness controls. |
| ItemESP | Item ESP | Dropped item labels and counts through terrain. |
| Projectiles | Trajectories (alias Projectiles) | Enable **fired-projectiles** for trajectories of airborne entities. |
| PropHunt | Prop Hunt | Outlines loaded falling-block and block-display prop candidates. These are not confirmed disguised players. Servers that disguise players as ordinary blocks need a server-specific adapter; their hidden identities cannot be recovered from ordinary block data. |
| SpawnerFinder | Spawner Finder | Normal/trial spawner outlines and labels. Loaded chunks only; bounded incremental scanning. |
| Trajectories | Trajectories | Held projectile aim prediction; accurate mode includes shooter motion. Random launch spread and server differences can change the actual path. |

### Euphoric visuals

The GUI defaults to **Appearance → Euphoric**: cyan accents, violet surfaces, rose highlights, layered card lighting and a slowly moving background aurora. Text remains opaque and steady. **Animated atmosphere** freezes the backdrop when disabled; **Atmosphere intensity** controls the lighting. Select **Custom** to use your saved colors.

Under **Modules → Studio → Look presets**, choose **Euphoric aurora** to configure Constellation ESP and cyan/violet combat effects. Enable **ESP** and/or **Combat Visuals** separately. Combat Visuals adds spectral orbit ribbons, fading halos, rings, sparks and scan bands around a target. It can also use your last manually attacked player. Adjust **intensity**, **animation-speed** and **detail** for your preference/performance; zero animation speed freezes target motion. Presets configure appearance and do not enable combat automation.

## Utility and world

| Requested feature | Where to find it | Behavior / setup |
| --- | --- | --- |
| Scaffold | Scaffold | Existing bridging assistance and block selection. |
| AutoPearl | Auto Pearl | Predicts an observed enemy-owned pearl collision and checks a path for your own pearl to that destination. Requires a pearl in hotbar/offhand, a known enemy owner, loaded path and a reachable destination. Unknown owners, obstructed/unreachable predictions and active item use are skipped. Prediction is approximate. |
| Panic | Panic | Bind an emergency key; disables all active modules once, including itself. |
| FakeLag | Fake Lag | Bounded outgoing movement delay. Keepalives are immediate. Actions flush older movement first. |
| Blink | Blink | Existing movement buffering/pulse/cancel controls. |
| BackTrack | Back Track | Brief outgoing movement delay after local attack attempts. Does not rewind opponents or change server reach. Fake Lag takes priority when both are enabled. |
| AutoTool | Auto Tool | Existing tool selection. |
| ChestSteal | Chest Steal | Moves fitting chest/shulker stacks while open, one at a time. Skips incompatible earlier stacks, respects slot/item component limits, offers Include/Exclude filtering, and delays retrying the same slot. An empty filter takes everything; module info explains when it waits. Disable Inventory Tweaks auto-steal if using this module. |
| FastPlace | Fast Place | Client block placement delay only; leaves other items' cadence alone. Fast Use retains its broader controls. |
| FreeCam | Freecam | Existing camera movement without moving your player. |
| MLG | MLG | Uses a hotbar/offhand water bucket when a landing surface enters normal block reach. Respects water-evaporating dimensions and item use; does not change your velocity. Pauses while No Fall is enabled. Placement is an attempt, not a guarantee. |

Movement queues preserve FIFO order, have a hard capacity, discard data on disconnect/correction, and pause after corrections. Blink takes priority over the new delay modules. Delays can still cause rubber-banding. Build with unrelated combat/movement automation disabled to reduce interference.

## Inventory

| Requested feature | Where to find it | Behavior / setup |
| --- | --- | --- |
| AutoArmor | Auto Armor | Existing scoring/equipment controls. |
| AutoHeal | Auto Heal | Uses effect-verified healing potions or golden apples from hotbar/offhand at a health threshold. Splash healing waits until grounded. Delay allows health updates to arrive. |
| InvCleaner | Inv Cleaner | Drops only explicitly configured item types, one stack at a time. Discard list starts empty; hotbar and equipped armor are preserved by default. |
| ThrowDebuff | Throw Debuff | Bind to throw one harmful splash potion toward a visible enemy. Checks the predicted collision and skips friendly/self splash areas. Requires at least four blocks of separation and a reachable path. |
| Throwpot | Throw Pot | Bind to use one healing item from hotbar/offhand; auto-disables after the use. |
| InventoryManager | Inventory Tweaks (alias InventoryManager) | Existing sorting, filtering, transfer and anti-drop controls. Crafting-grid storage/XCarry defaults off for new profiles. |
| ArmorSwitch | Armor Switch | Configure set A/B item types and a switch key. Equips one missing humanoid armor piece at a time. Pauses when Auto Armor is enabled. Missing pieces are left as they are. |
| Refill | Refill | Restocks one configured empty hotbar slot with a healing item from the main inventory. Never overwrites an occupied slot. Auto Replenish handles general item refills. |

New helpers stop on screen changes and avoid external containers/carried cursor stacks. Potion sessions own their selected slot and return it only if you have not manually changed selection. Deferred actions are scoped to an activation/world/player; old callbacks cannot act after a new session starts. Auto Eat, Auto Gap and potion helpers wait for one another.

## Legit/HUD features

| Requested feature | Where to find it | Behavior / setup |
| --- | --- | --- |
| Freelook | Free Look | Third-person perspective controls; choose camera mode to rotate your perspective independently. |
| NoClickDelay | No Click Delay | Removes client miss delay; weapon cooldown still applies. |
| KeyStrokes | HUD: KeyStrokes | Keyboard/mouse presets and optional **show-cps**. |
| Clock | HUD: Text → Clock / Real time | Local real-world time. |
| Potion Status | HUD: Potion Status | Existing active-effect timers. |
| Armor Status | HUD: Armor Status | Existing armor/durability display. |
| Compass | HUD: Compass | Existing heading display. |
| Time Changer | Time Changer | Client world-time appearance. |
| Coords | HUD: Text → Coords / Position | XYZ coordinates. |
| FPS | HUD: Text → FPS | Frame rate. |
| Reach Display | HUD: Reach Display | Eye-to-hitbox distance of your last local player attack attempt. Does not confirm a successful server hit. |
| Inventory Blur | Blur (alias Inventory Blur) | Enable inventory screens in Blur settings. |
| Clear Water | Clear Water | Removes fog only while the camera is underwater. |
| Block Overlay | Block Selection (alias Block Overlay) | Configurable crosshair block highlight. |
| Hit Color | Hit Color | Configurable tint for hurt living entities. |

## Other

| Requested feature | Where to find it | Behavior / setup |
| --- | --- | --- |
| Internal GUI | In-game GUI | Responsive categories/search, readable text, scrollable menus and corrected input capture. |
| Profiles | Profiles page | Existing multiple named configurations. |
| Friends | Friends page | Existing friend list; combat helpers respect it. Splash checks also avoid nearby friends. |
| Macros | Macros page | **hotbar-item** selects an item before existing macro messages run. Air leaves selection unchanged. Missing items cancel the macro. |
| Text GUI | HUD: Text GUI | Existing enabled module list. |
| Duel Info | HUD: Duel Info | Session melee hits from received server damage events and observed potion drinks/throws, per player. Bind **reset-session** before a duel. Remote observations can be incomplete when owners/uses are not sent by the server; not an authoritative duel score. |
| Target Info | HUD: Target Info | Health, equipment and details of the last attacked player; falls back to a nearby valid target after five seconds. |
| Radar | HUD: Radar | Actual rotating 2D player radar, with above/below markers and friend colors. The old Player Radar name list is also retained. |
| Rearview | HUD: Rearview | Rear-player awareness overlay showing up to four nearby players behind you. A live rear camera is not implemented; it requires a separate render pass and in-game graphics validation. |

## Schematics and building

Use `.schematic screen` to open the guided schematic workspace before or after loading. Search display names or registry IDs, select **Only missing items**, and sort by name, missing items or required totals. Manual requirements remain visible in the shortage filter. Inventory refreshes twice per second; large lists use 16-row pages and a scrolling view. **Materials CSV** saves the complete report. Totals still cover the full source, including already built blocks.

See [README schematic commands](../README.md#schematic-building-baritone-required) for loading, full materials lists, shortages and CSV export. The safer build guard temporarily disables sprint/parkour/risky descents, caps planned falls and builds one layer at a time. It restores settings when building ends and preserves manual edits. Fewer planned falls are intended to reduce retracing; actual completion-time improvement has not been measured in this sandbox.

## Keeping this maintainable

Shared helpers own targeting math, projectile aim, queue deadlines, slot ownership, palette interpolation and radar projection. The code uses the existing event/settings lifecycle and public Minecraft APIs where available. Existing injection points are reused for miss delay, item cadence, harmful-effect checks, damage tint and underwater fog. Registry IDs/config keys remain stable; aliases and display names provide discovery without replacing existing profile keys.

For each Minecraft update:

1. Update the declared Minecraft/Fabric/Java/dependency versions together and resolve compile/API changes.
2. Run `./gradlew build` using the declared JDK. The mandatory regression runner covers persistence/lifecycle/recon/combat, materials, GUI input, queue ordering/capacity/concurrency, ownership, aim, radar, potion classification and continuous visual colors.
3. Launch a client to verify mixin application, HUD rendering, readable menus, resizing, dropdowns and the visual preset. These checks require the actual renderer.
4. In a disposable world/server, verify one manual attack, eating/healing handoff, key release, armor swapping, item macros, pearl prediction, water clutch, corrections/disconnects, and a small layered schematic with known supplies.
5. Document server-specific limitations. Do not infer hidden identities or successful damage from client attack attempts.

No implementation can guarantee two years of compatibility across Minecraft updates. These boundaries and checks make failures easier to isolate and repair.

## Xray and RayTraceAntiXray

[RayTraceAntiXray](https://github.com/stonar96/RayTraceAntiXray) is a server-side add-on for Paper Anti-Xray. Source inspected at commit `9014d7aab524fb6f9805cd3d40a501873d36190f`: its chunk controller writes replacement block states and removes hidden block-entity entries; its ray-tracing task reveals or re-hides blocks according to player visibility. Client rendering cannot recover block identities that were never sent. The upstream README currently targets Paper 26.3; this client still targets 26.2.

**Ore Memory** (also searchable as **Revealed Xray**) adds a useful, bounded capability: it remembers recently observed selected block types from loaded chunks and received block updates. Cyan means the same block is currently observed; amber means a dated **last seen** location that may have been hidden, mined or replaced. Memories expire after 120 seconds by default, are removed on server air updates/chunk unload, and clear when leaving or changing worlds. Enable labels to distinguish current observations from history. It sends no reveal/probing requests and creates no fake ore identities. On servers sending decoy ores, observations can also be decoys.

Open **Xray → Configure Ore Memory** or search for **Ore Memory**. Enable it separately. It can retain locations after a server re-hides blocks it previously revealed; it cannot bypass withholding of never-revealed ores or chests. These limits are fundamental to the data available to a client, not a missing rendering option.

## Flight and AntiFly

Flight now offers four modes:

- **Server** only flies when the server grants the ability; it preserves the server's flight speed and accepts ability updates, including revocation.
- **Auto** prefers granted abilities and otherwise uses local Velocity controls.
- **Velocity** uses normalized directional input, independent vertical controls, and bounded acceleration/braking. Set **acceleration** to zero for instant response. Opening a screen stops directional movement. Freecam, Elytra Fly, gliding and riding pause Flight controls.
- **Abilities** changes local flight flags; it does not grant permission on the server.

**Server Recovery → pause-on-correction** defaults on: position corrections pause Flight for two seconds; three corrections within ten seconds hold controls until **resume-flight** or re-enabling the module. Teleports also count as corrections; this is not automatic AntiFly identification. Fluid/sneak modifications pause with controls, and ability restoration is scoped to the same player/world. **Anti Kick → None** is the new default. Existing profiles retain their setting; Normal/Packet are optional vanilla floating workarounds and do not defeat server flight verification.

[AntiFly](https://github.com/cev-api/AntiFly) was inspected at commit `a7d729715c846cca66ea10f7edd9906c236dcfd6`. Both Paper and Fabric verify actual support and airborne motion, include a sustained-air backstop, and exempt server-granted flight. A client cannot create that server permission. Use **Server** mode with a server-provided flight ability; server operators can configure AntiFly exemptions. Unauthorized hover/Velocity flight can still be corrected or penalized, including hunger/health penalties in Hunger Mode. No reliable bypass or live compatibility result is claimed. Flight control/recovery regressions run in the build; multiplayer flight still needs in-game testing.

## Survival Bot (Baritone required)

Open **World → Survival Bot → settings → Start / Resume** while standing on solid Overworld ground. That position becomes your camp. Enabling the module alone does not start it. Install a compatible Baritone for Minecraft 26.2 first.

The default plan gathers wood, crafts a table and basic tools, mines and smelts iron, then works toward:

- Full diamond armor, equipped automatically; diamond pickaxe, sword, axe, shovel and hoe.
- Shield, empty bucket, shears and a white bed carried in inventory.
- Crafting table, furnace and a chest placed at camp; returns to camp on completion.
- 32 cooked beef/pork/mutton, 64 torches, 16 logs and 64 cobblestone or equivalent stone.
- Eight spare diamonds and 16 spare iron ingots. Food and spare quantities are configurable.

Recipes and progress are checked against inventory. Worn harvesting tools trigger replacement. Mining uses conservative fall/parkour settings; owned Baritone settings are restored when stopped. Compatible superior equipment already owned counts toward the kit.

**Stop** cancels the bot's job. **Start / Resume** replans from your current inventory and remembers the camp within the same world session. Disconnecting or changing worlds clears the camp; death, config loading and reconnecting never automatically restart a run.

Watch the status in settings for pauses: low health, fire, empty food, stalled resources, inaccessible workstations, conflicting modules or insufficient inventory space. Four free inventory slots are required. By default it drops excess cobblestone, cobbled deepslate, dirt and gravel, preserving at least 64 of each and the quantity carried at Start. Disable `discard-mining-spoil` to prevent this. Store surplus valuables manually before resuming.

This plan requires reachable resources and adult cows/pigs/sheep for food, plus shearable white sheep for the bed. Named animals are excluded. It uses vanilla recipes; protected areas or altered server recipes can stop progress. Combat, flight, inventory automation and packet-delay modules that conflict must be turned off; the status identifies them.

The bed is carried, the bucket is empty and the chest starts empty. This does not build a house, farm, enchanting setup or Nether progression. A live Minecraft run is still needed to verify crafting clicks, world interaction and unattended completion; successful compilation and planner checks do not establish those behaviors.

## Redesigned workspace

The top tab bar and per-module Configure row are removed. Modules live in a centered workspace with rounded cards, compact enable switches and favorites built into each card. Click the card body for settings; right-click works too. Categories, All, Favorites, Enabled and Visual Studio are in the sidebar. HUD, Profiles, Friends, Macros, GUI and other pages are under Workspace. On narrower displays, filters and a workspace picker replace the sidebar. Utility pages have a small workspace picker at the bottom left.

Search still finds module names, aliases, descriptions and settings; Ctrl+F focuses it. The grid adapts from one to three columns, and long titles/descriptions are bounded inside their cards. Rounded controls and quieter window headers carry the new style into settings.

Survival Bot crafting now waits for expected cursor, source and crafting-grid state to settle after each ingredient click. It does not repeat an unacknowledged click and only reports a craft complete once the result reaches inventory. A rejected or persistently mismatched click pauses with an inventory synchronization message.

### Guided schematics

Choose **Workspace → Schematics** (Baritone required), or use `.schematic screen` even before loading a file. Select a file from the Minecraft `schematics/` folder or type its filename, then click **Load selected file**. The file list uses the formats supported by your installed Baritone; **Refresh files** picks up newly added files.

After loading, use **Use my position** or enter X/Y/Z and click **Apply coordinates**. The origin is the reference corner used by Baritone. Review the materials below: **Need**, **Have**, **Missing** and stack counts. Search, sorting, shortages and pages keep the report readable; **Materials CSV** exports the complete list. Manual blocks are identified separately.

**Build** starts and closes the GUI; **Pause**, **Resume** and **Stop build** control the builder. Stop retains the loaded schematic. Reopen the workspace to inspect status or change the origin after stopping. Status, scan progress and errors appear on the page. Totals describe the entire schematic, including already-built blocks; they exclude temporary scaffolding and container contents.
