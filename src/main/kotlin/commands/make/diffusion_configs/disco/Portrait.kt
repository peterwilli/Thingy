package commands.make.diffusion_configs

import commands.make.DiscoDiffusionConfig

val portrait = DiscoDiffusionConfig(
    baseSize = 512,
    initScale = 1000,
    skipAugs = "[False]*600+[True]*200+[False]*200",
    satScale = 1.0,
    diffusionModel = "portrait_generator_v001_ema_0.9999_1MM",
    steps = 150,
    clipModels = listOf("ViT-B-32::openai", "ViT-B-16::openai", "RN50::openai"),
    clipGuidanceScale = 5000,
    cutnBatches = 1,
    cutIcPow = "[0]*50+[0.05]*350+[0.1]*200+[0.7]*200+[1.0]*200",
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
    cutInnercut = "[1]*400+[1]*100+[5]*100+[7]*400",
    clipDenoised = false,
    perlinMode = "mixed",
    cutOverview = "[5]*400+[3]*100+[2]*100+[1]*400",
    transformationPercent = listOf(0.09),
    useSecondaryModel = true,
    perlinInit = false,
    cutIcgrayP = "[0.2]*400+[0]*600",
    randMag = 0.05,
    eta = 0.3
)