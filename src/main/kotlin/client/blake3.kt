import java.lang.IllegalStateException
import kotlin.Throws
import java.io.IOException
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.jvm.JvmOverloads

/**
 *
 * MahdiSml.ir
 * Mahdi Safarmohammadloo
 *
 * Special Thanks to rctcwyvrn !
 * He Translated Blake3 reference implementation from Rust to Java
 * AND
 * i optimized and translated it to Kotlin
 *
 */
class SMLBLAKE3 {
    // Node of the Blake3 hash tree
    // Is either chained into the next node using chainingValue()
    // Or used to calculate the hash digest using rootOutputBytes()
    private class Node(
        var inputChainingValue: IntArray,
        var blockWords: IntArray,
        var counter: Long,
        var blockLen: Int,
        var flags: Int
    ) {
        // Return the 8 int CV
        fun chainingValue(): IntArray {
            return compress(inputChainingValue, blockWords, counter, blockLen, flags).copyOfRange(0, 8)
        }

        fun rootOutputBytes(outLen: Int): ByteArray {
            var outputCounter = 0
            val outputsNeeded = Math.floorDiv(outLen, 2 * OUT_LEN) + 1
            val hash = ByteArray(outLen)
            var i = 0
            while (outputCounter < outputsNeeded) {
                val words = compress(inputChainingValue, blockWords, outputCounter.toLong(), blockLen, flags or ROOT)
                for (word in words) {
                    for (b in ByteBuffer.allocate(4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(word)
                        .array()) {
                        hash[i] = b
                        i += 1
                        if (i == outLen) {
                            return hash
                        }
                    }
                }
                outputCounter += 1
            }
            throw IllegalStateException("Uh oh something has gone horribly wrong.")
        }
    }

    // Helper object for creating new Nodes and chaining them
    private class ChunkState(var chainingValue: IntArray, var chunkCounter: Long, var flags: Int) {
        var block = ByteArray(BLOCK_LEN)
        var blockLen: Int = 0
        var blocksCompressed: Int = 0
        fun len(): Int {
            return BLOCK_LEN * blocksCompressed + blockLen
        }

        private fun startFlag(): Int {
            return if (blocksCompressed == 0) CHUNK_START else 0
        }

        fun update(input: ByteArray) {
            var currPos = 0
            while (currPos < input.size) {

                // Chain the next 64 byte block into this chunk/node
                if (blockLen == BLOCK_LEN) {
                    val blockWords = wordsFromLEBytes(block)
                    chainingValue = compress(
                        chainingValue,
                        blockWords,
                        chunkCounter,
                        BLOCK_LEN,
                        flags or startFlag()
                    ).copyOfRange(0, 8)
                    blocksCompressed += 1
                    block = ByteArray(BLOCK_LEN)
                    blockLen = 0
                }

                // Take bytes out of the input and update
                val want = BLOCK_LEN - blockLen // How many bytes we need to fill up the current block
                val canTake = want.coerceAtMost(input.size - currPos)
                System.arraycopy(input, currPos, block, blockLen, canTake)
                blockLen += canTake
                currPos += canTake
            }
        }

        fun createNode(): Node {
            return Node(
                chainingValue,
                wordsFromLEBytes(block),
                chunkCounter,
                blockLen.toInt(),
                flags or startFlag() or CHUNK_END
            )
        }
    }

    // Hasher
    private var chunkState: ChunkState? = null
    private lateinit var key: IntArray
    private val cvStack = arrayOfNulls<IntArray>(54)
    private var cvStackLen: Int = 0
    private var flags = 0

    private constructor() {
        initialize(IV, 0)
    }

    private constructor(key: ByteArray) {
        initialize(wordsFromLEBytes(key), KEYED_HASH)
    }

    private constructor(context: String) {
        val contextHasher = SMLBLAKE3()
        contextHasher.initialize(IV, DERIVE_KEY_CONTEXT)
        contextHasher.update(context.toByteArray(StandardCharsets.UTF_8))
        val contextKey = wordsFromLEBytes(contextHasher.digest())
        initialize(contextKey, DERIVE_KEY_MATERIAL)
    }

    private fun initialize(key: IntArray, flags: Int) {
        chunkState = ChunkState(key, 0, flags)
        this.key = key
        this.flags = flags
    }

    /**
     * Append the byte contents of the file to the hash tree
     * @param file File to be added
     * @throws IOException If the file does not exist
     */
    @Throws(IOException::class)
    fun update(file: File) {
        // Update the hasher 4kb at a time to avoid memory issues when hashing large files
        FileInputStream(file).use { ios ->
            val buffer = ByteArray(4096)
            var read = 0
            while (ios.read(buffer).also { read = it } != -1) {
                if (read == buffer.size) {
                    update(buffer)
                } else {
                    update(buffer.copyOfRange(0, read))
                }
            }
        }
    }

    /**
     * Appends new data to the hash tree
     * @param input Data to be added
     */
    fun update(input: ByteArray) {
        var currPos = 0
        while (currPos < input.size) {

            // If this chunk has chained in 16 64 bytes of input, add its CV to the stack
            if (chunkState!!.len() == CHUNK_LEN) {
                val chunkCV = chunkState!!.createNode().chainingValue()
                val totalChunks = chunkState!!.chunkCounter + 1
                addChunkChainingValue(chunkCV, totalChunks)
                chunkState = ChunkState(key, totalChunks, flags)
            }
            val want = CHUNK_LEN - chunkState!!.len()
            val take = want.coerceAtMost(input.size - currPos)
            chunkState!!.update(input.copyOfRange(currPos, currPos + take))
            currPos += take
        }
    }
    /**
     * Generate the blake3 hash for the current tree with the given byte length
     * @param hashLen The number of bytes of hash to return
     * @return The byte array representing the hash
     */
    /**
     * Generate the blake3 hash for the current tree with the default byte length of 32
     * @return The byte array representing the hash
     */
    @JvmOverloads
    fun digest(hashLen: Int = DEFAULT_HASH_LEN): ByteArray {
        var node = chunkState!!.createNode()
        var parentNodesRemaining = cvStackLen.toInt()
        while (parentNodesRemaining > 0) {
            parentNodesRemaining -= 1
            node = parentNode(
                cvStack[parentNodesRemaining],
                node.chainingValue(),
                key,
                flags
            )
        }
        return node.rootOutputBytes(hashLen)
    }
    /**
     * Generate the blake3 hash for the current tree with the given byte length
     * @param hashLen The number of bytes of hash to return
     * @return The hex string representing the hash
     */
    /**
     * Generate the blake3 hash for the current tree with the default byte length of 32
     * @return The hex string representing the hash
     */
    @JvmOverloads
    fun hexdigest(hashLen: Int = DEFAULT_HASH_LEN): String {
        return bytesToHex(digest(hashLen))
    }

    private fun pushStack(cv: IntArray) {
        cvStack[cvStackLen] = cv
        cvStackLen += 1
    }

    private fun popStack(): IntArray? {
        cvStackLen -= 1
        return cvStack[cvStackLen]
    }

    private fun addChunkChainingValue(newCV: IntArray, totalChunks: Long) {
        var newCV = newCV
        var totalChunks = totalChunks
        while (totalChunks and 1 == 0L) {
            newCV = parentCV(popStack(), newCV, key, flags)
            totalChunks = totalChunks shr 1
        }
        pushStack(newCV)
    }

    companion object {
        private val HEX_ARRAY = "0123456789abcdef".toCharArray()
        private const val DEFAULT_HASH_LEN = 32
        private const val OUT_LEN = 32
        private const val KEY_LEN = 32
        private const val BLOCK_LEN = 64
        private const val CHUNK_LEN = 1024
        private const val CHUNK_START = 1
        private const val CHUNK_END = 2
        private const val PARENT = 4
        private const val ROOT = 8
        private const val KEYED_HASH = 16
        private const val DERIVE_KEY_CONTEXT = 32
        private const val DERIVE_KEY_MATERIAL = 64
        private val IV = intArrayOf(
            0x6A09E667, -0x4498517b, 0x3C6EF372, -0x5ab00ac6, 0x510E527F, -0x64fa9774, 0x1F83D9AB, 0x5BE0CD19
        )
        private val MSG_PERMUTATION = intArrayOf(
            2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8
        )

        private fun wrappingAdd(a: Int, b: Int): Int {
            return a + b
        }

        private fun rotateRight(x: Int, len: Int): Int {
            return x ushr len or (x shl 32 - len)
        }

        private fun g(state: IntArray, a: Int, b: Int, c: Int, d: Int, mx: Int, my: Int) {
            state[a] = wrappingAdd(wrappingAdd(state[a], state[b]), mx)
            state[d] = rotateRight(state[d] xor state[a], 16)
            state[c] = wrappingAdd(state[c], state[d])
            state[b] = rotateRight(state[b] xor state[c], 12)
            state[a] = wrappingAdd(wrappingAdd(state[a], state[b]), my)
            state[d] = rotateRight(state[d] xor state[a], 8)
            state[c] = wrappingAdd(state[c], state[d])
            state[b] = rotateRight(state[b] xor state[c], 7)
        }

        private fun roundFn(state: IntArray, m: IntArray) {
            // Mix columns
            g(state, 0, 4, 8, 12, m[0], m[1])
            g(state, 1, 5, 9, 13, m[2], m[3])
            g(state, 2, 6, 10, 14, m[4], m[5])
            g(state, 3, 7, 11, 15, m[6], m[7])

            // Mix diagonals
            g(state, 0, 5, 10, 15, m[8], m[9])
            g(state, 1, 6, 11, 12, m[10], m[11])
            g(state, 2, 7, 8, 13, m[12], m[13])
            g(state, 3, 4, 9, 14, m[14], m[15])
        }

        private fun permute(m: IntArray): IntArray {
            val permuted = IntArray(16)
            for (i in 0..15) {
                permuted[i] = m[MSG_PERMUTATION[i]]
            }
            return permuted
        }

        private fun compress(
            chainingValue: IntArray,
            blockWords: IntArray,
            counter: Long,
            blockLen: Int,
            flags: Int
        ): IntArray {
            var blockWords = blockWords
            val counterInt = (counter and 0xffffffffL).toInt()
            val counterShift = (counter shr 32 and 0xffffffffL).toInt()
            val state = intArrayOf(
                chainingValue[0],
                chainingValue[1],
                chainingValue[2],
                chainingValue[3],
                chainingValue[4],
                chainingValue[5],
                chainingValue[6],
                chainingValue[7],
                IV[0],
                IV[1],
                IV[2],
                IV[3],
                counterInt,
                counterShift,
                blockLen,
                flags
            )
            roundFn(state, blockWords) // Round 1
            blockWords = permute(blockWords)
            roundFn(state, blockWords) // Round 2
            blockWords = permute(blockWords)
            roundFn(state, blockWords) // Round 3
            blockWords = permute(blockWords)
            roundFn(state, blockWords) // Round 4
            blockWords = permute(blockWords)
            roundFn(state, blockWords) // Round 5
            blockWords = permute(blockWords)
            roundFn(state, blockWords) // Round 6
            blockWords = permute(blockWords)
            roundFn(state, blockWords) // Round 7
            for (i in 0..7) {
                state[i] = state[i] xor state[i + 8]
                state[i + 8] = state[i + 8] xor chainingValue[i]
            }
            return state
        }

        private fun wordsFromLEBytes(bytes: ByteArray): IntArray {
            val words = IntArray(bytes.size / 4)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in words.indices) {
                words[i] = buf.int
            }
            return words
        }

        // Combines the chaining values of two children to create the parent node
        private fun parentNode(leftChildCV: IntArray?, rightChildCV: IntArray, key: IntArray, flags: Int): Node {
            val blockWords = IntArray(16)
            var i = 0
            for (x in leftChildCV!!) {
                blockWords[i] = x
                i += 1
            }
            for (x in rightChildCV) {
                blockWords[i] = x
                i += 1
            }
            return Node(key, blockWords, 0, BLOCK_LEN, PARENT or flags)
        }

        private fun parentCV(leftChildCV: IntArray?, rightChildCV: IntArray, key: IntArray, flags: Int): IntArray {
            return parentNode(leftChildCV, rightChildCV, key, flags).chainingValue()
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            for (j in bytes.indices) {
                val v: Int = bytes[j].toInt() and 0xFF
                hexChars[j * 2] = HEX_ARRAY[v ushr 4]
                hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
            }
            return String(hexChars)
        }

        /**
         * Construct a BLAKE3 blake3 hasher
         */
        fun newInstance(): SMLBLAKE3 {
            return SMLBLAKE3()
        }

        /**
         * Construct a new BLAKE3 keyed mode hasher
         * @param key The 32 byte key
         * @throws IllegalStateException If the key is not 32 bytes
         */
        fun newKeyedHasher(key: ByteArray): SMLBLAKE3 {
            check(key.size == KEY_LEN) { "Invalid key length" }
            return SMLBLAKE3(key)
        }

        /**
         * Construct a new BLAKE3 key derivation mode hasher
         * The context string should be hardcoded, globally unique, and application-specific. <br></br><br></br>
         * A good default format is *"[application] [commit timestamp] [purpose]"*, <br></br>
         * eg "example.com 2019-12-25 16:18:03 session tokens v1"
         * @param context Context string used to derive keys.
         */
        fun newKeyDerivationHasher(context: String): SMLBLAKE3 {
            return SMLBLAKE3(context)
        }
    }
}
