/*
 * Aetherium Framework — in-process JVM Native Memory Tracking (NMT) snapshots.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the JVM's own Native Memory Tracking summary from inside the process — the external witness
 * for the FFM zero-leak audit.
 *
 * <p>EN: The {@link org.aetherium.core.compute.ArenaAuditor} ledger proves the <em>API-level</em>
 * balance (every byte the entity store requested was released). NMT corroborates it at the
 * <em>JVM level</em>: it is HotSpot's authoritative account of actual native allocations. This monitor
 * invokes the {@code VM.native_memory summary} diagnostic command through the platform
 * {@code DiagnosticCommand} MBean (no external {@code jcmd} process) and parses the totals plus the
 * {@code Other} category — where FFM {@code Arena} memory is booked. Requires the JVM flag
 * {@code -XX:NativeMemoryTracking=summary}; without it {@link Snapshot#available()} is {@code false}
 * and callers degrade to the ledger-only proof.
 *
 * <p>RU: Реестр {@link org.aetherium.core.compute.ArenaAuditor} доказывает баланс на уровне API. NMT
 * подтверждает его на уровне JVM: это авторитетный учёт реальных нативных выделений HotSpot. Монитор
 * вызывает диагностическую команду {@code VM.native_memory summary} через MBean
 * {@code DiagnosticCommand} (без внешнего процесса {@code jcmd}) и разбирает итоги и категорию
 * {@code Other}, где учитывается память FFM {@code Arena}. Требует флага
 * {@code -XX:NativeMemoryTracking=summary}; без него {@link Snapshot#available()} равно {@code false}.
 */
public final class NmtMonitor {

    private static final Pattern TOTAL =
            Pattern.compile("Total:\\s*reserved=(\\d+)KB,\\s*committed=(\\d+)KB");
    private static final Pattern OTHER =
            Pattern.compile("-\\s*Other \\(reserved=(\\d+)KB, committed=(\\d+)KB\\)");

    private NmtMonitor() {
    }

    /**
     * One NMT summary reading (KB scale). {@code available=false} means NMT is off on this JVM.
     *
     * @param available          whether NMT was enabled and the summary parsed
     * @param totalReservedKb    total native reserved (all categories)
     * @param totalCommittedKb   total native committed (all categories)
     * @param otherReservedKb    the "Other" category (FFM/Unsafe allocations) reserved
     * @param otherCommittedKb   the "Other" category committed — the arena-leak signal
     */
    public record Snapshot(boolean available,
                           long totalReservedKb, long totalCommittedKb,
                           long otherReservedKb, long otherCommittedKb) {

        static final Snapshot UNAVAILABLE = new Snapshot(false, 0, 0, 0, 0);
    }

    /** Take an NMT summary snapshot; {@link Snapshot#UNAVAILABLE} if NMT is off or unreadable. */
    public static Snapshot snapshot() {
        String report = nativeMemorySummary();
        if (report == null || report.contains("Native memory tracking is not enabled")) {
            return Snapshot.UNAVAILABLE;
        }
        Matcher total = TOTAL.matcher(report);
        if (!total.find()) {
            return Snapshot.UNAVAILABLE;
        }
        long otherReserved = 0;
        long otherCommitted = 0;
        Matcher other = OTHER.matcher(report);
        if (other.find()) {
            otherReserved = Long.parseLong(other.group(1));
            otherCommitted = Long.parseLong(other.group(2));
        }
        return new Snapshot(true,
                Long.parseLong(total.group(1)), Long.parseLong(total.group(2)),
                otherReserved, otherCommitted);
    }

    /** True if this JVM was launched with {@code -XX:NativeMemoryTracking=summary} (or detail). */
    public static boolean enabled() {
        return snapshot().available();
    }

    private static String nativeMemorySummary() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("com.sun.management:type=DiagnosticCommand");
            return (String) server.invoke(name, "vmNativeMemory",
                    new Object[]{new String[]{"summary", "scale=KB"}},
                    new String[]{String[].class.getName()});
        } catch (Exception unavailable) {
            return null;
        }
    }
}
