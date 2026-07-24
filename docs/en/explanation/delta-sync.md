# Delta-Sync — Dirty-Bitmap Networking

*English. Russian mirror: [`../ru/delta-sync.md`](../../ru/explanation/delta-sync.md).*

Delta-sync upgrades the zero-GC `StructArena` sync ([`network.md`](network.md)) to transmit **only the
rows that changed** during the last tick — not the whole buffer.

## The dirty bitmap

`DirtyBitmap` is one bit per entity row, packed into `long` words. The producer marks a row dirty when it
mutates it. Its key operation is `forEachRun`, which coalesces consecutive dirty rows into contiguous
`(startRow, rowCount)` runs — so the codec ships each run as a single zero-copy off-heap `writeSegment`,
never a row at a time and never the whole buffer. The bitmap itself is tiny on the wire (one bit per row),
so it is sent in full and the receiver reconstructs the identical runs deterministically.

## Computing the delta

`StructArenaDelta` holds a confined **off-heap shadow** of the last-sent image. After the
`AetheriumTickEngine` advances entities, `computeDirty(arena, rowCount)` compares each row to the shadow
(`MemorySegment.mismatch`), records the changed rows in a `DirtyBitmap`, and refreshes the shadow. The
first call marks every row dirty (the initial full state is sent once). Diffing thousands of entities
allocates nothing on the Java heap.

## Wire format

`StructArenaDeltaCodec` encodes `[rowCount][bitmapWordCount][words…][dirty runs]`:

- **Encode:** write the row count and bitmap words, then stream each contiguous dirty run sliced straight
  from the server arena (`segment().asSlice(...)`).
- **Decode:** read the bitmap, reconstruct the identical runs, and read each straight into the matching
  slice of the pre-allocated client arena — so untouched rows keep their previous values and only the
  dirty bytes are written.

Both sides are allocation-free on the Java heap. `StructArenaDeltaPacket` carries the arena, row count,
and bitmap, and reports `payloadBytes()` vs `fullBytes()` for savings.

## Proof

```bash
aetherium delta
```

With 4096 entities, a tick that moves **7** rows ships **112 bytes** instead of **65 536** — a byte-exact
client reconstruction.
