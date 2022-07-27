package commands.make

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import utils.snakeToLowerCamelCase
import java.io.BufferedReader
import java.io.InputStreamReader

data class DiffusionConfig(
    val baseSize: Int,
    val cutIcPow: Double,
    val skipAugs: Boolean,
    val fuzzyPrompt: Boolean,
    val randomizeClass: Boolean,
    val clipGuidanceScale: Int,
    val clipDenoised: Boolean,
    val perlinMode: String,
    val cutInnercut: String,
    val steps: Int,
    val cutIcgrayP: String,
    val eta: Double,
    val initScale: Int,
    val rangeScale: Int,
    val useSecondaryModel: Boolean,
    val perlinInit: Boolean,
    val randMag: Double,
    val cutnBatches: Int,
    val tvScale: Double,
    val cutOverview: String,
    val clipModels: List<String>,
    val skipSteps: Int,
    val diffusionModel: String,
    val transformationPercent: List<Double>,
    val clampGrad: Boolean,
    val onMisspelledToken: String,
    val clampMax: Double,
    val useHorizontalSymmetry: Boolean,
    val useVerticalSymmetry: Boolean,
    val satScale: Double
)

// Small utility converting kwargs from DiscoArt in Python to a DiffusionConfig
fun main() {
    fun formatJsonObj(obj: Any): String {
        if (obj is String) {
            return "\"${obj}\""
        }
        if (obj is Double) {
            // Rough estimation when an option can be an int
            if (obj.toString().endsWith(".0")) {
                return obj.toInt().toString()
            }
        }
        if (obj is JsonArray<*>) {
            val arrayInstance = StringBuilder("listOf(")
            for ((idx, item) in obj.withIndex()) {
                arrayInstance.append("${formatJsonObj(item!!)}")
                if (idx < obj.size - 1) {
                    arrayInstance.append(", ")
                }
            }
            arrayInstance.append(")")
            return arrayInstance.toString()
        }
        return obj.toString()
    }

    fun getStringType(obj: Any): String {
        if (obj is Double) {
            // Rough estimation when an option can be an int
            if (obj.toString().endsWith(".0")) {
                return "Int"
            }
        }
        if (obj is JsonArray<*>) {
            val objArr = obj
            if (objArr.isNotEmpty()) {
                return "List<${getStringType(objArr[0]!!)}>"
            }
        }
        return obj::class.simpleName!!
    }

    val reader = BufferedReader(
        InputStreamReader(System.`in`)
    )
    println("Paste kwargs (in JSON):")
    val configJSON = Parser.default().parse(StringBuilder(reader.readLine())) as JsonObject
    val classVars = StringBuilder()
    val initVars = StringBuilder()
    // These are properties not related to tuning the quality of the image
    val denylist = listOf("seed", "text_prompts", "width_height", "display_rate")
    val configJSONFiltered = configJSON.keys.filter { !denylist.contains(it) }
    for ((idx, k) in configJSONFiltered.withIndex()) {
        val camelK = k.snakeToLowerCamelCase()
        initVars.append("val $camelK: ${getStringType(configJSON[k]!!)}")
        classVars.append("$camelK = ${formatJsonObj(configJSON[k]!!)}")
        if (idx < configJSONFiltered.size - 1) {
            initVars.append(",\n")
            classVars.append(", ")
        }
    }
    println("init vars: $initVars")
    println("override: $classVars")
}