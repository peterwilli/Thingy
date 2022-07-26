package commands.make.diffusion_configs

import commands.make.DiffusionConfig

val pixelArtHard = DiffusionConfig(
    baseSize = 256,
    initScale = 1000,
    skipAugs = true,
    diffusionModel = "pixel_art_diffusion_hard_256",
    steps = 150,
    clipModels = listOf("ViT-B-32::laion2b_e16"),
    clipGuidanceScale = 5000,
    diffusionSamplingMode = "plms",
    cutnBatches = 1,
    cutIcPow = 1.65,
    tvScale = 0.0,
    onMisspelledToken = "ignore",
    useVerticalSymmetry = false,
    useHorizontalSymmetry = false,
    clampMax = 0.05,
    skipSteps = 0,
    clampGrad = true,
    fuzzyPrompt = true,
    rangeScale = 150,
    randomizeClass = true,
    cutInnercut = "[2]*600+[4]*400",
    clipDenoised = false,
    perlinMode = "mixed",
    cutOverview = "[4]*1000",
    transformationPercent = listOf(0.09),
    satScale = 0.0,
    useSecondaryModel = true,
    perlinInit = false,
    cutIcgrayP = "[0.2]*400+[0]*600",
    randMag = 0.05,
    eta = 0.3
)