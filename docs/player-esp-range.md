# Player ESP range (26.2)

ESP now has a separate **player-distance** setting: 512 blocks by default, a slider up to 1024, and typed values up to 4096. Existing saved `max-distance` values continue to control non-player entities; the new player setting applies to every ESP mode.

Player overlays no longer stop at the terrain render-distance boundary. Shader and Glow also bypass vanilla's player model distance cutoff for eligible players. No Render exclusions still take priority over model rendering. Signature modes retain their configurable entity budget.

These changes use currently tracked entities only. The server controls player tracking distance; increasing this setting cannot reveal players whose positions have not been sent, or retain players after they unload. Very distant outlines may be small, and higher ranges can increase rendering cost.
