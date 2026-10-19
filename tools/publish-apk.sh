#!/usr/bin/env bash
# Hands a verified build to GitHub as the Release "v<versionName>" (see
# .github/workflows/publish-apk.yml). Run it after the source commit is pushed:
#
#   tools/publish-apk.sh NOTES.md      # NOTES.md: the release notes (English, no passenger data)
#
# It pushes one commit (dist/NastaStopp.apk + the notes on top of HEAD) to the branch
# "apk-drop/v<versionName>"; the workflow publishes it and deletes the branch. The APK is never
# committed to a real branch.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

notes="${1:?usage: tools/publish-apk.sh <notes file>}"
apk=dist/NastaStopp.apk
version="v$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)"

test -s "$apk" || { echo "missing $apk" >&2; exit 1; }
test -s "$notes" || { echo "missing $notes" >&2; exit 1; }
git diff --quiet HEAD || { echo "commit and push the source first" >&2; exit 1; }
git fetch -q origin "$(git branch --show-current)"
[ "$(git rev-parse HEAD)" = "$(git rev-parse FETCH_HEAD)" ] || { echo "push HEAD first" >&2; exit 1; }

index="$(mktemp)"
trap 'rm -f "$index"' EXIT
export GIT_INDEX_FILE="$index"
git read-tree HEAD
git update-index --add --cacheinfo "100644,$(git hash-object -w "$apk"),dist/NastaStopp.apk"
git update-index --add --cacheinfo "100644,$(git hash-object -w "$notes"),dist/NOTES.md"
commit="$(git commit-tree "$(git write-tree)" -p HEAD -m "Hand over $version (deleted after publishing)")"
unset GIT_INDEX_FILE

git push origin "$commit:refs/heads/apk-drop/$version"
echo "Release $version: https://github.com/HussamEl/S20Ultra/releases/tag/$version"
echo "APK:            https://github.com/HussamEl/S20Ultra/releases/download/$version/NastaStopp.apk"
