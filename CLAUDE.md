# TileFinder

Finds the block entities around the player (chests, machines, cables...), lists them in a Vellum page, and leads the
player to one with a box and a beam. NeoForge + Fabric, Minecraft 26.3 / 26.2 / 1.21.1 from one source tree.

## Layout
- `common/` vanilla-only code, compiled into both loader jars (MultiLoader-Template style). `neoforge/`, `fabric/`
  hold entrypoints and the `Platform` service (`META-INF/services`).
- Stonecutter: subprojects are `:common:26.3`, `:neoforge:26.2`, `:fabric:1.21.1`... Sources are written for **26.3**
  (the vcs version). Differences use `//? if >=26 {` comments, the regex renames in `stonecutter.gradle`
  (`Identifier`/`ResourceLocation`, `GuiGraphicsExtractor`/`GuiGraphics`), or files under
  `common/versions/<mc>/src`. Keep `stonecutter.active '26.3'` when committing.
- Version shims: `Mc` (both sides) and `client/McClient`. Put new version differences there when they are small.
- Per-version build settings: `stonecutter.properties.toml`. Shared: `gradle.properties`.
- The finder UI is `common/src/main/resources/assets/tilefinder/vellum/finder.{html,css,js}` driven by
  `client/FinderPage.java`; the HUD line is `tracker.html` driven by `client/Tracker.java`.

## Building
- Gradle needs JDK 25+: `export JAVA_HOME=~/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home`.
- Vellum (`dev.vellum:vellum-<loader>-<mc>`) comes from https://maven.maxleiter.com; `vellum_version` is per
  Minecraft version in stonecutter.properties.toml. An unreleased Vellum resolves from mavenLocal
  (`./gradlew publishToMavenLocal` in a Vellum checkout). Vellum is a required mod, never bundled.
- `./gradlew build` builds every version and loader. One target: `./gradlew :fabric:26.2:build`.
  Loom resolves mods while configuring, so a missing artifact fails configuration of every project; add
  `--configure-on-demand` to only configure what you run. Gradle writes ~/.gradle: run it outside the sandbox.
- Run: `./gradlew :neoforge:26.3:runClient`; add `-Pjei`, `-Prei` or `-Pemi` (1.21.1) to load a recipe viewer.
- Jars: `<loader>/versions/<mc>/build/libs/tilefinder-<loader>-<mc>-<version>.jar`.

## Conventions
- No fallbacks or simulated behaviour that hide a failure: if something can't work, fail loudly.
- Comments explain non-obvious behaviour only.
- Messages from the page (`vellum.send`) are untrusted input (a resource pack can replace the page): validate.
- Never reference JEI/REI/EMI classes outside `compat/`; their plugin classes register bridges in `RecipeViewers`.

## Releasing
Bump `version` in gradle.properties, add a `## <version>` section to CHANGELOG.md, commit, then
`./bump_release.sh <version>` (or tag `v<version>` and push). `.github/workflows/release.yml` builds, makes the GitHub
release and uploads to Modrinth/CurseForge (needs `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` secrets). Dry run:
`./gradlew publishMods -PpublishDryRun`.
