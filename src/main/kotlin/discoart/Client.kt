package discoart

//import commands.make.DiffusionConfig
import alphanumericCharPool
import com.google.protobuf.*
import com.google.protobuf.Struct.Builder
import commands.make.CreateArtParameters
import commands.make.DiffusionConfig
import io.grpc.ManagedChannel
import jina.Jina
import jina.JinaRPCGrpcKt
import jina.dataRequestProto
import jina.headerProto
import kotlinx.coroutines.flow.asFlow
import randomString
import utils.camelToSnakeCase
import java.io.Closeable
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.memberProperties

private fun reqToByteArrayList(req: Jina.DataRequestProto): List<ByteArray> {
    var returnedImages = mutableListOf<ByteArray>()
    if(req.data.docs.docsCount > 0) {
        for (doc in req.data.docs.docsList) {
            returnedImages.add(Base64.getDecoder().decode(doc.uri.substring("data:image/png;base64,".length)))
        }
    }
    return returnedImages
}

data class RetrieveArtResult(
    val images: List<ByteArray>,
    val completed: Boolean,
    val statusPercent: Double
)

class Client (
    private val channel: ManagedChannel
) : Closeable {
    private val stub: JinaRPCGrpcKt.JinaRPCCoroutineStub = JinaRPCGrpcKt.JinaRPCCoroutineStub(channel)!!

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
                if(fieldsMap["_status"] != null) {
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

    private fun addDiffusionConfig(config: DiffusionConfig, builder: Builder) {
        // Block our own settings
        val denyList = listOf("baseSize")
        for (p in config::class.memberProperties) {
            if (p.name in denyList) {
                continue
            }
            val discoArtName = p.name!!.camelToSnakeCase()
            val value = p.getter.call(config)
            if(value is List<*>) {
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
            }
            else {
                val convertedValue = convertAnyToValue(value!!)
                if (convertedValue != null) {
                    builder.putFields(discoArtName, convertAnyToValue(value!!))
                }
            }
        }
    }

    private fun addDefaultCreateParameters(params: CreateArtParameters, builder: Struct.Builder) {
        builder.putFields("text_prompts", value {
            listValue = listValue {
                for (prompt in params.prompts) {
                    values.add(value {
                        stringValue = prompt
                    })
                }
            }
        })
        if(params.initImage != null) {
            builder.putFields("init_image", value { stringValue = params.initImage!! })
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
                    numberValue = params.symmetryIntensity
                })
            }
        })
        if(params.verticalSymmetry) {
            builder.putFields("use_vertical_symmetry", value { boolValue = true })
        }
        if(params.horizontalSymmetry) {
            builder.putFields("use_horizontal_symmetry", value { boolValue = true })
        }
        if(params.verticalSymmetry || params.horizontalSymmetry) {
            // There is no symmetry (yet) for PLMS
            builder.putFields("diffusion_sampling_mode", value { stringValue = "ddim" })
        }
        else {
            // There is no symmetry (yet) for PLMS
            builder.putFields("diffusion_sampling_mode", value { stringValue = "plms" })
        }
        builder.putFields("width_height", value {
            listValue = listValue {
                val (w, h) = params.ratio.calculateSize(params.preset.baseSize)
                values.addAll(listOf(
                    value { numberValue = w.toDouble() },
                    value { numberValue = h.toDouble() }
                ))
            }
        })
    }

    suspend fun variateArt(params: CreateArtParameters) {
        val builder = com.google.protobuf.Struct.newBuilder()
        addDiffusionConfig(params.preset, builder)
        addDefaultCreateParameters(params, builder)
        builder.putFields("init_image", value {
            stringValue = params.initImage.toString()
        })
        builder.putFields("skip_steps", value {
            numberValue = 75.0
        })
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

    suspend fun upscaleArt(params: CreateArtParameters) {
        val builder = com.google.protobuf.Struct.newBuilder()
        addDiffusionConfig(params.preset, builder)
        addDefaultCreateParameters(params, builder)
        builder.putFields("n_batches", value { numberValue = 1.0 })
        builder.putFields("batch_size", value { numberValue = 1.0 })
        builder.putFields("skip_steps", value {
            numberValue = 30.0
        })
        builder.putFields("width_height", value {
            listValue = listValue {
                val (w, h) = params.ratio.calculateSize(1024)
                values.addAll(listOf(
                    value { numberValue = w.toDouble() },
                    value { numberValue = h.toDouble() }
                ))
            }
        })
        val dataReq = dataRequestProto {
            parameters = builder.build()
            header = headerProto {
                execEndpoint = "/create"
                requestId = randomString(alphanumericCharPool, 32)
            }
        }
        val reqs = listOf(dataReq).asFlow()
        var result: List<ByteArray>? = null
        stub.withCompression("gzip").call(reqs).collect {
        }
    }

    suspend fun createArt(params: CreateArtParameters) {
        val builder = com.google.protobuf.Struct.newBuilder()
        addDiffusionConfig(params.preset, builder)
        addDefaultCreateParameters(params, builder)

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