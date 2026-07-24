/*
 * Aetherium Framework — sovereign anti-reverse-engineering / anti-AI protection.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * The Sovereign Shield: a build/load-time bytecode protector for a mod author's own classes, hardening them
 * against reverse-engineering and, deliberately, against automated (AI/LLM) decompilation and analysis.
 *
 * <p>EN: Entry point is {@link org.aetherium.shield.Shield#protect}. Each pass targets a distinct capability
 * an analyst relies on — {@link org.aetherium.shield.DebugStripTransformer} (structure + names),
 * {@link org.aetherium.shield.StringEncryptionTransformer} (the "what"),
 * {@link org.aetherium.shield.ControlFlowObfuscator} (the "how"), {@link org.aetherium.shield.Renamer} (the
 * semantic map), {@link org.aetherium.shield.IntegrityManifest} (tamper detection), and
 * {@link org.aetherium.shield.WatermarkAttribute} (author traceability). Everything runs inside the
 * {@code aetherium-bytecode} verification sandbox, so protection never breaks a valid class. Opt-in; the
 * author remains responsible for their own mod's license terms.
 * RU: Точка входа — {@link org.aetherium.shield.Shield#protect}. Каждый проход бьёт по своей способности
 * аналитика. Всё работает внутри песочницы, поэтому защита никогда не ломает валидный класс.
 */
package org.aetherium.shield;
