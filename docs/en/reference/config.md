# ConfigStore — typed configuration (reference)

`aetherium-config` provides `ConfigStore<T>`, so no mod re-implements JSON loading, validation, atomic
writing, and hot-reload. It is built on the depth/size-hardened `TreeNode` (from `aetherium-network`); there
is no external JSON library.

## API

```java
public final class ConfigStore<T> implements AutoCloseable {
    interface Codec<T> { TreeNode toTree(T v); T fromTree(TreeNode t); }

    static <T> ConfigStore<T> open(Path file, Codec<T> codec, T defaults); // writes defaults if missing
    T get();
    void set(T value);            // normalize + atomic write
    void save();                  // atomic write of the current value
    void reload();                // re-read, normalize, notify listeners
    ConfigStore<T> validate(UnaryOperator<T> normalizer);  // clamp/fill on every load
    ConfigStore<T> onReload(Consumer<T> listener);
    ConfigStore<T> watch();       // WatchService hot-reload (80 ms settle)
    void close();
}
```

## Behaviour

| Concern | Guarantee |
|---|---|
| **Format** | pretty-printed, sorted-key JSON via `TreeJson` (human-editable) |
| **Atomicity** | writes go to a `.tmp` sibling then `ATOMIC_MOVE` — a crash mid-save never truncates the file |
| **Hardening** | `TreeJson.parse` is a bounded recursive-descent parser: max depth, no trailing garbage, malformed input → `AetheriumException` |
| **Hot-reload** | `watch()` starts a daemon `WatchService`; an admin edit reloads, re-validates, and notifies `onReload` listeners |
| **Containment** | a malformed hand-edit is contained — the last-good value stays live and the watcher survives |
| **Validation** | `validate(normalizer)` clamps/fills every loaded value (applied to the current value immediately) |

## Example

```java
record FactionConfig(String name, int maxMembers, double taxRate) {}

ConfigStore<FactionConfig> store = ConfigStore.open(
        dir.resolve("faction.json"), FACTION_CODEC, new FactionConfig("Iron Vanguard", 20, 0.05))
    .validate(c -> new FactionConfig(c.name(), Math.max(1, Math.min(50, c.maxMembers())), c.taxRate()))
    .onReload(c -> LOG.info("config reloaded: {}", c))
    .watch();

FactionConfig cfg = store.get();
```

Run `aetherium config` for the end-to-end self-test (defaults, round-trip, validate, hot-reload, contained
bad edit).

## `reload()` returns a result (, )

`ConfigStore.reload()` no longer throws on a malformed file — it returns a `ReloadResult(boolean ok,
Optional<Diagnostic> diagnostic)` and keeps the last-good value. A direct caller (e.g. an admin
`/reload config`) behaves exactly like the watch thread. `InventoryAccess.EMPTY` and
`PlayerHandle.hasPermission(int)` () round out the edge ergonomics.
