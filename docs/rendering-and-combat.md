# Rendering, combat timing and search (26.2)

## Rendering

- Mesh allocation now reserves the complete requested batch on its first allocation and uses checked sizing before native buffer writes. Exact-fit batches reuse their buffers.
- Fills render before outlines in the shared 2D/3D renderers. Line opacity follows the same alpha setting as fills.
- Mesh submission releases its shared state and model-view stack even when rendering fails.
- Projection rejects nonfinite coordinates and points at the camera plane. Directional arrows retain behind-camera information.
- ESP and structure labels use bounded collision placement and small dark backgrounds. Offscreen structure markers no longer consume the visible-label budget.
- Custom text shadows use instance color state and return the foreground advance, fixing drift when shadowed text runs are chained.

## KillAura

Weapon recharge is mandatory. A saved `custom-delay = true` and `hit-delay = 0` no longer bypasses recharge. Custom delay now means an **additional minimum** between attacks.

Each weapon charge is used for one target. Other candidates remain available for target selection; increasing `max-targets` does not generate a burst of weak attacks. Pending rotation callbacks recheck player/world, target validity, weapon charge and pause conditions before attacking. Actual item attack range is checked using Minecraft 26.2's range API.

`wait-for-hurt-recovery` is enabled by default. It prefers targets outside their observed hurt animation. This is a client observation, not a server damage acknowledgement. Server timing uses received tick estimates when enabled, with a fallback when time packets become stale. Manual attacks also update the local cadence while KillAura is enabled.

The status reports charging, waiting for recovery and pause reasons. Select the mob types to attack and hold a configured weapon (or enable auto-switch). This update does not implement spear use/charge attacks or guarantee survival against mobs.

## Search and random teleport

Typing in the Modules search searches every module, including modules outside the selected category, favorites/enabled filters and hidden browsing list. Clearing the query restores the current browsing filters. The search viewport retains its height so results do not move the input while typing. Search includes aliases, names, descriptions, setting names and setting descriptions. Separators and case do not prevent matches, and words can match different fields.

Useful queries:

- `base finder`, `base hunter`: Stash Finder, which records storage in received chunks.
- `chest esp`, `container esp`: Storage ESP.
- `sign esp`, `poi esp`: Structure ESP.
- `survival mode`, `baritone survival`: Survival Bot.
- `rtp`, `wild`, `random tp`: Random Teleport.

Random Teleport is a new one-shot helper. Enable it to send the configured command once, then disable automatically. The default command is `rtp`; change it to the server's command (for example `wild`). The server must provide the command and controls permission, cooldown and destination. The helper cannot teleport on servers without that feature.
