package commands.make.diffusion_configs

import commands.make.DiffusionConfig

val standardSmall = DiffusionConfig(
    initScale = 1000,
    skipAugs = true,
    satScale = 1.0,
    diffusionModel = "512x512_diffusion_uncond_finetune_008100",
    steps = 150,
    clipModels = listOf("ViT-B-32::laion2b_e16"),
    clipGuidanceScale = 5000,
    diffusionSamplingMode = "plms",
    cutnBatches = 1,
    cutIcPow = 1.65,
    tvScale = 9.0,
    onMisspelledToken = "ignore",
    useVerticalSymmetry = false,
    useHorizontalSymmetry = false,
    clampMax = 0.05,
    skipSteps = 0,
    clampGrad = true,
    fuzzyPrompt = false,
    rangeScale = 150,
    randomizeClass = true,
    cutInnercut = "[3]*1000",
    clipDenoised = false,
    perlinMode = "mixed",
    cutOverview = "[4]*1000",
    transformationPercent = listOf(0.09),
    useSecondaryModel = true,
    perlinInit = false,
    cutIcgrayP = "[0.2]*400+[0]*600",
    randMag = 0.05,
    eta = 0.3
)