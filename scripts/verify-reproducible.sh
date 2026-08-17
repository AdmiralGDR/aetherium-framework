#!/usr/bin/env bash
#
# Aetherium Framework — reproducible-build verification.
#
# EN: Builds every jar TWICE from a clean tree and asserts the artifacts are byte-identical (same SHA-256).
#     A reproducible build is a pure function of the sources (MANIFEST axiom: cryptographic reproducibility) —
#     the same inputs must yield the same bytes on any machine, so a third party can rebuild a release and
#     confirm it matches the signed artifacts. Exits non-zero (with a diff) if any jar differs.
# RU: Собирает каждый jar ДВАЖДЫ из чистого дерева и проверяет побайтовую идентичность (тот же SHA-256).
#     Воспроизводимая сборка — чистая функция от исходников: одинаковый вход даёт одинаковые байты на любой
#     машине, поэтому третья сторона может пересобрать релиз и убедиться, что он совпадает с подписанными
#     артефактами. Ненулевой код (и diff), если хоть один jar отличается.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Hash every built jar, keyed by its repo-relative path, sorted for a stable listing.
hash_all_jars() {
  find . -path '*/build/libs/*.jar' | sed 's|^\./||' | sort | while read -r jar; do
    printf '%s  %s\n' "$(sha256sum "$jar" | cut -d' ' -f1)" "$jar"
  done
}

echo "== reproducible-build check: building all jars twice from clean =="

echo "-- build #1 --"
./gradlew --no-daemon --console=plain clean jar >/dev/null
FIRST="$(hash_all_jars)"
COUNT="$(printf '%s\n' "$FIRST" | grep -c . || true)"

echo "-- build #2 --"
./gradlew --no-daemon --console=plain clean jar >/dev/null
SECOND="$(hash_all_jars)"

if [ "$FIRST" == "$SECOND" ]; then
  echo "REPRODUCIBLE: all ${COUNT} jars are byte-identical across two clean builds."
  exit 0
fi

echo "NOT REPRODUCIBLE — the two builds differ:" >&2
diff <(printf '%s\n' "$FIRST") <(printf '%s\n' "$SECOND") >&2 || true
exit 1
