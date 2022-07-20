package commands.make

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.apache.commons.text.CaseUtils
import utils.snakeToLowerCamelCase
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Math.round
import kotlin.text.StringBuilder

data class DiffusionConfig(
    val cutIcPow: Double,
    val skipAugs: Boolean,
    val fuzzyPrompt: Boolean,
    val randomizeClass: Boolean,
    val clipGuidanceScale: Int,
    val widthHeight: List<Int>,
    val clipDenoised: Boolean,
    val perlinMode: String,
    val seed: Int,
    val cutInnercut: Int,
    val steps: Int,
    val cutIcgrayP: String,
    val eta: Double,
    val initScale: Int,
    val diffusionSamplingMode: String,
    val rangeScale: Int,
    val useSecondaryModel: Boolean,
    val perlinInit: Boolean,
    val randMag: Double,
    val cutnBatches: Int,
    val tvScale: Double,
    val nBatches: Int,
    val clipSequentialEvaluate: Boolean,
    val textPrompts: List<String>,
    val displayRate: Int,
    val cutOverview: Int,
    val clipModels: List<String>,
    val skipSteps: Int,
    val diffusionModel: String,
    val batchSize: Int,
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
                if(idx < obj.size - 1) {
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
            val objArr = obj as JsonArray<*>
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
    val denylist = listOf("seed", "textPrompts", "")
    for ((idx, k) in configJSON.keys.withIndex()) {
        val camelK = k.snakeToLowerCamelCase()
        initVars.append("val $camelK: ${getStringType(configJSON[k]!!)}")
        classVars.append("$camelK = ${formatJsonObj(configJSON[k]!!)}")
        if(idx < configJSON.keys.size - 1) {
            initVars.append(",\n")
            classVars.append(", ")
        }
    }
    println("init vars: $initVars")
    println("override: $classVars")
}