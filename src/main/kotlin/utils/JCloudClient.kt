package utils

import commands.make.DiscoDiffusionParameters
import commands.make.StableDiffusionParameters
import config
import discoart.Client
import gson
import io.grpc.ManagedChannelBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URL

data class URLResponse(
    val endpoint: String
)

class JCloudClient {
    private var client: Client? = null

    fun currentClient(): Client {
        if (client == null || !client!!.isOnline()) {
            client = newClient()
        }
        if (client == null) {
            throw IllegalAccessException("client is null!")
        }
        return client!!
    }

    private fun getCurrentURL(): URI? {
        val connection = config.jcloudKeeper.url.openConnection()
        BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
            val response = gson.fromJson(inp.readText(), URLResponse::class.java)
            return URI(response.endpoint)
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