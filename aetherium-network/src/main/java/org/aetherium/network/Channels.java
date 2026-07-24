/*
 * Aetherium Framework — network channel-id validation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.aetherium.core.AetheriumException;
import org.aetherium.core.Diagnostic;

import java.util.regex.Pattern;

/**
 * Validates network channel ids so two mods can never share (or malform) a channel.
 *
 * <p>EN: A channel id must be a namespaced {@code "namespace:path"} — exactly the shape a loader turns into
 * a {@code ResourceLocation}. Enforcing the namespace at construction time is what makes cross-mod
 * cross-talk <em>impossible by convention</em>: a mod that writes {@code "mymod:state"} can never collide
 * with another mod that writes {@code "othermod:state"}. The framework used to ship a single process-wide
 * constant ({@code "aetherium:tree_sync"}); this class replaces that footgun.
 *
 * <p>RU: Идентификатор канала обязан быть вида {@code "namespace:path"} — ровно то, что загрузчик
 * превращает в {@code ResourceLocation}. Проверка пространства имён при создании делает перекрёстные
 * коллизии модов <em>невозможными по соглашению</em>. Раньше фреймворк отдавал одну глобальную константу
 * — этот класс убирает эту ловушку.
 */
public final class Channels {

    // namespace = [a-z0-9_.-]+ , path = [a-z0-9/._-]+ (Minecraft ResourceLocation rules), single ':'.
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private Channels() {
    }

    /**
     * Validate a namespaced channel id, returning it unchanged if valid.
     *
     * @throws AetheriumException if {@code channelId} is null, blank, or not of the form
     *                            {@code "namespace:path"} in lowercase.
     */
    public static String validate(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new AetheriumException(Diagnostic.error("AE-NET-CHANNEL-EMPTY",
                    "A channel id must be a non-blank namespaced id like \"mymod:state\"."));
        }
        if (!VALID.matcher(channelId).matches()) {
            throw new AetheriumException(Diagnostic.error("AE-NET-CHANNEL-SHAPE",
                    "Channel id '" + channelId + "' is not a valid namespaced id. Use lowercase "
                            + "\"namespace:path\", e.g. \"mymod:faction_state\"."));
        }
        return channelId;
    }
}
