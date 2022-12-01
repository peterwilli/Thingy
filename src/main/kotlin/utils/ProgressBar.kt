package utils

import kotlin.math.floor

fun asciiProgressBar(percentage: Double, length: Int = 20): String {
    val result = StringBuilder()
    val onStart = floor(percentage * length).toInt()
    for (i in 0 until length) {
        result.append(
            if (i > onStart) {
                "▱"
            } else {
                "▰"
            }
        )
    }
    result.append(" **${floor(percentage * 100).toInt()}**%")
    return result.toString()
}