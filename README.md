# TileFinder

[Modrinth](https://modrinth.com/mod/tilefinder) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/tilefinder)

Find the block entities around you: chests, furnaces, machines, tanks, cables, everything a modpack base hides
behind walls. Press `\`, search, click, and follow the beam.

| Minecraft | NeoForge | Fabric |
|---|---|---|
| 26.3 | 26.3.0.26-beta+ | Loader 0.19.5+, Fabric API |
| 26.2 | 26.2.0.88+ | Loader 0.19.5+, Fabric API |
| 1.21.1 | 21.1.255+ | Loader 0.19.5+, Fabric API |

TileFinder needs [Vellum](https://github.com/MaxLeiter/vellum), which draws its screen; Modrinth and CurseForge install
it with TileFinder. [JEI](https://modrinth.com/mod/jei), [REI](https://modrinth.com/mod/rei) and
[EMI](https://modrinth.com/mod/emi) (1.21.1) are optional. The old Forge 1.12.2 version is on the `1.12/forge`
branch, and the NeoForge-only 1.21.1 version (0.4) on `1.21.1/neoforge`.

## Using it

- **`\`** opens the finder. Everything with a block entity within the radius is listed, nearest first, grouped by
  kind: "Furnace ×12", "Universal Cable · 340 in 3", each with its mod and the direction and distance to the nearest.
- **Search** by name, by mod (`@mekanism`), or by a container's custom name (a chest you renamed "Diamonds").
  Vanilla doesn't send those names to clients, so on a multiplayer server only names a mod syncs are known; in
  singleplayer all are.
  **Enter** tracks the nearest match.
- **Filters**: everything, blocks that hold items, fluids or energy (whatever the mod exposes to its loader's
  transfer API), your starred spots, and decoration.
- **Click** a group to see each place, or **Track all** to box every one of them. Click a place to track just that
  one. Tracking draws a box around each target and a beam to the nearest, through walls, with a line on the HUD
  saying how far and which way. A target drops off when you reach it; **`O`** stops tracking.
- **Hover an item** in your inventory, a chest, or JEI/REI/EMI's item list and press **`\`** to find that block
  nearby. In the finder, **R** and **U** open the group's recipes and uses in your recipe viewer.
- **★** stars a place (per world). **⧉** copies its coordinates.
- The **Settings** tab (also behind the config button in NeoForge's mod list and Mod Menu) sets the default radius,
  whether touching blocks merge, whether decoration shows under All, the beam's colours, width, speed and arc, when a
  target counts as reached, and the HUD line. It's saved to `config/tilefinder.json`.

### What counts as one thing

- A double chest is one chest, a two-tall block is one block, and on 1.21.1 a bed is one bed (beds have no block
  entity from 26.1 on, so they aren't listed there).
- Touching blocks of the same kind are merged into one place: a run of cable or pipe is one network, a wall of
  barrels is one storage wall. Expand it to see every block. You can turn this off.
- Wall signs, wall heads and wall banners are listed with their items, so all oak signs are one group.
- Moving pistons are never listed. Signs, hanging signs, banners, heads, beds (1.21.1), decorated pots, bells and
  copper golem statues are decoration: hidden from "All" unless you ask for them, always under "Decor".
- Packs and servers can extend both lists with the block entity type tags `tilefinder:hidden` and
  `tilefinder:decorative` (`data/<namespace>/tags/block_entity_type/…`); players can add type ids in the settings.

The client can only see chunks it has loaded, so the radius goes up to your render distance.

### On servers

TileFinder is client-side; a server doesn't need it. If the server has it, `/tilefinder [radius] [filter]` works for
everyone, including players without the mod: it opens a chest menu of what's nearby (click to be pointed the way with
particles, right-click to list each place, shift-click to teleport if you're an operator). `/tilefinder clear` stops
the particles.

## Building

The sources build every Minecraft version on both loaders with [Stonecutter](https://stonecutter.kikugie.dev), in
the layout Vellum uses. See [CLAUDE.md](CLAUDE.md) for the details.

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build
```

Jars land in `<loader>/versions/<minecraft>/build/libs/`. `./gradlew :neoforge:26.3:runClient` (or any loader and
version) starts a dev client; `-Pjei`, `-Prei` or `-Pemi` (1.21.1) adds a recipe viewer.

## Releasing

1. Add a `## <version>` section to [CHANGELOG.md](CHANGELOG.md) and commit it.
2. `./bump_release.sh <version>`: sets the version, tags `v<version>` and pushes.
3. The Release workflow builds every jar, makes the GitHub release, and uploads each jar to Modrinth and CurseForge
   for the Minecraft version it was built for. It needs the `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` repository
   secrets; without them it only makes the GitHub release. `./gradlew publishMods -PpublishDryRun` shows what would
   be uploaded.

## License

MIT
