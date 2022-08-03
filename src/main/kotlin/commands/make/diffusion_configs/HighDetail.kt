package commands.make.diffusion_configs

import commands.make.DiffusionConfig

val highDetail = DiffusionConfig(
    baseSize = 512,
    initScale = 1000,
    skipAugs = "[False]*600+[True]*200+[False]*200",
    satScale = 1.0,
    diffusionModel = "512x512_diffusion_uncond_finetune_008100",
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
    skipSteps = 20,
    clampGrad = true,
    fuzzyPrompt = false,
    rangeScale = 5000,
    randomizeClass = true,
    cutInnercut = "[5]*400+[5]*100+[10]*100+[10]*400",
    clipDenoised = false,
    perlinMode = "mixed",
    cutOverview = "[10]*400+[5]*100+[3]*100+[1]*400",
    transformationPercent = listOf(0.09),
    useSecondaryModel = true,
    perlinInit = false,
    cutIcgrayP = "[0.25]*100+[0.22]*100+[0.1]*100+[0.05]*100+[0]*600",
    randMag = 0.05,
    eta = 0.3
)