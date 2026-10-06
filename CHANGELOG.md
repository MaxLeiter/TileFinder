# Changelog

## 1.0.0

A rewrite, for Minecraft 26.3, 26.2 and 1.21.1, on NeoForge and Fabric.

- New finder screen, built with [Vellum](https://github.com/MaxLeiter/vellum): search by name, by mod (`@mekanism`)
  or by a chest's custom name (in singleplayer; vanilla servers don't send those names); filter to blocks that hold items, fluids or energy, or to your starred spots; sort by
  distance, name, mod or count; change the radius with a slider.
- Finds what you'd call one thing as one thing: a double chest is one chest, a bed is one bed (1.21.1; 26.x beds have no block entity), a run of cable or pipe
  is one network (expand it to see each block), and wall signs, wall heads and banners list with their items.
- Decoration (signs, banners, heads, beds, pots) is kept out of the way under its own filter. Packs can hide or
  reclassify block entities with the `tilefinder:hidden` and `tilefinder:decorative` block entity type tags.
- Track one block or every block of a kind: boxes around each, a beam to the nearest, and a HUD line with the
  distance and direction. Targets drop off as you reach them. Everything shows through walls.
- Hover an item in JEI, REI, EMI (1.21.1) or any inventory and press the TileFinder key to find that block nearby.
  The finder's R and U buttons open recipes and uses in your recipe viewer.
- Stars are saved per world. Coordinates copy to the clipboard with one click.
- Settings in the finder's Settings tab, also from NeoForge's mod list and Mod Menu.
- Servers: `/tilefinder [radius] [filter]` works for players without the mod, in a chest menu, and points the way with
  particles. No library needed (GooeyLibs is gone).
- Scanning reads each loaded chunk's block entities instead of probing every block, so large radii are cheap.
