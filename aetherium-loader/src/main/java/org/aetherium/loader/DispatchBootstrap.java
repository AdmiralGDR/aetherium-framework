/*
 * Aetherium Framework — dispatch table bootstrap.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import org.aetherium.bytecode.runtime.DispatchTable;
import org.aetherium.core.SymbolManifest;
import org.aetherium.transformer.AetheriumSymbols;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Builds and installs the {@code invokedynamic} dispatch table during the loader's construction.
 *
 * <p>EN: The dispatch table must be populated <em>before</em> any transformed mod class executes a
 * lowered API call, so the loader installs it as early as possible — here, driven from
 * {@code FMLConstructModEvent}. Each entry is a {@code MethodHandle} resolved from the
 * {@link SymbolManifest}; once installed, {@code DispatchTable.handle(id)} is the {@code O(1)} path
 * behind every {@code invokedynamic} site the bytecode engine produced. (Wiring the engine into
 * NeoForge's class-loading via an {@code ITransformationService} so mod classes are transformed
 * on load is the documented next step; the table and bootstrap are in place for it.)
 *
 * <p>RU: Таблица диспетчеризации должна быть заполнена <em>до</em> того, как любой преобразованный
 * класс мода выполнит пониженный вызов API, поэтому загрузчик устанавливает её как можно раньше —
 * здесь, из {@code FMLConstructModEvent}. Каждая запись — {@code MethodHandle}, разрешённый из
 * {@link SymbolManifest}; после установки {@code DispatchTable.handle(id)} — путь {@code O(1)} за
 * каждой точкой {@code invokedynamic}, созданной движком байт-кода. (Подключение движка к
 * загрузке классов NeoForge через {@code ITransformationService}, чтобы классы модов
 * преобразовывались при загрузке — задокументированный следующий шаг; таблица и bootstrap для него
 * готовы.)
 */
final class DispatchBootstrap {

    private DispatchBootstrap() {
    }

    /** Reference dispatch target: doubles its input (mirrors the engine self-test). */
    static int doubler(int x) {
        return x * 2;
    }

    /**
     * Build the framework symbol manifest and install the dispatch table. Never throws — a failure
     * here must not abort the launch (the worst case is that lowered calls degrade, handled
     * elsewhere).
     *
     * @return the number of installed dispatch handles
     */
    static int installDefaultTable() {
        try {
            // Use the SHARED manifest so the dispatch table and the lowering transformer agree on IDs.
            // b: the manifest now lives in the boot-layer aetherium-transformer module.
            SymbolManifest manifest = AetheriumSymbols.MANIFEST;

            MethodHandle[] handles = new MethodHandle[manifest.size()];
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            handles[manifest.idOf("compute:doubler").orElseThrow()] =
                    lookup.findStatic(DispatchBootstrap.class, "doubler", MethodType.methodType(int.class, int.class));

            DispatchTable.install(handles);
            return DispatchTable.size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Defensive: never abort the launch from here.
            return DispatchTable.size();
        }
    }
}
