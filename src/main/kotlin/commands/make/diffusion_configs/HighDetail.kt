package commands.make.diffusion_configs

import commands.make.DiffusionConfig

val highDetail = DiffusionConfig(
    baseSize = 512,
    initScale = 1000,
    skipAugs = true,
    satScale = 1.0,
    diffusionModel = "512x512_diffusion_uncond_finetune_008100",
    steps = 150,
    clipModels = listOf("ViT-B-32::laion2b_e16"),
    clipGuidanceScale = 5000,
    cutnBatches = 1,
    cutIcPow = "[0]*200+[0]*200+[5]*200+[10]*200+[20]*200",
    tvScale = 9.0,
    onMisspelledToken = "ignore",
    useVerticalSymmetry = false,
    useHorizontalSymmetry = false,
    clampMax = 0.05,
    skipSteps = 0,
    clampGrad = true,
    fuzzyPrompt = false,
    rangeScale = 5000,
    randomizeClass = true,
    cutInnercut = "[2]*200+[4]*200+[8]*200+[16]*200+[32]*200",
    clipDenoised = false,
    perlinMode = "mixed",
    cutOverview = "[8]*200+[6]*200+[4]*200+[0]*200+[0]*200",
    transformationPercent = listOf(0.09),
    useSecondaryModel = true,
    perlinInit = false,
    cutIcgrayP = "[0.25]*100+[0.22]*100+[0.1]*100+[0.05]*100+[0]*600",
    randMag = 0.05,
    eta = 0.3
)