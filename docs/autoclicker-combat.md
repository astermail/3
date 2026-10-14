# Auto Clicker — Minecraft 26.2

Use **mode-left: Press**, **hold-to-click: On**, and **combat-timing: On** for combat. Hold the attack binding and aim at an entity. Repeated attacks wait for full weapon cooldown, a live crosshair target, and the end of item use (including eating or blocking). Vanilla targeting and attack events still apply.

`delay-left` is the minimum interval in ticks; weapon cooldown can extend it. The first eligible click is immediate. Zero allows at most one generated click per tick. Disable combat timing to generate ordinary clicks against blocks or air. Hold mode retains vanilla held-button behavior; it is not a repeated combat attack mode.

Changing modes, disabling the module or opening a screen releases owned synthetic holds. Generated left clicks preserve the attack binding's previous state. Repeated right clicks pause during active item use.

Regression coverage checks exact intervals, readiness gating, release/reset, zero delay and absence of catch-up bursts. Build validation does not establish live server combat behavior.
