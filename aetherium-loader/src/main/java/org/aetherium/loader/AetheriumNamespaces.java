/*
 * Aetherium Framework — class-namespace transform filter.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The performance-critical class filter: decides which classes the engine is allowed to touch.
 *
 * <p>EN: ModLauncher offers our {@code ILaunchPluginService} <em>every</em> class the game loads —
 * thousands of vanilla {@code net.minecraft.*} and {@code net.neoforged.*} classes. Running the ASM
 * engine on those would be ruinous. This filter answers "should we even look at this class?" with a
 * cheap prefix test, <strong>before</strong> any bytes are read. A hard deny-list protects vanilla
 * Minecraft, NeoForge, ModLauncher, the JDK, and Aetherium's own framework packages; an allow-list
 * (seeded with the test mod, extensible via {@code -Daetherium.transform.packages=a.b,c.d}) names the
 * Aetherium-mod namespaces we transform. Deny always wins.
 *
 * <p>RU: ModLauncher предлагает нашему {@code ILaunchPluginService} <em>каждый</em> загружаемый
 * класс — тысячи ванильных {@code net.minecraft.*} и {@code net.neoforged.*}. Запуск ASM-движка на
 * них был бы губителен. Этот фильтр отвечает на вопрос «стоит ли вообще смотреть на этот класс?»
 * дешёвой проверкой префикса <strong>до</strong> чтения байтов. Жёсткий deny-list защищает ванильный
 * Minecraft, NeoForge, ModLauncher, JDK и собственные пакеты фреймворка Aetherium; allow-list (с
 * тест-модом по умолчанию, расширяемый через {@code -Daetherium.transform.packages=a.b,c.d}) называет
 * пространства имён модов Aetherium, которые мы преобразуем. Deny всегда побеждает.
 */
final class AetheriumNamespaces {

    /** Internal-name prefixes we MUST NOT transform (vanilla, loader, JDK, our own framework). */
    private static final List<String> PROTECTED = List.of(
            "net/minecraft/",
            "net/neoforged/",
            "cpw/mods/",
            "org/objectweb/asm/",
            "org/slf4j/",
            "java/", "jdk/", "sun/", "javax/",
            "com/mojang/",
            // Never transform Aetherium's own framework classes:
            "org/aetherium/loader/",
            "org/aetherium/core/",
            "org/aetherium/bytecode/",
            "org/aetherium/native_bridge/");

    /** Internal-name prefixes that ARE Aetherium mods (seeded with the test mod). */
    private static final List<String> ALLOWED = loadAllowList();

    private AetheriumNamespaces() {
    }

    /** Cheap prefix test on a JVM internal name (e.g. {@code org/aetherium/testmod/Foo}). */
    static boolean shouldTransform(String internalName) {
        if (internalName == null) {
            return false;
        }
        for (String protectedPrefix : PROTECTED) {
            if (internalName.startsWith(protectedPrefix)) {
                return false; // deny wins
            }
        }
        for (String allowed : ALLOWED) {
            if (internalName.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    /** Immutable view of the active allow-list (internal-name prefixes), for diagnostics. */
    static List<String> allowList() {
        return List.copyOf(ALLOWED);
    }

    private static List<String> loadAllowList() {
        List<String> prefixes = new ArrayList<>();
        // Default: the bundled test mod.
        prefixes.add("org/aetherium/testmod/");
        // Extensible without recompiling: a CSV of dot-separated packages.
        String property = System.getProperty("aetherium.transform.packages", "");
        for (String pkg : property.split(",")) {
            String trimmed = pkg.strip().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                String internal = trimmed.replace('.', '/');
                if (!internal.endsWith("/")) {
                    internal += "/";
                }
                prefixes.add(internal);
            }
        }
        return List.copyOf(prefixes);
    }
}
