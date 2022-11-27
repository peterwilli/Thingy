package commands.make

import com.google.gson.JsonArray
import kotlin.math.min

fun getScriptForSize(size: Int): String {
    if (size >= 768) {
        return "stable_diffusion_768"
    }
    return "stable_diffusion_512"
}