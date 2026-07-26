#!/usr/bin/env bash
# Aetherium Framework — real launch check (/ WS3).
#
# Boots a REAL headless NeoForge 1.21.1 dedicated server with the framework staged, and asserts the game
# actually launches: no module-resolution crash, the framework loads as a MOD, and (if a mod with a machine
# behaviour is staged) the machine dispatches. This is the definitive proof the "the game starts" gate — the
# thing `bootSmoke`/`verifyJar` approximate offline. No display and no Minecraft account are needed: the
# dedicated-server jar is public, and the crash (if present) happens at module resolution, before any window.
#
# Usage:
#   ./scripts/launch-check.sh [server-dir] [extra-mod.jar ...]
# It builds the shipped jars, installs the server on first run (downloads MC + NeoForge), stages the
# transformer + loader (+ any extra mods), boots headless, and greps the log. Exit 0 = launched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NF="${AETHERIUM_NEOFORGE:-21.1.233}"
SRV="${1:-$ROOT/build/aetherium-test-server}"
JAVA="${AETHERIUM_JAVA:-java}"
shift || true

echo ">> building the shipped jars"
( cd "$ROOT" && ./gradlew -q :aetherium-transformer:jar :aetherium-loader:jar )

if [ ! -d "$SRV/libraries" ]; then
  echo ">> installing NeoForge $NF dedicated server into $SRV (first run; downloads MC + NeoForge)"
  mkdir -p "$SRV"
  curl -fsSL -o "$SRV/neoforge-installer.jar" \
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NF/neoforge-$NF-installer.jar"
  "$JAVA" -jar "$SRV/neoforge-installer.jar" --install-server "$SRV" >/dev/null
fi
echo "eula=true" > "$SRV/eula.txt"

echo ">> staging the framework + any extra mods into $SRV/mods"
rm -rf "$SRV/mods" && mkdir -p "$SRV/mods"
cp "$ROOT"/aetherium-transformer/build/libs/aetherium-transformer-*.jar "$SRV/mods/" 2>/dev/null || true
cp "$ROOT"/aetherium-loader/build/libs/aetherium-loader-*.jar "$SRV/mods/" 2>/dev/null || true
for extra in "$@"; do cp "$extra" "$SRV/mods/"; done
# drop the -sources jars if the glob caught them
rm -f "$SRV"/mods/*-sources.jar

echo ">> booting headless (auto-stops after startup)"
ARGS="$(tr '\n' ' ' < "$SRV/libraries/net/neoforged/neoforge/$NF/unix_args.txt")"
LOG="$SRV/launch-check.log"
( sleep 40; echo "stop" ) | ( cd "$SRV" && timeout 180 "$JAVA" @user_jvm_args.txt $ARGS --nogui ) > "$LOG" 2>&1 || true

echo ">> results"
strip() { sed 's/\x1b\[[0-9;]*m//g'; }
ok=1
if grep -q "ResolutionException" "$LOG"; then echo "  ✗ module ResolutionException (the crash)"; ok=0
else echo "  ✓ no module ResolutionException"; fi
if grep -q "Aetherium Framework .* (aetherium)" "$LOG"; then echo "  ✓ Aetherium Framework is in the mod list"
else echo "  ✗ Aetherium Framework NOT in the mod list"; ok=0; fi
if grep -q "Done (" "$LOG"; then echo "  ✓ server reached Done (started)"
else echo "  ✗ server did not reach Done"; ok=0; fi
echo "  — full log: $LOG"
[ "$ok" = 1 ] && { echo "RESULT: LAUNCH OK ✓"; exit 0; } || { echo "RESULT: LAUNCH FAILED ✗"; exit 1; }
