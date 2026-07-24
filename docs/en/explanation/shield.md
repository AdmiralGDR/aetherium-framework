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
