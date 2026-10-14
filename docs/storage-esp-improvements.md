# Storage ESP improvements — Minecraft 26.2

The reference's double-chest bounds, tracers, nearby fading and per-type colors
were already present. This update makes those features consistent and fills gaps:

- **Boxes** can be disabled independently of tracers and labels. Shape Mode still
  selects fill, border, or both when boxes are enabled.
- Double chests have one centered tracer, one label and one render-budget entry
  in both Box and Shader modes. Shader mode still draws both physical halves.
  Discovery of either loaded half identifies the pair.
- Blocks and container vehicles share a nearest-first render budget. A nearby
  chest boat or minecart no longer disappears merely because blocks used it up.
- Vehicle boxes honor Full, Corners and Hologram styling. Focused vehicle colors
  retain near/far fading, just like block containers.
- Selected campfires render correctly. Enchanting tables are available in the
  storage-block selector, using the Other color. Existing saved selections stay
  intact; enable Enchanting Table explicitly in an existing profile.

To get a simple view like the reference, choose **Mode: Box**, **Box Style: Full**,
then choose **Shape Mode: Both**. Enable **Tracers** if desired. Set **Fade Distance**
to zero to disable nearby fading. Disable **Labels** and **Inspector** for boxes
and lines alone.

Contents still come only from containers opened by the player and observed in
server/menu updates; utility blocks without inventories do not expose contents.
Tests cover merged geometry, opacity boundaries, color immutability, duplicate
container suppression and mixed block/vehicle render budgets. In-world visual
verification is still required; client startup alone does not prove shader output.
