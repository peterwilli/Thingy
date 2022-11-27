package utils

import docarray.Docarray.DocumentProto
import java.util.*

fun DocumentProto.base64UriToByteArray(): ByteArray {
    val filteredUrl = this.uri.substring(this.uri.indexOf(",") + 1)
    return Base64.getDecoder().decode(filteredUrl)
}