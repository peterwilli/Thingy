package discoart

import alphanumericCharPool
import com.google.protobuf.*
import com.google.protobuf.Struct.Builder
import com.google.protobuf.kotlin.DslMap
import commands.make.CreateArtParameters
import commands.make.DiffusionConfig
//import commands.make.DiffusionConfig
import io.grpc.ManagedChannel
import jina.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import randomString
import utils.camelToSnakeCase
import java.io.Closeable
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.memberProperties

private suspend fun reqsToByteArrayList(ongoingRequest: Flow<Jina.DataRequestProto>): List<ByteArray> {
    var returnedImages = mutableListOf<ByteArray>()
    ongoingRequest.collect {
        if(it.data.docs.docsCount > 0) {
            for (doc in it.data.docs.docsList) {
                returnedImages.add(Base64.getDecoder().decode(doc.uri.substring("data:image/png;base64,".length)))
            }
        }
    }
    return returnedImages
}

class Client (
    private val channel: ManagedChannel
) : Closeable {
    private val stub: JinaRPCGrpcKt.JinaRPCCoroutineStub = JinaRPCGrpcKt.JinaRPCCoroutineStub(channel)!!

    suspend fun retrieveArt(artID: String): List<ByteArray> {
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
        return reqsToByteArrayList(stub.withCompression("gzip").call(reqs))
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
        builder.putFields("diffusion_sampling_mode", value { stringValue = "plms" })
        builder.putFields("clip_models",  value {
            listValue = listValue {
                values.add(value { stringValue = "ViT-B-32::laion2b_e16" })
            }
        })
        builder.putFields("n_batches", value { numberValue = 1.0 })
        builder.putFields("seed", value { numberValue = params.seed.toDouble() })
        builder.putFields("display_rate", value { numberValue = 15.0 })
        builder.putFields("steps", value { numberValue = 150.0 })
        builder.putFields("width_height", value {
            listValue = listValue {
                val (w, h) = params.ratio.calculateSize(256)
                values.addAll(listOf(
                    value { numberValue = w.toDouble() },
                    value { numberValue = h.toDouble() }
                ))
            }
        })
    }

    suspend fun variateArt(params: CreateArtParameters, imageIndex: Int): List<ByteArray> {
        val builder = com.google.protobuf.Struct.newBuilder()
        addDefaultCreateParameters(params, builder)
        builder.putFields("init_image", value {
            stringValue = "/app/${params.artID.substring(0..params.artID.length - 4)}/${imageIndex}-done.png"
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
        return reqsToByteArrayList(stub.withCompression("gzip").call(reqs))
    }

    suspend fun upscaleArt(params: CreateArtParameters, imageIndex: Int): ByteArray {
        val builder = com.google.protobuf.Struct.newBuilder()
        addDefaultCreateParameters(params, builder)
        builder.putFields("n_batches", value { numberValue = 1.0 })
        builder.putFields("batch_size", value { numberValue = 1.0 })
        builder.putFields("init_image", value {
            stringValue = "/app/${params.artID.substring(0..params.artID.length - 4)}/${imageIndex}-done.png"
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
        return reqsToByteArrayList(stub.withCompression("gzip").call(reqs)).first()
    }
    
    fun injectDiffusionConfig(config: DiffusionConfig) {
        for (p in config::class.memberProperties) {
            val discoArtName = p.name!!.camelToSnakeCase()
            println(discoArtName)
        }
    }

    suspend fun createArt(params: CreateArtParameters): List<ByteArray> {
        val builder = com.google.protobuf.Struct.newBuilder()
        addDefaultCreateParameters(params, builder)
        builder.putFields("skip_augs", value { boolValue = true })
        builder.putFields("cut_overview", value { stringValue = "[4]*1000" })
        builder.putFields("cutn_batches", value { numberValue = 1.0 })
        builder.putFields("tv_scale", value { numberValue = 10.0 })
        builder.putFields("range_scale", value { numberValue = 200.0 })
        //builder.putFields("sat_scale", value { numberValue = 1000.0 })
//        builder.putFields("cut_innercut", value { stringValue = "[0]*1000" })

        val dataReq = dataRequestProto {
            parameters = builder.build()
            header = headerProto {
                execEndpoint = "/create"
                requestId = randomString(alphanumericCharPool, 32)
            }
        }

        val reqs = listOf(dataReq).asFlow()
        return reqsToByteArrayList(stub.withCompression("gzip").call(reqs))
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}