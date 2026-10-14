<p align="center">
  <img src="src/main/resources/assets/client33/icon.png" alt="33 logo" width="96" />
</p>

<h1 align="center">33</h1>
<p align="center">A Minecraft Fabric utility client for 26.2.</p>

## Build

Use Java 25 and run `./gradlew build`. The JAR is written to `build/libs/`.

## Install

Install Fabric Loader for Minecraft 26.2, then put the built JAR in your Minecraft `mods` folder.

## Contribute

Open issues and pull requests in the [33 repository](https://github.com/GasPoweredCake/33). Keep the existing license headers on source files and follow the surrounding code style.

## License

33 is distributed under [GPL-3.0](LICENSE). Original copyright and source attribution remain in the source headers.

## Schematic building (Baritone required)

Put a schematic in Minecraft's `schematics/` folder. Use your configured client command prefix (the default is `.`):

- `.schematic load house.schem` — load without starting a build. Formats come from your installed Baritone; filenames can contain spaces.
- `.schematic materials` — show required items, stacks, current inventory, and shortages. Use `.schematic materials 2` for the next page.
- `.schematic screen` — open the guided file/origin/build workspace with searchable materials, shortages and CSV export; loading first is optional.
- `.schematic export` — save every item and manual block requirement to `client33/materials/schematic-materials.csv` in your game directory.
- `.schematic origin ~ ~ ~` — set the build origin before starting; otherwise your position when loading is used.
- `.schematic build` — start Baritone with the loaded schematic. Shortages are reported so you can plan restocking.
- `.schematic pause`, `.schematic resume`, `.schematic status` — control the build and check loading progress or builder activity. Baritone's `cancel` command ends a build.

Materials count the **entire source schematic**, including blocks already built. Doors, beds and tall plants share one item between their halves; double slabs need two. Totals exclude temporary scaffolding, container contents and extra fluid needed for waterlogging. Blocks with no inventory item are listed separately for manual handling. Block entities and their contents still require manual setup. Baritone's substitutions, skipped blocks, selection-only, map-art and repeated-build settings must be disabled before starting a full schematic with this command. Rotation and mirroring are handled by Baritone.

If Baritone repeatedly revisits difficult placements, try its `buildInLayers` setting with `layerHeight` set to `1` and `layerOrder` set to `false`. Check that the selected origin is correct, chunks are loaded, the server permits block placement, and supplies are accessible. The assistant reports requirements; it does not automatically fetch materials.

## Kill Aura pauses

Kill Aura stays enabled during AutoEat and AutoGap sessions and resumes when their eating pause ends. Its module info shows waiting reasons such as eating, using an item/mining, no valid target, missing weapon, Crystal Aura placement, cooldown, or no server packets. Stale world-time samples use vanilla cooldown timing; the lag pause checks incoming server traffic. Target filters and weapon settings still apply.

## GUI readability and controls

The module browser opens on **All** modules. Categories appear in the sidebar on wide screens and in a dropdown on smaller screens; Visual Studio remains available through the **Studio** filter. The top navigation switches to a page dropdown when the tabs cannot fit. Changing GUI scale rebuilds the layout. **High contrast text** is enabled by default in GUI settings; turn it off to use your custom text colors.

Dropdown menus open above their control when needed and scroll for longer lists. Arrow keys change the selected value; Enter or Escape closes the menu. Buttons activate on release inside the control, so dragging away cancels a click. Descriptions, long setting names, and tooltips wrap within their available width.

## More reliable Baritone building

**Config → Misc → safer-baritone-building** is enabled by default. While Baritone's builder is active, the client temporarily disables sprinting, parkour and risky diagonal descents, caps planned falls at one block, and builds one layer at a time from bottom to top. This applies to both Baritone's build command and `.schematic build`. Walking can take longer between placements, but the settings aim to reduce falls and repeated climbing.

The previous Baritone settings are restored when building ends, is cancelled, or you leave the world. Manual changes made during a build are preserved. Disable the option to use your own movement/layer settings. Saving settings from the client's Baritone page saves the original values while the build overrides are active. These controls reduce planned movement risk; knockback, server corrections and other movement modules can still cause falls.

## Expanded modules, HUD and visuals

See the [feature guide](docs/FEATURES.md) for every requested feature, its GUI name, setup and limitations. New modules include Aim Assist, Hit Select, W Tap, Silent Aura, Auto Heal, potion helpers, Auto Pearl, Panic, bounded Fake Lag/Back Track, Chest Steal, Inv Cleaner, Armor Switch, Refill, MLG, Fast Place, Arrows, Item ESP, Spawner Finder, Clear Water and Hit Color. Search accepts compact names such as `AimAssist` and aliases such as `Search` and `InventoryManager`.

Add **Radar**, **Reach Display**, **Duel Info** and **Rearview** through the HUD editor. Radar is a 2D view; Rearview currently lists players behind you rather than rendering a second camera. **Target Info** follows your last attacked player. Macros can select a configured hotbar item before running messages.

**Appearance → Euphoric** adds cyan/violet/rose lighting and a slow background aurora. **Studio → Look presets → Euphoric aurora** configures spectral combat ribbons and Constellation ESP. Enable the visual modules separately; adjust atmosphere intensity, animation and detail to taste. Live graphics and multiplayer behavior still need in-game verification. Minecraft updates require compatibility work; no two-year guarantee is implied.

**Ore Memory** keeps expiring, labelled observations of ore locations, including blocks later hidden again by a server. Open it from Xray settings or search for it. It cannot identify blocks never sent by server-side anti-xray; amber markers are dated history, not confirmed current ores. See the [Xray limitations](docs/FEATURES.md#xray-and-raytraceantixray).

## Flight controls and server compatibility

**Flight → Server** uses server-granted flight and preserves its speed. **Auto** prefers granted abilities and otherwise uses Velocity controls. Velocity now has bounded acceleration/braking, normalized diagonal movement and independent vertical speed. Server corrections pause controls; repeated corrections hold Flight until its resume key or re-enabling. The module shows the waiting reason and accepts server ability revocations. Optional Anti Kick pulses address vanilla floating only; they cannot guarantee flight through AntiFly's server checks. See the [Flight guide](docs/FEATURES.md#flight-and-antifly).

## Smarter chest transfers

Chest Steal scans past stacks that cannot fit and respects item components, slot restrictions and stack limits. **filter-mode** supports Include and Exclude; an empty item list still takes everything. **retry-delay-ticks** limits repeated attempts on the same slot while other eligible slots continue. The module info distinguishes no matching items, no inventory room, a held cursor and waiting slots. Transfers remain subject to server acceptance.

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

## Module workspace

Click a rounded module card for settings; use its switch to enable and its star to favorite. The sidebar contains categories and Workspace pages. Compact layouts use a workspace picker. The old top tab bar and Configure rows are removed. Ctrl+F focuses module search.
