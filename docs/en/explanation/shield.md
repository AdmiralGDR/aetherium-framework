# The Sovereign Shield — protecting mods and authors from reverse-engineering and AI analysis

`aetherium-shield` is a build/load-time **bytecode protector** for a mod author's *own* compiled classes.
Its goal is explicit and twofold: make a mod hard to reverse-engineer by a human, **and hard to analyze or
decompile by an automated tool — an LLM/AI included.** Protection is opt-in, and the author remains
responsible for their own mod's license terms.

## Why a dedicated anti-AI angle

Classic obfuscation was designed against human analysts. Automated decompilers and, increasingly, LLMs
change the threat model: a model can ingest a whole jar in seconds and reconstruct intent from three
signals that ordinary bytecode leaks for free —

1. **Words** — string literals (error messages, config keys, URLs, registry ids) are read verbatim as
   semantic labels.
2. **Names** — class, package, and variable names are the model's skeleton for "what this does".
3. **Shape** — clean, reducible control flow lets a model recover the original structure and summarize it.

The shield removes or corrupts each signal, so an automated pass is left with opaque byte-salad, synthetic
names, and tangled flow. It is defense-in-depth: no single layer is unbreakable, but together they raise the
cost — human or machine — far above reading a plaintext jar.

## The layers

Each pass is an independent `ClassTransformer` and runs **inside the `aetherium-bytecode` verification
sandbox**, so a pass that would ever produce invalid bytecode reverts *that one class* to its original bytes
and the build never breaks. Correctness dominates protection.

| Pass | Class | Denies the analyst… | Notes |
|---|---|---|---|
| Debug strip | `DebugStripTransformer` | line numbers + the author's variable/parameter names | removes `SourceFile`, `LineNumberTable`, `LocalVariableTable`, `MethodParameters` |
| String encryption | `StringEncryptionTransformer` | **the "what"** — every readable literal | each `LDC "s"` → `ldc <cipher>; ldc <key>; invokestatic decode`; plaintext never exists in the file |
| Control-flow obfuscation | `ControlFlowObfuscator` | **the "how"** — clean, reducible flow | inserts an opaque predicate + dead `throw` block per method |
| Renaming | `Renamer` | **the semantic map** — class/package/member names | classes → `o/a`, `o/b`…; private methods/fields → `a`, `b`…; keep-list preserves by-name/service resolution |
| Integrity manifest | `IntegrityManifest` | tamper without detection | SHA-256 per class; a mismatch proves the jar was patched after protection |
| Author watermark | `WatermarkAttribute` | anonymity of a leak | an invisible class attribute carrying `author\|timestamp` — a ripped jar stays traceable to its author |

### String encryption in detail

The single biggest leak in a mod jar is plaintext. The pass XOR-encrypts each literal with a per-literal
key, stores **only the ciphertext** in the constant pool, and rewrites the load into a call to a tiny
synthetic decoder (`$aeth$x`) added to the class. The readable text exists only for an instant in memory
during use — `grep` finds nothing, and a model reading the class sees a decode call over opaque bytes.

> Caveat: a `static final String` compile-time constant is *inlined by javac* at its use sites (where this
> pass encrypts it) but the declaring field also keeps a `ConstantValue` copy. Don't mark a genuine secret
> as a compile-time constant; use a normal field or a method return.

### Renaming and the keep-list

Renaming is the strongest anti-analysis pass and the most dangerous, because anything resolved *by name* at
runtime must survive. `KeepList` pins the framework runtime (`org/aetherium/**`), the `@AetheriumInit`
generated entrypoint, and every class named in a `META-INF/services` file. Only **private** methods and
fields are renamed (no external references, no overrides), and **records are left intact** (their
component/accessor/constructor names are load-bearing). Every reference — including the method handles behind
lambdas — is rewritten together, so the result still loads and runs.

### Integrity and watermark

