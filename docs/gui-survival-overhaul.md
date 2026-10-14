# GUI and survival update

## Interface

Choose **GUI → Appearance → Crimson** on an existing profile to use the new
opaque charcoal panels and red accents. New profiles use Crimson by default;
Euphoric and custom color settings remain available.

Categories now sit across the workspace header, with library filters and other
pages in the sidebar. Narrow windows use dropdowns. Module cards have separate
settings, favorite and enable targets. Returning from a module preserves the
library scroll position; changing search or filters resets it.

Settings search matches names, descriptions and group names, opens matching
groups without changing their saved collapse state, and reports empty results.
Module-specific actions appear above settings, so Survival Bot's Start / Resume
is immediately accessible.

## Survival crafting

The bot first requests one craft using an unlocked, craftable server recipe.
Minecraft chooses and places the ingredients, including compatible wood variants.
It waits for a stable grid and the expected result, then collects that result once.
If no suitable unlocked recipe is available, it uses the individually acknowledged
click transaction. A rejected server placement stops with a status message; it
never falls through to manual clicks over a partially populated grid.

Workstation placement now waits for the world update before recording a station.
Inactive Baritone jobs can be restarted for the same resource or travel target.

Use Minecraft **26.2**, Fabric and compatible **Baritone**. Start on solid ground
in the Overworld, with an empty cursor/crafting grid and conflicting inventory,
combat and movement automation disabled. Enable the module and click
**Start / Resume**. Enabling alone does not start a run. The bot's status explains
why a run cannot start or has paused. After a rejected click, inspect and clear
the crafting grid/cursor before resuming.

## Verification

`./gradlew build` runs GUI interactions, progression planning and crafting
transaction regressions. Crafting coverage includes all built-in recipes in each
supported grid size, exact and surplus ingredients, delayed recipe placement and
results, and rejected requests. The transaction tests use a simulated inventory;
they do not replace testing inside a Minecraft world with Baritone and a server.
