import com.google.gson.JsonObject
import com.google.protobuf.Struct
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.value
import docarray.Docarray.DocumentArrayProto
import docarray.documentArrayProto
import docarray.documentProto
import io.grpc.ManagedChannel
import jina.*
import kotlinx.coroutines.flow.asFlow
import java.io.Closeable
import java.util.*
import java.util.concurrent.TimeUnit

class Client(
    private val channel: ManagedChannel
) : Closeable {
    private val stub: JinaRPCGrpcKt.JinaRPCCoroutineStub = JinaRPCGrpcKt.JinaRPCCoroutineStub(channel)

    fun isOnline(): Boolean {
        return !(channel.isShutdown || channel.isTerminated)
    }

    suspend fun retrieveEntryStatus(queueId: String, index: Int): DocumentArrayProto {
        val builder = Struct.newBuilder()
        builder.putFields("index", value {
            numberValue = index.toDouble()
        })
        builder.putFields("queue_id", value {
            stringValue = queueId
        })
        val dataReq = dataRequestProto {
            parameters = builder.build()
            header = headerProto {
                execEndpoint = "/queue_entry_status"
                requestId = randomString(alphanumericCharPool, 32)
            }
        }
        val reqs = listOf(dataReq).asFlow()
        var result: DocumentArrayProto? = null
        stub.withCompression("gzip").call(reqs).collect {
            val status = it.header.status
            if (status.code == Jina.StatusProto.StatusCode.ERROR) {
                throw IllegalStateException("Error found in gateway response!\n${status.exception}")
            }
            result = it.data.docs
        }
        return result!!
    }

    suspend fun sendEntry(script: String, params: JsonObject): String {
        val builder = Struct.newBuilder()
        builder.putFields("script", value {
            stringValue = script
        })
        val dataReq = dataRequestProto {
            parameters = builder.build()
            header = headerProto {
                execEndpoint = "/run_queue_entry"
                requestId = randomString(alphanumericCharPool, 32)
            }
            data = DataRequestProtoKt.dataContentProto {
                this.docs = documentArrayProto {
                    val structBuilder = Struct.newBuilder()
                    JsonFormat.parser().ignoringUnknownFields().merge(params.toString(), structBuilder)
                    this.docs.add(documentProto {
                        tags = structBuilder.build()
                    })
                }
            }
        }
        val reqs = listOf(dataReq).asFlow()
        var result: String? = null
        stub.withCompression("gzip").call(reqs).collect {
            val status = it.header.status
            if (status.code == Jina.StatusProto.StatusCode.ERROR) {
                throw IllegalStateException("Error found in gateway response!\n${status.exception}")
            }
            result = it.data.docs.getDocs(0).text
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
                for (doc in it.data.docs.docsList) {
                    result.add(doc.text)
                }
            }
        }
        return result.toTypedArray()
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}