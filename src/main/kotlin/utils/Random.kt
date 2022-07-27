import java.nio.ByteBuffer

val alphanumericCharPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

private const val HEXES = "0123456789ABCDEF"

fun toHexString(bb: ByteBuffer): String? {
    val buffer = bb.duplicate()
    val hex = StringBuilder()
    while (buffer.hasRemaining()) {
        val b = buffer.get()
        hex.append(HEXES[b.toInt() and 0xF0 shr 4]).append(HEXES[b.toInt() and 0x0F])
    } /*  ww  w  . ja va2s  .c om*/
    return hex.toString()
}

fun randomString(charPool: List<Char>, size: Int): String {
    return (1..size)
        .map { i -> kotlin.random.Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
}