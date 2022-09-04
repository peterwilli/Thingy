package commands.make

data class DiffusionParameters(
    val artID: String,
    val seed: Int,
    val discoDiffusionParameters: DiscoDiffusionParameters?,
    val stableDiffusionParameters: StableDiffusionParameters?,
) {
    fun getPrompt(): String? {
        if (stableDiffusionParameters != null) {
            return stableDiffusionParameters.prompt
        }
        if (discoDiffusionParameters != null) {
            return discoDiffusionParameters.prompts.joinToString("|")
        }
        return null
    }
}