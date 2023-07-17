import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.math.pow
import kotlin.random.Random

const val miniManual = "Start by making one using `/make`, `/deliberate`, or `/stable_diffusion`"
val gson = Gson()
const val defaultNegative = "out of frame, lowres, text, error, cropped, worst quality, low quality, jpeg artifacts, ugly, duplicate, morbid, mutilated, out of frame, extra fingers, mutated hands, poorly drawn hands, poorly drawn face, mutation, deformed, blurry, dehydrated, bad anatomy, bad proportions, extra limbs, cloned face, disfigured, gross proportions, malformed limbs, missing arms, missing legs, extra arms, extra legs, fused fingers, too many fingers, long neck, username, watermark, signature,"
val imageModels: HashMap<String, String> = hashMapOf(
    "Stable Diffusion" to "stable_diffusion",
    "SDXL" to "sd_xl",
    "Deliberate" to "deliberate",
    "Photon (realistic photos)" to "photon",
    "Deep-Floyd IF" to "deep_floyd_if")

val audioModels: HashMap<String, String> = hashMapOf(
    "Bark" to "bark",
    "MusicGen" to "musicgen")

fun getDeepFloydJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("ar", "1:1")
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("noise_level", 100)
    obj.addProperty("steps", 50)
    obj.addProperty("negative_prompt", defaultNegative)
    obj.add("embeds", JsonArray(0))
    return obj
}

fun getSDUpscaleJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("noise_level", 100)
    obj.addProperty("tile_border", 32)
    obj.addProperty("tiling_mode", "linear")
    obj.addProperty("guidance_scale", 6)
    obj.addProperty("original_image_slice", 32)
    return obj
}

fun getBarkAudioDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("duration", 15)
    obj.add("embeds", JsonArray(0))
    return obj
}

fun getSdJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("ar", "1:1")
    obj.addProperty("size", 512)
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("guidance_scale", 9.0)
    obj.addProperty("steps", 25)
    obj.addProperty("negative_prompt", "out of frame, lowres, text, error, cropped, worst quality, low quality, jpeg artifacts, ugly, duplicate, morbid, mutilated, out of frame, extra fingers, mutated hands, poorly drawn hands, poorly drawn face, mutation, deformed, blurry, dehydrated, bad anatomy, bad proportions, extra limbs, cloned face, disfigured, gross proportions, malformed limbs, missing arms, missing legs, extra arms, extra legs, fused fingers, too many fingers, long neck, username, watermark, signature,")
    obj.add("embeds", JsonArray(0))
    return obj
}

fun getPhotonJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("ar", "1:1")
    obj.addProperty("size", 768)
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("guidance_scale", 6.0)
    obj.addProperty("steps", 20)
    obj.addProperty("negative_prompt", "out of frame, lowres, text, error, cropped, worst quality, low quality, jpeg artifacts, ugly, duplicate, morbid, mutilated, out of frame, extra fingers, mutated hands, poorly drawn hands, poorly drawn face, mutation, deformed, blurry, dehydrated, bad anatomy, bad proportions, extra limbs, cloned face, disfigured, gross proportions, malformed limbs, missing arms, missing legs, extra arms, extra legs, fused fingers, too many fingers, long neck, username, watermark, signature,")
    obj.add("embeds", JsonArray(0))
    return obj
}

val sdHiddenParameters = arrayOf("embeds", "model")
