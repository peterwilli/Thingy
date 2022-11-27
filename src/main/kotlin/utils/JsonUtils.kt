package utils

import com.google.gson.JsonObject

fun JsonObject.withDefaults(defaults: JsonObject): JsonObject {
    for((key, value) in defaults.asMap()) {
        if (!this.has(key)) {
            this.add(key, value)
        }
    }
    return this
}