# AppCDS — Zero-Parse Transformed-Class Caching

*English. Russian mirror: [`../ru/appcds.md`](../ru/appcds.md).*

Module: [`aetherium-loader`](../../aetherium-loader) (`AppCdsManager`).

Re-running the ASM pipeline (`ClassReader` → tree → `COMPUTE_FRAMES` → `CheckClassAdapter` verify) for
every modified class on **every** launch is the dominant load-phase cost. Aetherium eliminates it across
launches with two layers.

## Layer 1 — Memory-mapped transformed-class archive (active)

The final transformed bytes of each changed class are persisted to a blob, keyed by
`className + FNV-1a(originalBytes)`. On the next launch the blob is `mmap`'d (via the FFM
`MappedRegion`) and a cache **hit returns the cached bytes with a single slice copy — the entire ASM
pipeline is skipped**.

```
launch #1:  cold miss  → run ASM transform → record(name, originalHash, transformedBytes) → flush
launch #2:  mmap blob  → lookup(name, originalBytes) → HIT → return bytes  (zero ASM parse)
MC/NeoForge update changes a class → its hash changes → automatic per-entry invalidation (miss → re-cache)
```

The hash key means a NeoForge or Minecraft update that changes a class invalidates **only** the stale
entries, never the whole cache. The manager is safe by construction: any I/O error disables the cache for
the run (all misses, logged once) and never throws into the transform path. It flushes on JVM shutdown.

## Layer 2 — JVM AppCDS (`.jsa`) launch hints

`AppCdsManager` also emits a class list and the exact JVM flags so the launcher can enable the JDK's own
Application Class-Data Sharing — letting the JVM memory-map the *parsed, verified* class metadata of the
whole space and bypass even classfile parsing:

```
-XX:+AutoCreateSharedArchive
-XX:SharedArchiveFile=<cacheDir>/aetherium.jsa
-XX:SharedClassListFile=<cacheDir>/aetherium-appcds.classlist
```

Layer 1 removes Aetherium's ASM cost; layer 2 removes the JVM's own classfile-parse cost. Together they
make a warm launch near-instant.

## Configuration & inspection

- Enabled by default; `-Daetherium.cds.enabled=false` to disable.
- Cache dir: `${user.dir}/.aetherium/cds`, override with `-Daetherium.cds.dir=<path>`.
- `aetherium cdscache` shows status (entries, archive size, hit/miss/store counts).
- `aetherium cdscache test` runs the round-trip self-test: cold miss → record + flush → **reopen
  (mmap) → warm hit (zero ASM parse)** → stale-bytes invalidation.
