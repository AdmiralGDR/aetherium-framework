// Aetherium Framework — sovereign native anti-tamper guard.
// Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
// See <https://www.gnu.org/licenses/>.
//
// A deliberately tiny, ZERO-DEPENDENCY shared object: no libc, no external package — it makes the two Linux
// syscalls it needs directly through Zig's std. Built with `zig build-lib -dynamic` and bundled under
// `native/` in aetherium-shield; the Java side (NativeGuard, FFM) degrades to a pure-Java path when it is
// absent. This aligns with the framework's MANIFEST axioms: Code is a Liability, Dependency Quarantine.
//
// Крошечный БЕЗ-ЗАВИСИМОСТНЫЙ .so: без libc и внешних пакетов — два нужных Linux-syscall делаются напрямую
// через std Zig. Java-сторона (NativeGuard, FFM) деградирует до чистого Java при отсутствии .so.

const std = @import("std");
const linux = std.os.linux;

/// FNV-1a 64-bit over a memory region — the fast native checksum used for runtime integrity verification.
export fn aeth_guard_fnv1a(ptr: [*]const u8, len: usize) u64 {
    var h: u64 = 0xcbf29ce484222325;
    var i: usize = 0;
    while (i < len) : (i += 1) {
        h ^= ptr[i];
        h *%= 0x100000001b3;
    }
    return h;
}

/// ABI version, so the Java side can refuse a mismatched library rather than trusting stale symbols.
export fn aeth_guard_abi() u32 {
    return 1;
}

/// The TracerPid from `/proc/self/status`: 0 = not traced, >0 = a debugger/attach is present, -1 = unavailable.
/// Reads the file with raw `open`/`read`/`close` syscalls — no libc, no allocations.
export fn aeth_guard_tracer_pid() i32 {
    const rc = linux.open("/proc/self/status", .{}, 0);
    if (@as(isize, @bitCast(rc)) < 0) return -1;
    const fd: i32 = @intCast(rc);
    defer _ = linux.close(fd);

    var buf: [4096]u8 = undefined;
    const rn = linux.read(fd, &buf, buf.len);
    if (@as(isize, @bitCast(rn)) < 0) return -1;
    const slice = buf[0..rn];

    const needle = "TracerPid:";
    const idx = std.mem.indexOf(u8, slice, needle) orelse return -1;
    var j = idx + needle.len;
    while (j < slice.len and (slice[j] == ' ' or slice[j] == '\t')) : (j += 1) {}
    var val: i32 = 0;
    while (j < slice.len and slice[j] >= '0' and slice[j] <= '9') : (j += 1) {
        val = val * 10 + @as(i32, slice[j] - '0');
    }
    return val;
}
