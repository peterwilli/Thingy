package commands.make.diffusion_configs

import commands.make.DiffusionConfig

val standardSmall = DiffusionConfig(
    initScale = 1000,
    skipAugs = true,
    satScale = 3.0,
    batchSize = 1,
    diffusionModel = "256x256_diffusion_uncond",
    steps = 150,
    nBatches = 1,
    clipModels = listOf("RN101::openai"),
    clipGuidanceScale = 2500,
    diffusionSamplingMode = "plms",
    cutnBatches = 1,
    cutIcPow = 1.65,
    tvScale = 9.0,
    onMisspelledToken = "ignore",
    useVerticalSymmetry = false,
    clipSequentialEvaluate = false,
    useHorizontalSymmetry = false,
    clampMax = 0.05,
    skipSteps = 0,
    clampGrad = true,
    fuzzyPrompt = false,
    rangeScale = 323,
    randomizeClass = true,
    cutInnercut = 3,
    clipDenoised = false,
    perlinMode = "mixed",
    cutOverview = 4,
    transformationPercent = listOf(0.09),
    useSecondaryModel = true,
    perlinInit = false,
    cutIcgrayP = "[0.2]*400+[0]*600",
    randMag = 0.05,
    eta = 0.3
)