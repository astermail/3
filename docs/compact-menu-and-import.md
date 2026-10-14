# Menu, HUD and schematic workspace — Minecraft 26.2

## Layout and rendering

The module browser uses horizontal category tabs and compact, paged columns (two or three on wider windows; a narrow-window fallback remains usable). Module controls use section tabs and paged column groups. Only the visible page's controls are created. Disabled modules hide their detailed controls until enabled. Existing setting visibility predicates remain effective, and scripts can declare dependent sliders.

The menu uses the client's lightweight widget/layout layer and existing Minecraft GPU pipelines, with dirty layout calculation, batched meshes, scissoring and cached fonts. It does not embed a browser, Electron, Lua or Python. It does not guarantee zero frame drops. Scripts → CPU timing reports the last 256 menu CPU submission samples; GPU time and total game frame time are excluded.

Click a module row for settings, use On/Off to toggle, or star it for Favorites. Hover for descriptions. Ctrl+F searches names, aliases and setting names. Utility pages share a bounded header, page picker and Close button. Schematics has a direct shortcut when Baritone is installed.

## Baritone and import

Baritone's default page groups common controls by Movement, Blocks & building, and Path display. Search and Show advanced settings retain access to every setting.

1. Open Schematics → Library. Choose file, paste a full path and Import path, or drop one schematic onto the screen.
2. The client copies it to the schematics folder on a worker thread and selects it. Supported extensions come from Baritone. Collisions get numbered filenames; existing files are preserved. Partial imports use temporary files.
3. Search or sort the library, then Load & continue in a world. Imports do not automatically load or build.
4. Build → enter origin coordinates, My position or Apply origin. Start build applies the current coordinate draft. Pause, Resume and Stop reflect the builder's current state.
5. Materials has searchable, paged Need/Have/Missing columns and CSV export.

Import is local copying. Directories, symlinks and unsupported formats are rejected. File-picker availability depends on the OS; path import, drag-and-drop and Open folder provide alternatives. See [the workspace guide](schematics-workspace.md).

## Detachable HUD and style

Module settings → Detach status creates a persistent module monitor and opens the HUD editor. Drag it to move, drag its lower-right handle to resize, and right-click it to enable Pin position. Pinning prevents both movement and resizing. HUD → Add status monitor creates an ESP monitor directly. Other HUD elements also support position pinning.

HUD → Euphoric preset updates existing text/module-list colors and panels without moving elements or changing which gameplay modules are enabled. It selects the custom font and restrained cyan/violet/rose accents instead of full-spectrum Rainbow. Rainbow remains an explicit optional color mode. Custom-font HUD scaling is normalized once, avoiding the previous squared size change; glyphs are rasterized above display resolution and downsampled.

GUI → Export JSON writes `client33/theme.json` in the Minecraft game directory. Edit it, then Load JSON. The file controls scale, independent fontScale, rounding (0 for sharp corners), gradient, and RGBA arrays for accent, gradientEnd, border and surface. The entire file is validated before application. More theme controls remain available in the GUI settings. Theme loading selects Custom appearance so supplied colors apply.

## Runtime controls with Starscript

Scripts → Browse or enter a file path → Load / Reload. UI scripts are ordinary UTF-8 `.starscript` templates using the existing engine:

```text
{ui.checkbox("Practice", "enabled", "Enable practice controls", false)}
{ui.slider("Practice", "range", "Range", 4, 1, 8, "enabled")}
{ui.textbox("Practice", "label", "Label", "Training")}
```

The first argument selects the script-defined section/tab; the second is a stable control key. The optional final slider argument names a previously declared parent checkbox in the same tab. Labels are presentation only. Other client Starscript expressions, including text HUD expressions, can read:

```text
Practice range: {ui.value("Practice", "range")}
```

Loading replaces the current script UI transactionally. A failed script preserves the previous UI. Matching control keys preserve session values across reloads; no automatic startup execution is performed. Limits: 256 KiB source, 256 controls and 16 tabs. Controls are paged to bound widget creation. The declarations create local UI values; they do not silently bind a range slider to an unrelated gameplay module.

## Below-deepslate ESP

ESP and Storage ESP now default to Full Height: range is horizontal, covering tracked entities/containers across the loaded dimension height, including negative Y. Disable it for spherical range. True 3D distances remain in labels, nearest-first storage ordering and near fade. Overlay depth already passes through terrain. This cannot discover players or bases whose data the server has not sent, or unloaded chunks.

## Stardust comparison

Inspected `0xTas/stardust` revision `0cdba3913efbc7fe974d4fcc8ae053132bdbb92b` (GPL-3.0); no source was copied. Its relevant exploration modules include ChatSigns/SignHistorian, TreasureESP, VaultESP and Archaeology. LoreLocator highlights metadata in accessible item inventories; StashBrander renames items and is not a stash detector.

This client has Stash Finder (storage counts and saved coordinates), Storage ESP, Structure ESP (loaded POIs/landmarks and server-provided debug bounds), Movement Heatmap and Faction Intel (observed server text). Structure ESP now also finds all sign types, labels received front text, and includes suspicious sand/gravel among default landmarks. Existing saved landmark lists retain their selections; add these blocks manually if needed. Sign markers are optional. Scanning remains budgeted and spans the loaded world's full height.

This is not full Stardust feature parity: sign restoration/history, old-sign heuristics, inventory metadata highlighting, treasure-specific classification and ominous-vault routing are not implemented by this update. A loaded sign, storage block or workstation is evidence to inspect, not proof of a player base. Neither client can retrieve undisclosed player/base locations across an entire multiplayer map. Server tracking and chunk delivery limit discovery; increasing ESP range cannot force the server to send missing data.
