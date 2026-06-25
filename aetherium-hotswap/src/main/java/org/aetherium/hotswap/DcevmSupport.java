/*
 * Aetherium Framework — detection of DCEVM / HotswapAgent for structural hot-swap.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap;

import org.aetherium.injector.probe.InstrumentationSupport;

import java.lang.management.ManagementFactory;
import java.util.Locale;

/**
 * Detects an enhanced redefinition runtime (DCEVM / HotswapAgent) that lifts the stock JVM's "method
 * bodies only" limit, enabling <strong>structural</strong> hot-swap — adding/removing fields and methods
 * of a live class.
 *
 * <p>EN: Stock HotSpot's {@code redefineClasses} rejects any schema change. The Dynamic Code Evolution VM
 * (DCEVM) and HotswapAgent remove that restriction, so the very same {@link HotSwapEngine#redefine} call
 * succeeds on a structural edit. There is no JDK API to query this, so detection is heuristic: the VM name
 * advertised by DCEVM, the {@code dcevm} marker in the VM version, the presence of HotswapAgent on the
 * classpath, or an explicit {@code -Daetherium.hotswap.structural=true} override (for CI/testing). When
 * absent, structural edits still fail <em>gracefully</em> (the engine reports {@code REJECTED}); the modder
 * just needs a restart for schema changes.
 * RU: Стоковый {@code redefineClasses} HotSpot отвергает любое изменение схемы. DCEVM и HotswapAgent
 * снимают это ограничение, поэтому тот же вызов {@link HotSwapEngine#redefine} проходит на структурном
 * изменении. API JDK для запроса этого нет, поэтому детект эвристический: имя VM от DCEVM, маркер
 * {@code dcevm} в версии VM, наличие HotswapAgent на classpath или явный
 * {@code -Daetherium.hotswap.structural=true}. При отсутствии структурные правки отказываются мягко.
 */
public final class DcevmSupport {

    private static final String OVERRIDE_PROPERTY = "aetherium.hotswap.structural";
    private static final String HOTSWAP_AGENT_CLASS = "org.hotswap.agent.HotswapAgent";

    private DcevmSupport() {
    }

    /** True if the running VM is (or reports itself as) DCEVM. */
    public static boolean isDcevmPresent() {
        String vmName = safe(ManagementFactory.getRuntimeMXBean().getVmName());
        String vmVersion = safe(System.getProperty("java.vm.version"));
        String vmInfo = safe(System.getProperty("java.vm.info"));
        return vmName.contains("dynamic code evolution")
                || vmVersion.contains("dcevm")
                || vmInfo.contains("dcevm");
    }

    /** True if HotswapAgent is on the classpath (it brokers structural redefinition). */
    public static boolean isHotswapAgentPresent() {
        try {
            Class.forName(HOTSWAP_AGENT_CLASS, false, DcevmSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    /** Explicit operator override ({@code -Daetherium.hotswap.structural=true}). */
    public static boolean isForced() {
        return Boolean.getBoolean(OVERRIDE_PROPERTY);
    }

    /**
     * EN: True if structural hot-swap (add/remove members live) is actually usable: an enhanced runtime
     * is present AND {@link InstrumentationSupport instrumentation} is available to perform the redefine.
     * RU: True, если структурный hot-swap реально доступен: усиленный рантайм присутствует И доступна
     * {@link InstrumentationSupport инструментация} для переопределения.
     */
    public static boolean structuralRedefineAvailable() {
        return (isForced() || isDcevmPresent() || isHotswapAgentPresent())
                && InstrumentationSupport.available();
    }

    /** A short human-readable summary for logs / the CLI. */
    public static String describe() {
        if (structuralRedefineAvailable()) {
            String via = isForced() ? "forced" : isDcevmPresent() ? "DCEVM" : "HotswapAgent";
            return "structural hot-swap AVAILABLE via " + via + " (add/remove fields & methods live)";
        }
        if (isDcevmPresent() || isHotswapAgentPresent() || isForced()) {
            return "enhanced runtime detected but no instrumentation; structural hot-swap unavailable";
        }
        return "standard JVM (method-body redefinition only; install DCEVM/HotswapAgent for structural)";
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
