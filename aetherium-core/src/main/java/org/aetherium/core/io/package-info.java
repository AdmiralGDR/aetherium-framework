/*
 * Aetherium Framework — memory-mapped I/O.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * Memory-mapped, zero-GC streaming I/O.
 *
 * <p><b>EN.</b> {@link org.aetherium.core.io.MappedRegion} maps files into FFM {@code MemorySegment}s
 * for zero-heap chunk/asset streaming; the mapping is Arena-scoped and unmapped deterministically.
 *
 * <p><b>RU.</b> {@link org.aetherium.core.io.MappedRegion} отображает файлы в FFM
 * {@code MemorySegment} для потоковой обработки чанков/ассетов без кучи; отображение в области Arena
 * и снимается детерминированно.
 */
package org.aetherium.core.io;
