package commands.make.diffusion_configs

val diffusionConfigs = mapOf(
    "standardSmall" to (standardSmall to "Standard"),
    "highDetail" to (highDetail to "High detail"),
    "pixelArt4K" to (pixelArt4K to "Pixel art (4K)"),
    "pixelArtHard" to (pixelArtHard to "Pixel art (hard)"),
    "pixelArtSoft" to (pixelArtSoft to "Pixel art (soft)"),
    "portrait" to (portrait to "Portrait")
)
val diffusionConfigInstanceToName = diffusionConfigs.keys.associateBy({
    diffusionConfigs[it]!!.first
}) {
    diffusionConfigs[it]!!.second
}