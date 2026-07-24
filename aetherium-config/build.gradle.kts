// aetherium-config — world/mod configuration store.
//
// EN: A framework-blessed ConfigStore<T> so every mod stops re-writing its own JSON loader, validation,
//     atomic writer, and hot-reload (~600 lines per consumer, per the a downstream mod feedback). Built on the
//     depth/size-hardened TreeNode from aetherium-network; no external JSON library. Pure: no MC types.
// RU: Общий ConfigStore<T>, чтобы моды не переписывали свой JSON-загрузчик, валидацию, атомарную запись и
//     горячую перезагрузку. Построен на устойчивом TreeNode из aetherium-network; без внешних JSON-библиотек.
dependencies {
    api(project(":aetherium-core"))
    api(project(":aetherium-network"))

    testImplementation(libs.junit.jupiter)
}
