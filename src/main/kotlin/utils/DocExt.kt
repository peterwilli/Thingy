package utils

import alphanumericCharPool
import com.google.protobuf.Struct
import docarray.Docarray.DocumentProto
import docarray.documentProto
import randomString

fun DocumentProto.merge(other: DocumentProto): DocumentProto {
    val structBuilder = Struct.newBuilder()
    structBuilder.putAllFields(this.tags.fieldsMap)
    structBuilder.putAllFields(other.tags.fieldsMap)

    return documentProto {
        id = randomString(alphanumericCharPool, 32)
        tags = structBuilder.build()
        uri = other.uri
    }
}