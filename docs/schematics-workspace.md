# Schematics workspace (Minecraft 26.2)

Requires the matching Baritone integration. Open **Schematics** from the module browser shortcut or workspace picker.

## Library

- Use **Choose file**, paste a full path and select **Import path**, or drag one schematic onto the screen. Formats come from the installed Baritone version.
- Import copies the file into Minecraft's `schematics` directory and selects it. Existing files are preserved; duplicate names get a numbered suffix. Import does not start a build or load into a different world after an asynchronous operation.
- Search ignores filename case, spaces, hyphens and underscores. Sort by name, newest modification time or largest file. Libraries beyond 256 files remain discoverable through paging.
- Hover a file for its full name, format, size and modified time. **Open folder** opens the library; **Refresh** picks up external changes.
- Join a world, select a file, and choose **Load & continue**. Loading parses the selected file and counts its materials in bounded tick work. Invalid file contents are reported at this stage.

## Build

- The loaded filename and dimensions identify the current schematic. X/Y/Z are its reference corner. **My position** fills the coordinate draft; **Apply origin** saves it.
- **Start build** applies the entered coordinates before starting. Unsaved edits survive switching tabs and resizing the window. Invalid coordinates disable placement actions.
- **Pause** retains the active build; **Resume** continues it; **Stop build** ends the session while retaining the loaded schematic.
- Loading, placement and runtime controls visibly disable when unavailable. A paused build still owns the file and origin: stop it before loading another file or moving its origin.
- Build and Resume close the menu only when the builder starts or continues successfully. The shared schematic controller still enforces world height and full-build configuration requirements.

## Materials

A dedicated page replaces the old collapsed materials section. Search by item name or registry ID, filter to missing items, sort, page, or export CSV. Compact columns show required, inventory and missing counts; hover names for registry IDs and stack totals. Blocks needing manual handling are identified separately.

Counts describe the whole source schematic. They do not deduct already-built blocks or include scaffolding, container inventory or shulker contents. A short inventory does not guarantee failure: Baritone may place available blocks and require restocking.

## Other fixes in this build

Flight search now resets pending scroll positioning when a long list becomes short, preventing results from being pushed below a blank gap. Description matching retains word boundaries, so `of light` no longer produces an unrelated `flight` match.

Sprint requests automatic sprint from current 26.2 input, during vanilla's movement update. Forward diagonals are accepted; Strict keeps vanilla food, vehicle, item-use and movement eligibility. Rage retains directional and optional stationary requests. GUI sprint requires active GUI Move with the current screen allowed. Keep Sprint hooks apply to the local player.
