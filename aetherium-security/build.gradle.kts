/*
 * aetherium-security — capability-based CIA-triad isolation.
 *
 * EN: Depends only on aetherium-core (error model). Provides default-deny capability grants, an
 *     FFM memory-bounds guard, and a reflection guard that protects framework-internal packages.
 *     Uses the FFM preview API, so it stays on --enable-preview (the default for this module).
 * RU: Зависит только от aetherium-core. Предоставляет default-deny выдачу возможностей, охрану
 *     границ FFM-памяти и охрану рефлексии, защищающую внутренние пакеты фреймворка.
 */

dependencies {
    api(project(":aetherium-core"))
}
