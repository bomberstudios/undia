#!/usr/bin/env bash
# Downloads the Zodiak font from Fontshare and puts the files where the app and the website expect them.
#
# Zodiak is under the ITF Free Font License, which lets you use it in your own apps and websites but not
# pass the font files on to anyone else, so they are not in this repository. Every build gets its own
# copy straight from Fontshare, which is what the licence asks for. See https://www.fontshare.com/fonts/zodiak
#
#   scripts/get-fonts.sh          download whatever is missing
#   scripts/get-fonts.sh --force  download everything again
set -euo pipefail
cd "$(dirname "$0")/.."

URL="https://api.fontshare.com/v2/fonts/download/zodiak"
FONT_DIR=app/src/main/res/font
ASSET_DIR=docs/assets
WEB_STYLES=(Light LightItalic Regular Italic Bold BoldItalic) # the ones the website uses

all_there() {
  local n
  for n in Thin ThinItalic Light LightItalic Regular Italic Bold BoldItalic Extrabold ExtraboldItalic Black BlackItalic; do
    [ -f "$FONT_DIR/$(snake "$n").otf" ] || return 1
  done
  for n in "${WEB_STYLES[@]}"; do
    [ -f "$ASSET_DIR/Zodiak-$n.otf" ] && [ -f "$ASSET_DIR/Zodiak-$n.woff2" ] || return 1
  done
}

# BoldItalic -> zodiak_bold_italic, Italic -> zodiak_italic (Android resource names)
snake() { echo "zodiak_$(echo "$1" | sed -E 's/([a-z])Italic/\1_italic/' | tr '[:upper:]' '[:lower:]')"; }

if [ "${1:-}" != "--force" ] && all_there; then
  echo "Zodiak is already in place."
  exit 0
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
echo "Downloading Zodiak from Fontshare..."
curl -fsSL --retry 3 -o "$tmp/zodiak.zip" "$URL"
unzip -q "$tmp/zodiak.zip" -d "$tmp/zodiak"
src="$tmp/zodiak/Zodiak_Complete/Fonts"
[ -d "$src/OTF" ] || { echo "Unexpected layout in the Fontshare download" >&2; exit 1; }

mkdir -p "$FONT_DIR" "$ASSET_DIR"
for f in "$src"/OTF/Zodiak-*.otf; do
  name=$(basename "$f" .otf)
  cp "$f" "$FONT_DIR/$(snake "${name#Zodiak-}").otf"
done
for n in "${WEB_STYLES[@]}"; do
  cp "$src/OTF/Zodiak-$n.otf" "$ASSET_DIR/"
  cp "$src/WEB/fonts/Zodiak-$n.woff2" "$ASSET_DIR/"
done
echo "Zodiak is in place."