The integrity manifest (`shield-integrity.txt`, a `binaryName=sha256` ledger) turns silent tampering into a
detectable event — a cracked jar, an injected backdoor, or a defeated check all change a hash. The watermark
is a non-standard class attribute the JVM ignores; it is not referenced by any code, so a naive strip pass
misses it, and it makes a leaked jar traceable to its author — protecting the *author*, not only the code.

## Using it

### Gradle (recommended)

```kotlin
aetherium {
    version = "1.0.0-SNAPSHOT"
    shield = true
    shieldAuthor = "a downstream mod"
    // shieldRename = true   // advanced: also rename classes/private members (understand the keep-list)
}
```

The plugin registers an `aetheriumShield` task that `jar` depends on. It runs as a **forked** JVM (the
framework runtime is `--enable-preview`, which the Gradle daemon refuses to load) and obfuscates the compiled
classes in place before packaging. Class renaming is **off by default** — it moves files and would break
by-name/service resolution unless the keep-list is complete.

### CLI

```console
$ aetherium protect build/classes/java/main --author "a downstream mod"      # in place, no rename (safe)
$ aetherium protect build/classes/java/main --author "a downstream mod" --rename
$ aetherium shield        # run the end-to-end self-test
```

### Programmatic

```java
Shield.Result r = Shield.protect(classesByBinaryName, ShieldOptions.standard("a downstream mod"),
                                 new KeepList().keepService("com.example.MyMod"), verifyLoader);
r.protectedClasses();   // binaryName -> protected bytes
r.integrity();          // the tamper manifest
r.revertedClasses();    // how many reverted (shipped valid but un-obfuscated)
```

## Guarantees (proven by `aetherium shield` / `ShieldSelfTest`)

- The plaintext of an encrypted string is **absent** from the protected bytes.
- Debug metadata is gone.
- A class renamed to an opaque name **still loads and runs**, decoding its strings correctly at runtime.
- A one-byte tamper is **rejected** by the integrity manifest.
- The author watermark is **extractable** from the protected bytes.
- Protecting an unprotectable input (garbage bytes) **reverts cleanly** with a diagnostic — never a crash.

## Limits

Obfuscation raises cost; it is not encryption of the running program. A determined analyst with a live JVM
can still observe behaviour, and the string decoder is present in the bytecode. The shield's job is to make
*bulk, automated* extraction — the cheap path an AI takes — expensive, and to make theft *traceable* and
*detectable*. Use it together with `aetherium-security` (capability isolation) for defense-in-depth.

## — native guard, stronger passes, and correctness

**Runtime enforcement, not just a manifest.** `ModVerifier` merges every `shield-integrity.txt` on the
classpath and re-hashes each class (SHA-256); the loader runs it at init and, by default, **refuses a
tampered mod** (`-Daetherium.shield.enforce=false` for report-only). See [in-game verification](verify.md).

**The sovereign native guard (Zig, zero-dependency).** `NativeGuard` binds a tiny freestanding `.so`
(`src/main/zig/aetherium_guard.zig`) via FFM — **no libc, no external package**, built with `zig build-lib`
only when Zig is on `PATH`, otherwise the guard degrades to pure Java (the FFM→pure-Java ladder). It provides
a fast native FNV-1a checksum and a `/proc/self/status` **TracerPid** read for debugger/attach detection.
Aligns with MANIFEST's *Code is a Liability* / *Dependency Quarantine*. `aetherium guard` reports the status.

**Stronger anti-AI passes.** The control-flow opaque predicate is now seeded in `<clinit>` from a runtime
arithmetic identity — `(t·t + t) & 1`, always 0 because `n²+n` is always even — so "the flag is always 0"
static analysis no longer folds the guard away. A new `JunkCodeTransformer` inserts synthetic, never-called
decoy methods so an automated tool cannot tell a decoy from a real method by name, strings, or a partial
call graph. Both are on by default and still run in the verification sandbox.

