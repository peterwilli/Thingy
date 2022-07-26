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

class Client (
    private val channel: ManagedChannel
) : Closeable {
    private val stub: JinaRPCGrpcKt.JinaRPCCoroutineStub = JinaRPCGrpcKt.JinaRPCCoroutineStub(channel)!!

    suspend fun retrieveArt(artID: String): Pair<List<ByteArray>, Boolean> {
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
                }
            }
        }
        return result!! to completed
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
        for (p in config::class.memberProperties) {
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
            builder.putFields("init_image", value { stringValue = params.initImage.toString() })
        }
        builder.putFields("name_docarray", value { stringValue = params.artID })
        builder.putFields("n_batches", value { numberValue = 1.0 })
        builder.putFields("seed", value { numberValue = params.seed.toDouble() })
        builder.putFields("display_rate", value { numberValue = 15.0 })
        builder.putFields("steps", value { numberValue = 150.0 })
        builder.putFields("truncate_overlength_prompt", value { boolValue = true })
        builder.putFields("width_height", value {
            listValue = listValue {
                val (w, h) = params.ratio.calculateSize(512)
                values.addAll(listOf(
                    value { numberValue = w.toDouble() },
                    value { numberValue = h.toDouble() }
                ))
            }
        })
    }

    suspend fun variateArt(params: CreateArtParameters, imageIndex: Int) {
        val builder = com.google.protobuf.Struct.newBuilder()
        addDefaultCreateParameters(params, builder)
        builder.putFields("init_image", value {
            stringValue = "/app/${params.artID.substring(0..params.artID.length - 4)}/${imageIndex}-done-0.png"
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

    suspend fun upscaleArt(params: CreateArtParameters, imageIndex: Int) {
        val builder = com.google.protobuf.Struct.newBuilder()
        addDefaultCreateParameters(params, builder)
        builder.putFields("n_batches", value { numberValue = 1.0 })
        builder.putFields("batch_size", value { numberValue = 1.0 })
        builder.putFields("init_image", value {
            stringValue = "/app/${params.artID.substring(0..params.artID.length - 4)}/0-done-0.png"
        })
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
        addDefaultCreateParameters(params, builder)
        addDiffusionConfig(params.preset, builder)

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