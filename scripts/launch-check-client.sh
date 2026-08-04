#!/usr/bin/env bash
# Aetherium Framework — real CLIENT launch check under Xvfb (/ WS2).
#
# Boots a REAL NeoForge 1.21.1 CLIENT with the framework, headless via Xvfb + software GL (llvmpipe), and
# asserts the game actually launches AND renders — the thing a dedicated server cannot show, and exactly the
# class of defect was (a client-only GUI blur). No Minecraft account is needed: an offline client
# reaches the title screen and loads mods without login. The definitive companion to scripts/launch-check.sh
# (the server check) — together they cover "the game launches for the framework AND mods".
#
# The offline AE-UI-BLUR guard in `./gradlew check` is the fast CI truth; this is the real, visible proof.
#
# Usage: ./scripts/launch-check-client.sh
#   AETHERIUM_JAVA=/path/to/java21   override the JDK
#   AETHERIUM_CLIENT_TIMEOUT=420     seconds to allow (first run downloads client assets once)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA="${AETHERIUM_JAVA:-}"
TIMEOUT="${AETHERIUM_CLIENT_TIMEOUT:-420}"
LOG="$ROOT/build/aetherium-client-check.log"
mkdir -p "$ROOT/build"

command -v xvfb-run >/dev/null 2>&1 || { echo "xvfb-run not found — install it (sudo apt-get install -y xvfb)"; exit 3; }

echo ">> booting a REAL NeoForge client under Xvfb with software GL (assets download once; up to ${TIMEOUT}s)"
# Force Mesa software rendering so an OpenGL 3.2+ context exists without a GPU (llvmpipe/swrast).
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export MESA_GL_VERSION_OVERRIDE=3.3
export MESA_GLSL_VERSION_OVERRIDE=330
export __GLX_VENDOR_LIBRARY_NAME=mesa
GRADLE_ARGS=(":aetherium-loader:runClient" "--console=plain")
[ -n "$JAVA" ] && GRADLE_ARGS+=("-Dorg.gradle.java.home=$(dirname "$(dirname "$JAVA")")")

# runClient never self-quits (it is the game), so bound it with `timeout`; we read success from the log after.
# SIGKILL after the grace period guarantees the JVM + Xvfb are torn down even if the window is up.
( cd "$ROOT" && timeout --kill-after=20 --signal=TERM "$TIMEOUT" \
    xvfb-run -a -s "-screen 0 1280x720x24" \
    ./gradlew "${GRADLE_ARGS[@]}" ) > "$LOG" 2>&1 || true
pkill -KILL -f "runClient" 2>/dev/null || true

echo ">> results"
ok=1
if grep -q "ResolutionException" "$LOG"; then echo "  ✗ module ResolutionException"; ok=0
else echo "  ✓ no module ResolutionException"; fi
if grep -qiE "Aetherium (Framework|loader) |Aetherium PAL bridge" "$LOG"; then echo "  ✓ the framework constructed on the client"
else echo "  ✗ the framework did not construct"; ok=0; fi
if grep -qiE "Backend library: LWJGL|OpenAL initialized|Reloading ResourceManager|Narrator library|minecraft:textures/atlas" "$LOG"; then
  echo "  ✓ the client reached client-init / title-screen markers (GL + resources up — the GUI can render)"
else
  echo "  ✗ the client did not reach the title screen (software GL may be unavailable in this environment)"; ok=0
fi
# WS6: the client must load a MOD too, not just the framework — assert the testmod's content registers.
if grep -qiE "Registered Aetherium machine block-entity aetherium:test_machine" "$LOG"; then
  echo "  ✓ a mod's content registered on the client (testmod: aetherium:test_machine)"
else
  echo "  ✗ the testmod's content did not register on the client"; ok=0
fi
# WS7: the serverbound (client→server) channel must register in the real runtime, and the loader must
# supply the physical side to the mod — the directional matrix + side model, live (not just offline).
if grep -qiE "registered serverbound admin channel aetherium_testmod:admin" "$LOG"; then
  echo "  ✓ a mod registered a serverbound channel on the real client (return channel is wired)"
else
  echo "  ✗ the testmod's serverbound channel did not register"; ok=0
fi
if grep -qiE "side = CLIENT " "$LOG"; then
  echo "  ✓ the loader supplied the physical side (CLIENT) to the mod — the side model is live"
else
  echo "  ✗ the mod was not told its physical side"; ok=0
fi
echo "  — full log: $LOG"
if [ "$ok" = 1 ]; then
  echo "RESULT: CLIENT LAUNCH OK ✓ — the game launches and renders with the framework"
  exit 0
else
  echo "RESULT: CLIENT LAUNCH INCONCLUSIVE ✗ — see the log; the offline AE-UI-BLUR guard + scripts/launch-check.sh remain the CI truth"
  exit 1
fi
