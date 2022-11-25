package discoart

//import commands.make.DiffusionConfig
import alphanumericCharPool
import com.google.protobuf.*
import com.google.protobuf.Struct.Builder
import commands.make.DiffusionParameters
import commands.make.DiscoDiffusionConfig
import config
import docarray.documentArrayProto
import docarray.documentProto
import io.grpc.ManagedChannel
import jina.*
import kotlinx.coroutines.flow.asFlow
import randomString
import utils.camelToSnakeCase
import java.io.Closeable
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.memberProperties

private fun reqToByteArrayList(req: Jina.DataRequestProto): List<ByteArray> {
    var returnedImages = mutableListOf<ByteArray>()
    if (req.data.docs.docsCount > 0) {
        for (doc in req.data.docs.docsList) {
            val filteredUrl = doc.uri.substring(doc.uri.indexOf(",") + 1)
            returnedImages.add(Base64.getDecoder().decode(filteredUrl))
        }
    }
    return returnedImages
}

data class RetrieveArtResult(
    val images: List<ByteArray>,
    val completed: Boolean,
    val statusPercent: Double
)

class Client(
    private val channel: ManagedChannel
) : Closeable {
    private val stub: JinaRPCGrpcKt.JinaRPCCoroutineStub = JinaRPCGrpcKt.JinaRPCCoroutineStub(channel)

    fun isOnline(): Boolean {
        return !(channel.isShutdown || channel.isTerminated)
    }

    suspend fun retrieveUpscaleArt(artID: String): RetrieveArtResult {
        val dataReq = dataRequestProto {
            data = DataRequestProtoKt.dataContentProto {
                this.docs = documentArrayProto {
                    this.docs.add(documentProto {
                        this.id = artID
                    })
                }
            }
            header = headerProto {
                execEndpoint = "/upscale_progress"
                requestId = randomString(alphanumericCharPool, 32)
            }
        }
        val reqs = listOf(dataReq).asFlow()
        var result: List<ByteArray>? = null
        var completed = false
        var progress = 0.0
        stub.withCompression("gzip").call(reqs).collect {
            result = reqToByteArrayList(it)
            if (it.data.docs.docsCount > 0) {
                val doc = it.data.docs.getDocs(0)
                val fieldsMap = doc.tags.fieldsMap
                if (fieldsMap["progress"] != null) {
                    progress = fieldsMap["progress"]!!.numberValue.toDouble()
                    completed = progress == 1.0
                }
            }
        }
        return RetrieveArtResult(
            images = result!!,
            statusPercent = progress,
            completed = completed
        )
    }

    suspend fun retrieveArt(artID: String): RetrieveArtResult {
        val dataReq = dataRequestProto {
            parameters = struct {
                fields.put("name_docarray", value { stringValue = artID })
            }
            header = headerProto {
                execEndpoint = "/result"
                requestId = randomString(alphanumericCharPool, 32)
            }
        }
        val reqs = listOf(dataReq).asFlow()
        var result: List<ByteArray>? = null
        var completed = false
        var curT = 0
        stub.withCompression("gzip").call(reqs).collect {
            result = reqToByteArrayList(it)
            if (it.data.docs.docsCount > 0) {
                val doc = it.data.docs.getDocs(0)
                val fieldsMap = doc.tags.fieldsMap
                if (fieldsMap["_status"] != null) {
                    val statusFieldMap = fieldsMap["_status"]!!.structValue.fieldsMap
                    if (statusFieldMap["completed"] != null) {
                        completed = statusFieldMap["completed"]!!.boolValue
                    }
                    if (statusFieldMap["cur_t"] != null) {
                        curT = statusFieldMap["cur_t"]!!.numberValue.toInt()
                    }
                }
            }
        }
        return RetrieveArtResult(
            images = result!!,
            statusPercent = 1 - (curT / 150.0), // For now all our presets use 150 steps, so this is fine
            completed = completed
        )
    }

    private fun convertAnyToValue(value: Any): Value? {
        return when (value) {
            is Int -> {
                value {
                    numberValue = value.toDouble()
                }
            }

            is Double -> {
                value {
                    numberValue = value.toDouble()
                }
            }

            is Boolean -> {
                value {
                    boolValue = value
                }
            }

            is String -> {
                value {
                    stringValue = value
                }
            }

            else -> {
                return null
            }
        }
    }

    private fun addDiscoDiffusionConfig(config: DiscoDiffusionConfig, builder: Builder) {
        // Block our own settings
        val denyList = listOf("baseSize")
        for (p in config::class.memberProperties) {
            if (p.name in denyList) {
                continue
            }
            val discoArtName = p.name.camelToSnakeCase()
            val value = p.getter.call(config)
            if (value is List<*>) {
                builder.putFields(discoArtName, value {
                    listValue = listValue {
                        for (v in value) {
                            val convertedValue = convertAnyToValue(v!!)
                            if (convertedValue != null) {
                                this.values.add(convertedValue)
                            }
                        }
                    }
                })
            } else {
                val convertedValue = convertAnyToValue(value!!)
                if (convertedValue != null) {
                    builder.putFields(discoArtName, convertAnyToValue(value))
                }
            }
        }
    }

    private fun addDefaultStableDiffusionParameters(params: DiffusionParameters, builder: Builder) {
        val stableParams = params.stableDiffusionParameters!!
        builder.putFields("hf_auth_token", value {
            stringValue = config.bot.hfToken
        })
        builder.putFields("steps", value { numberValue = stableParams.steps.toDouble() })
        builder.putFields("seed", value { stringValue = params.seed.toString() })
        builder.putFields("size", value {
            listValue = listValue {
                val (w, h) = stableParams.ratio.calculateSize(params.stableDiffusionParameters.size)
                values.addAll(listOf(
                    value { numberValue = w.toDouble() },
                    value { numberValue = h.toDouble() }
                ))
            }
        })
        if (stableParams.strength != null) {
            builder.putFields("strength", value { numberValue = stableParams.strength!! / 100.0 })
        }
        builder.putFields("guidance_scale", value { numberValue = stableParams.guidanceScale })
    }

    private fun addDefaultDiscoDiffusionParameters(params: DiffusionParameters, builder: Builder) {
        val discoParams = params.discoDiffusionParameters!!
        builder.putFields("text_prompts", value {
            listValue = listValue {
                for (prompt in discoParams.prompts) {
                    values.add(value {
                        stringValue = prompt
                    })
                }
            }
        })
        if (discoParams.initImage != null) {
            builder.putFields("init_image", value { stringValue = discoParams.initImage!! })
            builder.putFields("skip_steps", value {
                numberValue = if (discoParams.skipSteps == 0) {
                    50
                } else {
                    discoParams.skipSteps
                }.toDouble()
            })
        }
        builder.putFields("name_docarray", value { stringValue = params.artID })
        builder.putFields("n_batches", value { numberValue = 1.0 })
        builder.putFields("seed", value { numberValue = params.seed.toDouble() })
        builder.putFields("display_rate", value { numberValue = 15.0 })
        builder.putFields("steps", value { numberValue = 150.0 })
        builder.putFields("truncate_overlength_prompt", value { boolValue = true })
        builder.putFields("transformation_percent", value {
            listValue = listValue {
                this.values.add(value {
                    numberValue = discoParams.symmetryIntensity
                })
            }
        })
        if (discoParams.verticalSymmetry) {
            builder.putFields("use_vertical_symmetry", value { boolValue = true })
        }
        if (discoParams.horizontalSymmetry) {
            builder.putFields("use_horizontal_symmetry", value { boolValue = true })
        }
        if (discoParams.verticalSymmetry || discoParams.horizontalSymmetry) {
            // There is no symmetry (yet) for PLMS
            builder.putFields("diffusion_sampling_mode", value { stringValue = "ddim" })
        } else {
            // There is no symmetry (yet) for PLMS
            builder.putFields("diffusion_sampling_mode", value { stringValue = "plms" })
        }
        builder.putFields("width_height", value {
            listValue = listValue {
                val (w, h) = discoParams.ratio.calculateSize(discoParams.preset.baseSize)
                values.addAll(listOf(
                    value { numberValue = w.toDouble() },
                    value { numberValue = h.toDouble() }
                ))
            }
        })
    }

    suspend fun variateArt(params: DiffusionParameters) {
    }

    suspend fun upscaleArt(params: DiffusionParameters) {
    }

    suspend fun createStableDiffusionArt(params: DiffusionParameters): List<ByteArray> {
        val builder = Struct.newBuilder()
        addDefaultStableDiffusionParameters(params, builder)
        val dataReq = if (params.stableDiffusionParameters!!.initImage == null) {
            dataRequestProto {
                parameters = builder.build()
                header = headerProto {
                    execEndpoint = "/stable_diffusion/txt2img"
                    requestId = randomString(alphanumericCharPool, 32)
                }
                data = DataRequestProtoKt.dataContentProto {
                    this.docs = documentArrayProto {
                        this.docs.add(documentProto {
                            text = params.stableDiffusionParameters!!.prompt
                        })
                    }
                }
            }
        } else {
            dataRequestProto {
                parameters = builder.build()
                header = headerProto {
                    execEndpoint = "/stable_diffusion/img2img"
                    requestId = randomString(alphanumericCharPool, 32)
                }
                data = DataRequestProtoKt.dataContentProto {
                    this.docs = documentArrayProto {
                        this.docs.add(documentProto {
                            text = params.stableDiffusionParameters!!.prompt
                            uri = params.stableDiffusionParameters!!.initImage!!.toString()
                        })
                    }
                }
            }
        }

        val reqs = listOf(dataReq).asFlow()
        var result: List<ByteArray>? = null
        stub.withCompression("gzip").call(reqs).collect {
            val status = it.header.status
            if (status.code == Jina.StatusProto.StatusCode.ERROR) {
                throw IllegalStateException("Error found in gateway response!\n${status.exception}")
            }
            result = reqToByteArrayList(it)
        }
        return result!!
    }

    suspend fun magicPrompt(startPrompt: String, amount: Int, variation: Double): Array<String> {
        val builder = Struct.newBuilder()
        builder.putFields("amount", value {
            numberValue = amount.toDouble()
        })
        builder.putFields("variation", value {
            numberValue = variation
        })
        val dataReq = dataRequestProto {
            parameters = builder.build()
            header = headerProto {
                execEndpoint = "/magic_prompt/stable_diffusion"
                requestId = randomString(alphanumericCharPool, 32)
            }
            data = DataRequestProtoKt.dataContentProto {
                this.docs = documentArrayProto {
                    this.docs.add(documentProto {
                        text = startPrompt
                    })
                }
            }
        }
        val reqs = listOf(dataReq).asFlow()
        val result = mutableListOf<String>()
        stub.withCompression("gzip").call(reqs).collect {
            val status = it.header.status
            if (status.code == Jina.StatusProto.StatusCode.ERROR) {
                throw IllegalStateException("Error found in gateway response!\n${status.exception}")
            }
            if (it.data.docs.docsCount > 0) {
                for(doc in it.data.docs.docsList) {
                    result.add(doc.text)
                }
            }
        }
        return result.toTypedArray()
    }

    suspend fun createDiscoDiffusionArt(params: DiffusionParameters) {
        val builder = Struct.newBuilder()
        addDiscoDiffusionConfig(params.discoDiffusionParameters!!.preset, builder)
        addDefaultDiscoDiffusionParameters(params, builder)

        val dataReq = dataRequestProto {
            parameters = builder.build()
            header = headerProto {
                execEndpoint = "/create"
                requestId = randomString(alphanumericCharPool, 32)
            }
        }
        val reqs = listOf(dataReq).asFlow()
        stub.withCompression("gzip").call(reqs).collect {
        }
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}