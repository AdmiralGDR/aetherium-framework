# UI — Declarative, Flexbox-like GUI Framework

*English. Russian mirror: [`../ru/ui.md`](../../ru/explanation/ui.md).*

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

The loader integration is one thin adapter, and as of it is **implemented** (): the
loader ships `NeoForgeUiRenderer` (over `GuiGraphics`: `fill`/`drawString`/`enableScissor`/`disableScissor`),
`NeoForgeUiMetrics` (over `Minecraft.getInstance().font`, so widths are font-accurate — the offline
`UiMetrics.DEFAULT` 6px/char is wrong for Cyrillic), and `AetheriumScreenAdapter extends Screen` which calls
`UiRuntime.render/click/keyPressed/charTyped/scroll`. A mod shows a screen with
`AetheriumUi.open(AetheriumScreen)` — resolved via `ServiceLoader` exactly like `Platform.bridge()`, a no-op
off-client. No Blaze3D type ever crosses into the framework.

also makes `FlexLayout` **shrink** over-full children (flex-shrink; `Widget.shrink(0)` opts out) so
a row no longer paints off-screen, makes `ScrollPanel.intrinsicWidth` symmetric with its height, lets a
scroll position survive a rebuild (`ScrollPanel.child(...)` + a pending offset), and adds
`UiRuntime.audit(LaidOut)` which reports any child rect that escapes its parent.

## Proof

```bash
aetherium ui
```

The self-test lays out the faction screen in a 200×120 viewport, confirms the two `grow(1)` buttons split
the row, paints it through a recording renderer (3 fills, 3 text draws), and dispatches a click that fires
exactly the hit button's handler — all with no game running.

## (feedback)

`UiRuntime.audit(root, metrics)` also flags any label wider than its own box (box-containment alone passed a
screen whose every label was clipped); `Widget.minContentSize(true)` stops flex-shrink from cutting a label
below its content (flexbox `min-width:auto`). `Text.align(START|CENTER|END)` (only `Button` centred before).
`ScrollPanel.hasMeasured()` distinguishes "nothing to scroll" from "not measured yet"; `ScrollPanel.scrollbar(true)`
paints a track + proportional thumb and `PAGE_UP`/`PAGE_DOWN` scroll a page. `AetheriumUi.close()` mirrors
`open()`; `AetheriumScreen.build(Rect viewport)` enables responsive layout.

## (feedback) — the finished GUI is no longer re-blurred

was the first report from a session where the UI was actually visible in game, and it found a
high-severity render bug: **every Aetherium screen was smeared** — not the world behind it, the mod's own flat
`fill` rectangles and text. Proven from shipped bytecode: `AetheriumScreenAdapter.render` ended with
`super.render(...)`, and `Screen.render`'s **first instruction in 1.21.1 is another `renderBackground()`** →
`renderBlurredBackground` → `GameRenderer.processBlurEffect`, a post-process over the main render target — which
by then holds the finished interface. So the adapter blurred the world (correct), drew the scrim + UI, then
blurred *everything it had just drawn*.

The fix is one line: **don't call `super.render`.** The adapter registers no vanilla widgets, so `Screen.render`'s
only other effect — iterating `this.renderables` — is a no-op; the adapter now iterates `renderables` directly
(so vanilla widget support still works) and never re-runs the background. The single, intended world-blur at the
start of `render` stays.

Because the blur lived inside a framework class a mod cannot replace, there was no consumer workaround — so the
fix ships with a guard. `ArtifactVerifier`'s **`AE-UI-BLUR`** check (in `verifyJar` + `bootSmoke`, so it runs in
`./gradlew check`) asserts with ASM on the shipped loader jar that `AetheriumScreenAdapter.render` contains no
`INVOKESPECIAL Screen.render` — precisely the discipline the feedback asked for, catching the regression before a
player sees it. And a real client boot under Xvfb (`scripts/launch-check-client.sh`, see [verify](verify.md))
now proves the GUI actually renders.
