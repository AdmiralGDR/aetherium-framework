/*
 * Aetherium Framework — hotswap module package docs.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * Live, zero-downtime class hot-swapping for mod developers.
 *
 * <p>EN: {@link org.aetherium.hotswap.ClassFileWatcher} watches the build output;
 * {@link org.aetherium.hotswap.HotSwapEngine} pushes changed bytecode into the running JVM via
 * {@code Instrumentation.redefineClasses} (acquired through {@code aetherium-injector}'s
 * {@code InstrumentationSupport}); a {@link org.aetherium.hotswap.HotSwapListener} re-resolves the
 * injector's {@link org.aetherium.injector.LiveHookGraph} so hooks stay correctly ordered after a swap.
 * RU: {@link org.aetherium.hotswap.ClassFileWatcher} следит за выводом сборки;
 * {@link org.aetherium.hotswap.HotSwapEngine} пушит изменённый байт-код в работающую JVM через
 * {@code Instrumentation.redefineClasses} (получая его через {@code InstrumentationSupport} из
 * {@code aetherium-injector}); {@link org.aetherium.hotswap.HotSwapListener} заново разрешает
 * {@link org.aetherium.injector.LiveHookGraph}, чтобы хуки оставались упорядоченными после свопа.
 */
package org.aetherium.hotswap;
