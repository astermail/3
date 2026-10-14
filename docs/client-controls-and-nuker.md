# Minecraft 26.2 fixes

## Nuker

Nuker defaults to **Selected Block** selection. Enable it, close the menu, aim at
an actual block and right-click to select its type. Selection consumes that click,
so it does not also place/use an item. Right-click another block to change type.
The module info shows the selected block and its current status.

Choose **Area** to mine all blocks allowed by the whitelist/blacklist. Existing
Sphere, Cube, Uniform Cube, All, Flatten, Smash, sorting, interaction and rendering
options remain available. Flatten excludes blocks below your feet. Whitelist and
blacklist filters also apply to Selected Block mode.

- **Mine delay ms:** delay between completed blocks. The existing tick delay is
  retained; the larger of the two delays applies.
- **Distance check:** limits targets to normal player reach. Disabling this only
  changes client selection; the server still controls which blocks can be broken.
- **Rotate / Silent:** rotate toward the target; Silent keeps your local camera
  still. Rotated mining processes one target per tick. With rotation off and no
  delay, instantaneous breaks can use the configured per-tick budget.
- **Packet mine / Adaptive:** use packets for instant breaks and normal mining
  for hard blocks. **Always** accumulates hardness progress before sending STOP,
  then waits before retrying a rejected attempt.
- **Only suitable tools:** checks harvesting requirements, while allowing blocks
  that do not require a particular tool.

The miner holds a valid target until completion rather than switching whenever
another block becomes closer. Opening a GUI, using an item, disabling the module,
changing selection or leaving the world cancels pending work. Bounding-box and
target colors now have separate setting names so they save independently.

## Shared controls

- Mouse-wheel changes now commit settings that apply on slider release, including
  GUI scale.
- Sliders accept the left mouse button and only its release finishes the drag.
  Fixed-value/tiny sliders no longer calculate non-finite values.
- Integer inputs clamp to their valid bounds and show the accepted value.
- Plus/minus buttons apply and display fractional bounds correctly; integer
  increments do not overflow.
- Enter commits text once. Focus-loss callbacks can reload a screen without
  recursively calling themselves, and widget traversal tolerates those reloads.

## Validation

The build runs regression tests for these controls, target retention, mining
cooldowns and packet progression, alongside the existing suite. A real 26.2
client startup completed initialization and mixin audit under Xvfb/software
rendering. In-world Nuker behavior and multiplayer server acceptance still need
live gameplay verification.
