package commands.make.diffusion_configs

private val wordsToConfig = mapOf(
    "pixelart,pixel art,sprite".split(",") to "pixelArtHard",
    "portrait,headshot,selfie".split(",") to "portrait",
)

fun catchRecommendedConfig(prompts: String): Pair<String, String>? {
    val lowerCasePrompts = prompts.lowercase()
    for ((words, configName) in wordsToConfig) {
        for (word in words) {
            if (lowerCasePrompts.contains(word)) {
                return word to configName
            }
        }
    }
    return null
}