#!/usr/bin/env bash
# Usage: ./bump_release.sh 1.0.1
# Sets `version` in gradle.properties, commits, tags v<version> and pushes the tag; .github/workflows/release.yml
# then builds every Minecraft version and loader and publishes them. Write the "## <version>" section of CHANGELOG.md
# first: the release notes and the store changelogs come from it.
set -euo pipefail

new_version=${1:-}
if [[ -z "$new_version" ]]; then
  echo "Usage: ./bump_release.sh <version>, e.g. ./bump_release.sh 1.0.1" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "The working tree has uncommitted changes; commit or stash them first." >&2
  exit 1
fi
if ! grep -qE "^## \[?${new_version//./\\.}\]?( |$)" CHANGELOG.md; then
  echo "CHANGELOG.md has no '## $new_version' section yet." >&2
  exit 1
fi

sed -i.bak -E "s/^version=.*/version=$new_version/" gradle.properties && rm gradle.properties.bak
if ! git diff --quiet gradle.properties; then
  git add gradle.properties
  git commit -m "Version $new_version"
fi
git tag "v$new_version"
git push origin HEAD
git push origin "v$new_version"
echo "Tagged v$new_version. The Release workflow builds and publishes it."
