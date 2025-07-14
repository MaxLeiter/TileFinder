# TileFinder

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/tilefinder)

For neoforge and Minecraft 1.21.1.

For the Forge 1.12.1 version, see the `1.12.1/forge` branch.

A simple mod for helping you locate tile/block entities in your world. Useful for modpacks with tons of random machines.

Repo is based on https://github.com/quat1024/modern-forge-1.12-template, vibecoded with v0 and OpenAI o3.

![screenshot](./screenshot.png)

## Releasing

1. Run `./bump_release.sh <newVersion>` – this updates `gradle.properties`, commits, tags, and pushes.
2. GitHub Actions (see `.github/workflows/release.yml`) builds the jar and attaches it to the GitHub Release automatically.
