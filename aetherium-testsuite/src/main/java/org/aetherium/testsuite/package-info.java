/*
 * Aetherium Framework — Chaos Engineering test suite.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * Aetherium Framework — Chaos Engineering validation.
 *
 * <p><b>EN.</b> Deliberately attacks the framework with malformed bytecode ({@link org.aetherium.testsuite.ChaosMutators})
 * and FFM misuse ({@link org.aetherium.testsuite.NativeChaos}) across hundreds of parallel virtual
 * threads ({@link org.aetherium.testsuite.ChaosHarness}), asserting via {@link org.aetherium.testsuite.ChaosReport}
 * that the engine and native fallback contain every failure and the JVM never crashes.
 *
 * <p><b>RU.</b> Намеренно атакует фреймворк некорректным байт-кодом и злоупотреблением FFM на сотнях
 * параллельных виртуальных потоков, утверждая, что движок и нативный откат локализуют каждый сбой и
 * JVM никогда не падает.
 */
package org.aetherium.testsuite;
