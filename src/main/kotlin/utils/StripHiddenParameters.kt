package utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject

fun JsonArray.stripHiddenParameters(): JsonArray {
    return this.stripHiddenParameters(arrayOf())
}

fun JsonArray.stripHiddenParameters(hiddenParameters: Array<String>): JsonArray {
    val result = JsonArray()
    for (item in this) {
        if (item.isJsonObject) {
            result.add(item.asJsonObject.stripHiddenParameters(hiddenParameters))
        } else {
            result.add(item)
        }
    }
    return result
}

fun JsonObject.stripHiddenParameters(): JsonObject {
    return this.stripHiddenParameters(arrayOf())
}

fun JsonObject.stripHiddenParameters(hiddenParameters: Array<String>): JsonObject {
    val result = this.deepCopy()
    for ((k, _) in this.asMap()) {
        if (k.startsWith("_") || hiddenParameters.contains(k)) {
            result.remove(k)
        }
    }
    return result
}