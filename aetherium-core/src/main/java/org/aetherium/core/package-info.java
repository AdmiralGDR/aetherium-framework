/**
 * Aetherium Framework — stable, loader-agnostic public API (the leaf module).
 *
 * <p><b>EN.</b> This package defines the contracts every other module depends on and nothing
 * internal depends back upon (see {@code ARCHITECTURE.md} ). The two pillars are:
 * <ul>
 *   <li>the {@link org.aetherium.core.SymbolManifest Symbol Manifest} — the dense, build-assigned
 *       map of abstract API symbols to integer IDs that powers {@code O(1)} runtime dispatch; and</li>
 *   <li>the {@link org.aetherium.core.CapabilityRegistry Capability / Fallback registry} — the
 *       load-time probe that selects the highest viable tier
 *       ({@code FFM → JNI → PURE_JAVA → DISABLED}) for each capability.</li>
 * </ul>
 *
 * <p><b>RU.</b> Этот пакет определяет контракты, от которых зависят все остальные модули и от
 * которых ничто внутреннее не зависит обратно (см. {@code ARCHITECTURE.md} ). Две опоры:
 * Манифест Символов (плотное отображение абстрактных символов API в целочисленные ID для
 * {@code O(1)}-диспетчеризации) и Реестр Возможностей/Откатов (зондирование во время загрузки,
 * выбирающее наивысший доступный уровень {@code FFM → JNI → PURE_JAVA → DISABLED}).
 */
package org.aetherium.core;
