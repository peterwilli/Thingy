package commands.make

data class DiffusionParameters(
    val artID: String,
    val seed: Int,
    val discoDiffusionParameters: DiscoDiffusionParameters?,
    val stableDiffusionParameters: StableDiffusionParameters?
)