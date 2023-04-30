/**
 * Returns the first key corresponding to the given [value], or `null`
 * if such a value is not present in the map.
 */
fun <K, V> Map<K, V>.getKey(value: V) =
    entries.firstOrNull { it.value == value }?.key