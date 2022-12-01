package utils

import Client
import config
import gson
import io.grpc.ManagedChannelBuilder
import org.apache.commons.lang3.exception.ExceptionUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI

data class URLResponse(
    val endpoint: String
)

class JCloudClient {
    private var client: Client? = null

    fun currentClient(): Client {
        if (client == null) {
            client = newClient()
        }
        if (!client!!.isOnline()) {
            println("Client is offline, making a new one!")
            client = newClient()
        }
        if (client == null) {
            throw IllegalAccessException("client is null!")
        }
        return client!!
    }

    fun freeClient() {
        if (client != null) {
            try {
                client!!.close()
            } catch (e: Exception) {
                println(
                    "Exception in freeClient (Ignored):\n${ExceptionUtils.getMessage(e)}\n" +
                            "${ExceptionUtils.getStackTrace(e)}"
                )
            }
            client = null
        }
    }

    private fun getCurrentURL(): URI? {
        if (config.jcloudKeeper != null) {
            val connection = config.jcloudKeeper!!.url.openConnection()
            BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
                val response = gson.fromJson(inp.readText(), URLResponse::class.java)
                return URI(response.endpoint)
            }
        } else if (config.directUrl != null) {
            return config.directUrl
        }
        return null
    }

    private fun newClient(): Client? {
        val url = getCurrentURL()
        if (url != null) {
            val channelBuilder = ManagedChannelBuilder.forAddress(url.host, url.port)
                .maxInboundMessageSize(1024 * 1024 * 1024)
            if (url.scheme == "grpc") {
                channelBuilder.usePlaintext()
            }
            return Client(channelBuilder.build())
        }
        return null
    }
}