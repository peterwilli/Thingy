package commands.make.diffusion_configs.disco

import commands.make.diffusion_configs.*

val discoDiffusionConfigs = mapOf(
    "standardSmall" to (standardSmall to "Standard"),
    "highDetail" to (highDetail to "High detail"),
    "pixelArt4K" to (pixelArt4K to "Pixel art (4K)"),
    "pixelArtHard" to (pixelArtHard to "Pixel art (hard)"),
    "pixelArtSoft" to (pixelArtSoft to "Pixel art (soft)"),
    "diorama" to (diorama to "Diorama effect"),
    "portrait" to (portrait to "Portrait")
)
val discoDiffusionConfigInstanceToName = discoDiffusionConfigs.keys.associateBy({
    discoDiffusionConfigs[it]!!.first
}) {
    discoDiffusionConfigs[it]!!.second
}