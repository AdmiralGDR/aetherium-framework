/*
 * Aetherium Framework — a known vanilla injection point (LSP autocomplete datum).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.lsp;

import java.util.List;
import java.util.Map;

/**
 * One well-known vanilla method a mod can inject into, with its valid anchors — pure tooling metadata.
 *
 * <p>EN: The LSP backend serves these as autocomplete items so an IDE can offer real, validated
 * injection targets ({@code owner::method}) and the anchors that make sense there ({@code HEAD},
 * {@code RETURN}) <em>before</em> the modder compiles anything. It carries no Minecraft type — only the
 * JVM internal name + descriptor strings the injector consumes — so the CLI stays loader-agnostic.
 * RU: Один известный ванильный метод для инъекции с валидными якорями — чистые метаданные инструментария.
 * LSP-бэкенд отдаёт их как элементы автодополнения, чтобы IDE предлагала реальные проверенные цели
 * ({@code owner::method}) и подходящие якоря ({@code HEAD}, {@code RETURN}) <em>до</em> компиляции. Не несёт
 * типов Minecraft — только JVM-имя + дескриптор.
 *
 * @param owner       JVM internal name (e.g. {@code net/minecraft/world/entity/Entity})
 * @param method      method name (e.g. {@code tick})
 * @param descriptor  JVM method descriptor (e.g. {@code ()V})
 * @param anchors     valid anchors for this method (e.g. {@code [HEAD, RETURN]})
 * @param summary     a short human description shown in the completion popup
 */
public record InjectionPoint(String owner, String method, String descriptor,
                             List<String> anchors, String summary) {

    public InjectionPoint {
        anchors = List.copyOf(anchors);
    }

    /** The {@code owner::method} target string the Aetherium DSL accepts (dots, IDE-friendly). */
    public String target() {
        return owner.replace('/', '.') + "::" + method;
    }

    /** A completion label combining the target and descriptor. */
    public String label() {
        return target() + descriptor;
    }

    /** Render as an LSP completion item (kind 2 = Method). */
    public Map<String, Object> toCompletionItem() {
        return Map.of(
                "label", label(),
                "kind", 2,
                "detail", "anchors: " + String.join(", ", anchors),
                "documentation", summary,
                "insertText", target());
    }
}
