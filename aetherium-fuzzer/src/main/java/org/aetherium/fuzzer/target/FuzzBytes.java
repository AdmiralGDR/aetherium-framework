/*
 * Aetherium Framework — adversarial byte-array generators for the fuzz targets.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer.target;

import java.util.random.RandomGenerator;

/**
 * Shapes of hostile input the targets feed to the production decoders.
 *
 * <p>EN: A flat random blob catches very little — real parsers reject it on the first byte. These
 * generators bias toward the <em>boundaries</em> that actually break decoders: empty and 1–3 byte runts,
 * lengths that are not a multiple of the word size, inputs that carry a valid magic header followed by
 * garbage (so the parser commits to a real path), and bit-flips of a known-good seed. The mix is chosen
 * per case from the seeded RNG so a campaign covers all shapes deterministically.
 * RU: Плоский случайный блоб ловит мало — настоящие парсеры отвергают его на первом байте. Эти
 * генераторы смещены к <em>границам</em>, реально ломающим декодеры: пустые и 1–3-байтовые огрызки,
 * длины не кратные слову, входы с валидной магией и мусором после неё (парсер заходит на реальный путь)
 * и инверсии битов известного-хорошего образца. Форма выбирается по сиду — кампания детерминирована.
 */
public final class FuzzBytes {

    private FuzzBytes() {
    }

    /** A blob whose shape (runt / unaligned / magic-prefixed / pure-random) is chosen from {@code rng}. */
    public static byte[] hostile(RandomGenerator rng, int[] magicPrefix, int maxLen) {
        return switch (rng.nextInt(6)) {
            case 0 -> new byte[0];                                  // empty
            case 1 -> random(rng, rng.nextInt(1, 4));               // runt (1–3 bytes)
            case 2 -> random(rng, alignedOffByOne(rng, maxLen));    // not a multiple of 4
            case 3 -> magicThenGarbage(rng, magicPrefix, maxLen);   // valid header, junk body
            case 4 -> allSame(rng, maxLen);                          // long run of one byte value
            default -> random(rng, rng.nextInt(0, maxLen + 1));      // arbitrary length pure random
        };
    }

    /** {@code len} random bytes. */
    public static byte[] random(RandomGenerator rng, int len) {
        byte[] b = new byte[Math.max(0, len)];
        rng.nextBytes(b);
        return b;
    }

    /** A copy of {@code seed} with a random number of random bit-flips (fuzzing a known-good input). */
    public static byte[] bitFlip(RandomGenerator rng, byte[] seed) {
        byte[] b = seed.clone();
        if (b.length == 0) {
            return b;
        }
        int flips = rng.nextInt(1, Math.max(2, b.length / 8 + 2));
        for (int i = 0; i < flips; i++) {
            int idx = rng.nextInt(b.length);
            b[idx] ^= (byte) (1 << rng.nextInt(8));
        }
        return b;
    }

    private static byte[] magicThenGarbage(RandomGenerator rng, int[] magicLittleEndianWords, int maxLen) {
        int bodyLen = rng.nextInt(0, maxLen + 1);
        byte[] b = new byte[magicLittleEndianWords.length * 4 + bodyLen];
        int p = 0;
        for (int word : magicLittleEndianWords) {
            b[p++] = (byte) (word & 0xFF);
            b[p++] = (byte) ((word >>> 8) & 0xFF);
            b[p++] = (byte) ((word >>> 16) & 0xFF);
            b[p++] = (byte) ((word >>> 24) & 0xFF);
        }
        for (int i = 0; i < bodyLen; i++) {
            b[p++] = (byte) rng.nextInt(256);
        }
        return b;
    }

    private static byte[] allSame(RandomGenerator rng, int maxLen) {
        byte[] b = new byte[rng.nextInt(0, maxLen + 1)];
        byte v = (byte) rng.nextInt(256);
        java.util.Arrays.fill(b, v);
        return b;
    }

    private static int alignedOffByOne(RandomGenerator rng, int maxLen) {
        int words = rng.nextInt(0, Math.max(1, maxLen / 4));
        return words * 4 + rng.nextInt(1, 4); // never a clean multiple of 4
    }
}
