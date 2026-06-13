/*
 * aetherium-native — JNI / FFM native bridge.
 *
 * EN: Knows `core` only. The C++ side (src/main/cpp → libaetherium_native.so) is compiled by a
 *     dedicated task in a later phase; for now this builds the Java-side bridge contracts.
 * RU: Знает только `core`. Сторона C++ (src/main/cpp → libaetherium_native.so) компилируется
 *     отдельной задачей на следующем этапе; сейчас собираются контракты моста на стороне Java.
 */

dependencies {
    api(project(":aetherium-core"))
}
