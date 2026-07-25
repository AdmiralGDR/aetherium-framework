# UI — декларативный Flexbox-подобный GUI-фреймворк

*Русский. Английская версия: [`../en/ui.md`](../../en/explanation/ui.md).*

`aetherium-ui` — кроссплатформенный GUI-фреймворк **без импортов Minecraft**. Мод описывает экран
декларативно один раз; загрузчик его рисует. Раскладка, отрисовка и обработка кликов работают офлайн —
самотест строит, раскладывает, «рисует» и кликает экран без запущенной игры.

## Создание экрана

Дерево виджетов строится фабрикой `Ui` — DSL в духе React/Flexbox:

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

`column`/`row` — flex-контейнеры; `grow(1f)` заставляет детей делить свободное место (две кнопки делят
строку поровну); `align(STRETCH)` растягивает детей по поперечной оси. Каждый модификатор типобезопасен и
цепочечный, потому что `Widget` самотипизирован.

Для полного экрана наследуйте `AetheriumScreen` и верните дерево из `build()`:

```java
public final class FactionScreen extends AetheriumScreen {
    public String title() { return "Faction"; }
    public Widget<?> build() { return /* дерево выше */; }
}
```

## Как это рисуется

`FlexLayout` вычисляет абсолютный `Rect` для каждого виджета (однопроходный Flexbox-солвер: базовые
размеры → распределение `grow` → `justify` → `align`/`STRETCH` по поперечной оси). Затем `UiRuntime`
рисует разложенное дерево через SPI `UiRenderer` из двух методов (`fillRect` + `drawText`) и
диспетчеризует клики верхней `Button`. Измерение текста скрыто за `UiMetrics` (загрузчик даёт точные по
шрифту метрики; офлайн используется значение по умолчанию).

Интеграция с загрузчиком — один тонкий адаптер, и с Фазы 22 он **реализован** (итерация 2, ): загрузчик
поставляет `NeoForgeUiRenderer` (поверх `GuiGraphics`: `fill`/`drawString`/`enableScissor`/`disableScissor`),
`NeoForgeUiMetrics` (поверх `Minecraft.getInstance().font` — точные ширины; офлайн 6px/символ врёт для
кириллицы) и `AetheriumScreenAdapter extends Screen`, вызывающий
`UiRuntime.render/click/keyPressed/charTyped/scroll`. Мод открывает экран через
`AetheriumUi.open(AetheriumScreen)` — разрешается через `ServiceLoader`, как `Platform.bridge()`, и no-op вне
клиента. Ни один тип Blaze3D не попадает во фреймворк.

С Фазы 22 `FlexLayout` также **сжимает** детей при нехватке места (flex-shrink; `Widget.shrink(0)` — не
сжимать), `ScrollPanel.intrinsicWidth` симметричен по высоте, позиция прокрутки переживает перестройку
(`ScrollPanel.child(...)` + отложенное смещение), а `UiRuntime.audit(LaidOut)` ловит вылет ребёнка за
пределы родителя.

## Доказательство

```bash
aetherium ui
```

Самотест раскладывает экран фракции во вьюпорте 200×120, подтверждает, что две кнопки `grow(1)` делят
строку, рисует его через записывающий рендерер (3 заливки, 3 отрисовки текста) и диспетчеризует клик,
вызывающий ровно обработчик нужной кнопки — всё без запущенной игры.
