/*
 * Aetherium Framework — content package overview.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * The declarative, zero-boilerplate content API.
 *
 * <p>EN: A modder writes one annotation — {@link org.aetherium.content.AetheriumBlock} or
 * {@link org.aetherium.content.AetheriumItem} — and the framework does the rest. The
 * {@link org.aetherium.content.AetheriumContentProcessor} runs inside {@code javac}: it generates the
 * resource JSON (via {@code aetherium-datagen}) into the jar and writes the runtime content index the
 * loader consumes to register the content and its {@code BlockItem}. No registry code, no JSON, no
 * Minecraft import on the mod side.
 *
 * <p>RU: Модельер пишет одну аннотацию — {@link org.aetherium.content.AetheriumBlock} или
 * {@link org.aetherium.content.AetheriumItem} — остальное делает фреймворк.
 * {@link org.aetherium.content.AetheriumContentProcessor} работает внутри {@code javac}: генерирует
 * JSON ресурсов (через {@code aetherium-datagen}) в jar и пишет рантайм-индекс контента, который
 * загрузчик использует для регистрации контента и его {@code BlockItem}. Без кода реестра, без JSON,
 * без импорта Minecraft на стороне мода.
 */
package org.aetherium.content;