**Correctness (feedback ).** With `--rename`, `ShieldDirectory` now rewrites every
`META-INF/aetherium/*.index` (`content.index`, `behaviors.index`) through the rename map, so the content
processor's name-based registry keeps pointing at the (renamed) class — no more "green build, broken jar".
A fail-loud guard refuses to ship if any index still names a renamed-away class. Renaming is therefore
**safe by default** again. And the Gradle task now passes the mod's runtime classpath so control-flow frames
recompute — far fewer classes revert un-protected.

## — the string decoder leaves the bytecode

With `nativeStringDecrypt` (on in `ShieldOptions.standard()`), a protected class no longer carries the XOR
decode routine at all: each literal lowers to `ldc <cipher>; ldc <key>; invokestatic ShieldRuntime.decode`,
and the decode runs **natively** in the Zig guard (`aeth_guard_xor16`) — or the identical pure-Java routine
when the `.so` is absent. A decompiler/AI sees only ciphertext and a call; the "how" is gone from the class.

## — the magic numbers leave the bytecode too

A decompiler — and an LLM reconstructing intent — anchors on literal constants: table sizes, bit masks,
protocol tags, opcodes. The `ConstantObfuscator` pass (on by default in `ShieldOptions.standard()`, order 32)
removes those anchors. Every non-trivial integer push (`BIPUSH`/`SIPUSH`/`LDC int`) is rewritten as
`(v ^ K) ^ K`, where the second `K` is read from an opaque static field `$aeth$k` seeded to `K` in `<clinit>`
through the same identity the control-flow pass uses (`(t²+t)&1` is always 0, so `K + 0 == K`, but a tool
cannot fold it without proving `n²+n` is even). The literal on screen becomes `v ^ K` — not `v` — and it
cannot be constant-folded back because the key is only known at runtime.

The pass is purely local and stack-neutral, so it composes with every other layer and runs inside the
verification sandbox (a bad rewrite reverts, never breaking the build). The key derives from the class name,
so it is deterministic and protected jars stay byte-reproducible (MANIFEST axiom V). Zero dependency: pure
ASM, no runtime helper. `aetherium shield` proves it — the magic number `21` is gone from `compute()` while
`compute(20)` still returns `41` through the full protect pipeline (string-encrypt → control-flow →
constants → junk → rename → watermark → integrity).

## — verify the shipped protection (`harden-check`)

Protecting a jar is not the same as *proving* it was protected. `verifyJar` brought that discipline to the
launch; `ShieldAudit` + `aetherium harden-check <jar|dir>` bring it to the shield. The audit reads the shipped
bytes with the framework's own ASM and reports, per class: **strings encrypted** (no readable code-string
constant survives — because the encryption pass turns every `LDC` into XOR ciphertext whose chars scatter
across the whole 16-bit range, a run of five ASCII letters is the unmistakable signature of an
*un*-encrypted literal), **debug stripped** (no `SourceFile`, line numbers, or local-variable names), and
**watermark present** (leaked-jar traceability). It exits non-zero when any class is still analysable, so CI
can gate on it:

```
aetherium harden-check build/libs/my-mod.jar
# → PROTECTED ✓ — no class leaks readable strings or debug metadata
```

`ShieldAuditSelfTest` (folded into `aetherium shield`) proves the gate can *fail*: it audits a class before
protection (reported leaky, plaintext named) and after (analysis-resistant, watermarked) — an audit that
could not fail would be worthless.

**Constant fields are an advisory, by design.** The audit distinguishes readable *code strings* (the leak the
shield closes) from readable `static final String` **constant-field** values. The string-encryption pass does
not rewrite the latter: javac inlines a compile-time constant at every call site (where it *is* encrypted),
and such constants are usually public API — registry ids like `"minecraft:air"`. So `harden-check` reports
them as an advisory ("move secrets out of `static final String` if they must stay hidden") rather than failing
the artifact. This is the honest scope: the gate verifies the shield's actual contract.
