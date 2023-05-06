package utils

import java.util.concurrent.TimeUnit


fun getEta(percentCompleted: Float, lastPercentCompleted: Float, timeSinceLastUpdate: Long): Long {
    val steps = 1 / (percentCompleted - lastPercentCompleted)
    return (peterDate() - timeSinceLastUpdate) * (steps * (1 - percentCompleted)).toLong()
}

fun etaToString(estimatedSeconds: Long): String {
    return String.format(
        "%02dm%02ds",
        TimeUnit.SECONDS.toMinutes(estimatedSeconds),
        TimeUnit.SECONDS.toSeconds(estimatedSeconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(estimatedSeconds))
    )
}