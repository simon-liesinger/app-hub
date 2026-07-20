#!/usr/bin/env bash
#
# add-app.sh — add (or update) an app in the App Hub catalog.
#
# Point it at a built APK; it reads the package name + version straight from the
# APK, publishes the APK as a GitHub release asset, and upserts the matching
# entry in manifest.json (keyed by package name, so re-running just updates it).
#
# Usage:
#   tools/add-app.sh --apk <path> --name "Display Name" [--desc "..."] [--slug foo]
#   tools/add-app.sh --name "Old App" --package com.foo.bar --deprecated [--desc "..."]
#
# After running, commit & push manifest.json (and `git push` — releases are already
# live). Every phone running App Hub sees the change on its next refresh.
#
set -euo pipefail

REPO="simon-liesinger/app-hub"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/manifest.json"
AAPT="${AAPT:-$HOME/Library/Android/sdk/build-tools/34.0.0/aapt}"

usage() { grep '^#' "$0" | sed 's/^# \{0,1\}//'; }

NAME="" DESC="" APK="" SLUG="" DEPRECATED=0 PKG=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apk)        APK="$2"; shift 2;;
    --name)       NAME="$2"; shift 2;;
    --desc)       DESC="$2"; shift 2;;
    --slug)       SLUG="$2"; shift 2;;
    --package)    PKG="$2"; shift 2;;
    --deprecated) DEPRECATED=1; shift;;
    -h|--help)    usage; exit 0;;
    *) echo "Unknown arg: $1" >&2; usage >&2; exit 1;;
  esac
done

[ -n "$NAME" ] || { echo "error: --name is required" >&2; exit 1; }
slugify() { echo "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//'; }

if [ "$DEPRECATED" = "1" ] && [ -z "$APK" ]; then
  # Deprecated with no distributable — just flag it in the catalog.
  [ -n "$PKG" ] || { echo "error: --package required for --deprecated without --apk" >&2; exit 1; }
  ENTRY=$(jq -n --arg name "$NAME" --arg pkg "$PKG" --arg desc "$DESC" \
    '{name:$name, packageName:$pkg, deprecated:true, description:$desc}')
else
  [ -n "$APK" ] || { echo "error: --apk is required" >&2; exit 1; }
  [ -f "$APK" ] || { echo "error: APK not found: $APK" >&2; exit 1; }
  BADGING=$("$AAPT" dump badging "$APK")
  PKG=$(echo "$BADGING" | sed -n "s/.*package: name='\([^']*\)'.*/\1/p")
  VC=$(echo  "$BADGING" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")
  VN=$(echo  "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
  [ -n "$PKG" ] && [ -n "$VC" ] || { echo "error: couldn't read package/versionCode from APK" >&2; exit 1; }
  [ -n "$SLUG" ] || SLUG=$(slugify "$NAME")
  TAG="${SLUG}-v${VC}"
  ASSET="${SLUG}.apk"
  TMPDIR=$(mktemp -d); TMP="$TMPDIR/$ASSET"
  cp "$APK" "$TMP"
  echo "→ $NAME  ($PKG  v$VN build $VC)  →  release $TAG / $ASSET"
  if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
    gh release upload "$TAG" "$TMP" --repo "$REPO" --clobber
  else
    gh release create "$TAG" "$TMP" --repo "$REPO" --title "$NAME $VN" \
      --notes "$NAME v$VN (build $VC)."
  fi
  rm -rf "$TMPDIR"
  URL="https://github.com/${REPO}/releases/download/${TAG}/${ASSET}"
  DEPFLAG='{}'; [ "$DEPRECATED" = "1" ] && DEPFLAG='{deprecated:true}'
  ENTRY=$(jq -n --arg name "$NAME" --arg pkg "$PKG" --argjson vc "$VC" \
                --arg vn "$VN" --arg url "$URL" --arg desc "$DESC" --argjson dep "$DEPFLAG" \
    '{name:$name, packageName:$pkg, versionCode:$vc, versionName:$vn, apkUrl:$url, description:$desc} + $dep')
fi

# Upsert by packageName: drop any existing entry for this package, append the new one.
tmp=$(mktemp)
jq --arg pkg "$PKG" --argjson entry "$ENTRY" \
   '.apps |= (map(select(.packageName != $pkg)) + [$entry])' "$MANIFEST" > "$tmp"
mv "$tmp" "$MANIFEST"
echo "✓ $NAME ($PKG) written to manifest.json"
