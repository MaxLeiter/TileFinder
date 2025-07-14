#!/usr/bin/env bash
# Usage: ./bump_release.sh 0.1.1
set -euo pipefail

NEW_VER=${1:-}
if [[ -z "$NEW_VER" ]]; then
  echo "Provide new version, e.g. ./bump_release.sh 0.1.1"; exit 1; fi

PROP_FILE="gradle.properties"

echo "Updating $PROP_FILE to version=$NEW_VER"
# replace line starting with version=
sed -i.bak -E "s/^(version=).*/\1$NEW_VER/" "$PROP_FILE" && rm "$PROP_FILE.bak"

git add $PROP_FILE

git commit -m "chore: bump version to $NEW_VER"

tag="v$NEW_VER"
echo "Tagging commit as $tag"
git tag "$tag"

echo "Pushing commit and tag"
git push origin HEAD
 git push origin "$tag"

echo "Done. GitHub Actions will build and release the new version." 