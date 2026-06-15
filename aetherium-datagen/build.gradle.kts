// aetherium-datagen — pure, build-time asset/JSON generator (the DataGen Engine).
//
// EN: STRICTLY PURE. It depends only on aetherium-core and MUST NOT import a single
//     net.minecraft / net.neoforged type. It turns declarative ContentEntry records into the raw
//     resource-pack JSON (models, blockstates, loot tables, lang) that vanilla expects, writing
//     plain files — entirely outside the game server/client. The annotation processor in
//     aetherium-content drives it during the consumer's Gradle build.
// RU: СТРОГО ЧИСТЫЙ. Зависит только от aetherium-core и НЕ импортирует ни одного типа
//     net.minecraft / net.neoforged. Превращает декларативные записи ContentEntry в «сырой» JSON
//     ресурс-пака (модели, blockstates, loot-таблицы, lang), записывая обычные файлы — полностью
//     вне игрового сервера/клиента. Аннотационный процессор из aetherium-content управляет им во
//     время сборки потребителя.
dependencies {
    api(project(":aetherium-core"))
}
