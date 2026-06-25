# UI — Declarative, Flexbox-like GUI Framework

*English. Russian mirror: [`../ru/ui.md`](../ru/ui.md).*

`aetherium-ui` is a cross-platform GUI framework with **no Minecraft imports**. A mod describes a screen
once, declaratively; the loader paints it. Layout, painting, and click dispatch all run offline — the
self-test builds, lays out, "renders", and clicks a screen with no game present.

## Creating a screen

Build the widget tree with the `Ui` factory — a React/Flexbox-style DSL:

```java
Widget<?> ui = Ui.column()
    .padding(8).gap(4)
    .background(UiColor.rgb(0x202024))
    .align(AlignItems.STRETCH)
    .children(
        Ui.label("Faction: Iron Vanguard").color(UiColor.WHITE),
        Ui.row().gap(4).children(
            Ui.button("Deposit", this::deposit).grow(1f),
            Ui.button("Close", this::close).grow(1f)),
        Ui.spacer().grow(1f));
```

`column`/`row` are flex containers; `grow(1f)` makes children share spare space (so the two buttons split
the row evenly); `align(STRETCH)` stretches children across the cross axis. Every modifier is type-safe and
chains, because `Widget` is self-typed.

For a full screen, subclass `AetheriumScreen` and return the tree from `build()`:

```java
public final class FactionScreen extends AetheriumScreen {
    public String title() { return "Faction"; }
    public Widget<?> build() { return /* the tree above */; }
}
```

## How it renders

`FlexLayout` computes an absolute `Rect` for every widget (a single-pass Flexbox solver: base sizes →
`grow` distribution → `justify` → cross-axis `align`/`STRETCH`). `UiRuntime` then paints the laid-out tree
through a two-method `UiRenderer` SPI (`fillRect` + `drawText`) and dispatches clicks to the top-most
`Button`. Text measurement is abstracted behind `UiMetrics` (the loader supplies font-accurate metrics; an
offline default is used for tests).

The **entire loader integration is one thin adapter**: implement `UiRenderer` over the platform's
`GuiGraphics`/`DrawContext`, call `UiRuntime.render(...)` each frame and `UiRuntime.click(...)` on
mouse-down. No Blaze3D type ever crosses into the framework.

## Proof

```bash
aetherium ui
```

The self-test lays out the faction screen in a 200×120 viewport, confirms the two `grow(1)` buttons split
the row, paints it through a recording renderer (3 fills, 3 text draws), and dispatches a click that fires
exactly the hit button's handler — all with no game running.
