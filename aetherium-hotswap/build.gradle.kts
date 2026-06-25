// aetherium-hotswap — live class hot-swap engine (zero-downtime developer iteration).
//
// EN: Watches the modder's build output for changed .class files and pushes the new bytecode straight
//     into the running game via JVM Instrumentation.redefineClasses() — no Minecraft restart. Reuses
//     aetherium-injector's Instrumentation acquisition (the same Attach-API self-attach the ephemeral
//     JFR probes use) and its LiveHookGraph, so injected hooks are re-resolved live after each swap.
// RU: Следит за выводом сборки мода на предмет изменённых .class и пушит новый байт-код прямо в
//     работающую игру через JVM Instrumentation.redefineClasses() — без перезапуска Minecraft.
//     Переиспользует получение Instrumentation из aetherium-injector (тот же self-attach через
//     Attach API, что и у эфемерных JFR-зондов) и его LiveHookGraph, поэтому внедрённые хуки
//     заново разрешаются вживую после каждого свопа.
dependencies {
    // Brings InstrumentationSupport + LiveHookGraph, and ASM transitively (injector exposes it as api).
    api(project(":aetherium-injector"))

    testImplementation(libs.junit.jupiter)
}
