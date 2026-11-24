package com.filesecuritytool.android.crypto

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import java.util.LinkedHashMap
import java.util.UUID
import javax.crypto.BadPaddingException
import javax.crypto.SecretKey

/**
 * Port of OfflineCryptoService.java — FST2 file and FST-TEXT1 text encryption/decryption.
 *
 * FST2: AES-256-GCM chunked file encryption + RSA key wrap + HMAC-SHA256 nonce derivation.
 * FST-TEXT1: AES-256-GCM text encryption + CBOR serialization + Base64URL encoding.
 *
 * This class handles the container format, AES-GCM, and HMAC.
 * RSA operations are delegated to the provided [rsaEncryptor] and [rsaDecryptor] lambdas,
 * allowing the caller to use Android Keystore for hardware-backed RSA.
 */
class OfflineCryptoService(
    private val rsaEncryptor: (aesKey: SecretKey, receiverPublicKeyPem: String) -> String,
    private val rsaDecryptor: (encryptedKeyBase64: String) -> SecretKey
) {
    // ── FST2 constants ────────────────────────────────────────
    companion object {
        private val FST2_MAGIC = byteArrayOf('F'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), '2'.code.toByte())
        private const val FST2_VERSION: Byte = 1
        private const val ALG_KEY_WRAP_RSA: Byte = 1
        private const val ALG_AES_256_GCM: Byte = 1
        private const val ALG_NONCE_HMAC_SHA256: Byte = 1
        private const val MAX_TEXT_BYTES = 16 * 1024
        private const val MAX_TEXT_PAYLOAD_CHARS = 64 * 1024
        private const val MAX_HEADER_CIPHERTEXT_BYTES = 64 * 1024
        private const val MAX_LENGTH_PREFIXED_FIELD_BYTES = 1024 * 1024
        private const val MAX_CHUNK_SIZE = 1024 * 1024
        private const val FST_TEXT_PREFIX = "FST-TEXT1:"
        // Java/macOS compatibility baseline: transfer.chunk-size-bytes = 1 MiB.
        private const val DEFAULT_CHUNK_SIZE = 1024 * 1024
    }

    private val secureRandom = SecureRandom()

    // ═══════════════════════════════════════════════════════════
    //  FST2 File Encryption
    // ═══════════════════════════════════════════════════════════

    data class Fst2EncryptResult(
        val success: Boolean,
        val outputFileName: String,
        val fileSize: Long,
        val totalBlocks: Int
    )

    /**
     * Encrypts data from [input] using the recipient's public key and writes the FST2 container to [output].
     * @param fileName original file name (stored in encrypted header)
     * @param fileSize total size in bytes
     * @param receiverPublicKeyPem recipient's RSA public key in PEM format
     * @param chunkSize AES-GCM chunk size (default 64 KiB)
     * @return [Fst2EncryptResult] with metadata
     */
    @Throws(IOException::class, GeneralSecurityException::class)
    fun encryptFile(
        input: InputStream,
        fileName: String,
        fileSize: Long,
        receiverPublicKeyPem: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        output: OutputStream,
        onProgress: (processedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Fst2EncryptResult {
        val totalBlocks = computeTotalBlocks(fileSize, chunkSize)
        val aesKey = CryptoOperations.generateAesKey()
        val encryptedSessionKey = rsaEncryptor(aesKey, receiverPublicKeyPem)
        val encryptedSessionKeyBytes = Base64.getDecoder().decode(encryptedSessionKey)
        val nonceSeed = CryptoOperations.randomBytes(CryptoOperations.NONCE_SEED_BYTES)

        // Build CBOR header
        val header = LinkedHashMap<String, Any?>()
        header["fileName"] = fileName
        header["fileSize"] = fileSize
        header["chunkSizeBytes"] = chunkSize
        header["totalBlocks"] = totalBlocks
        val headerPlain = CborLite.encodeCanonical(header)

        // Encrypt header
        val headerAad = fst2HeaderAad(encryptedSessionKeyBytes, nonceSeed, headerPlain.size)
        val encryptedHeader = CryptoOperations.encryptChunk(
            headerPlain, aesKey,
            CryptoOperations.deriveNonce(nonceSeed, "FST2-header", 0),
            headerAad
        )

        val dos = DataOutputStream(output)
        // Write prefix
        dos.write(FST2_MAGIC)
        dos.writeByte(FST2_VERSION.toInt())
        dos.writeByte(ALG_KEY_WRAP_RSA.toInt())
        dos.writeByte(ALG_AES_256_GCM.toInt())
        dos.writeByte(ALG_NONCE_HMAC_SHA256.toInt())

        // Write encrypted session key
        writeLengthBytes(dos, encryptedSessionKeyBytes)
        // Write nonce seed
        writeLengthBytes(dos, nonceSeed)
        // Write encrypted header
        writeLengthBytes(dos, encryptedHeader.ciphertext)
        writeLengthBytes(dos, encryptedHeader.tag)

        // Write encrypted blocks
        writeEncryptedBlocks(
            input, dos, aesKey, nonceSeed, chunkSize, fileSize, totalBlocks,
            onProgress, isCancelled
        )
        dos.flush()

        return Fst2EncryptResult(true, fileName, fileSize, totalBlocks)
    }

    // ═══════════════════════════════════════════════════════════
    //  FST2 File Decryption
    // ═══════════════════════════════════════════════════════════

    data class Fst2DecryptResult(
        val success: Boolean,
        val fileName: String,
        val fileSize: Long,
        val totalBlocks: Int
    )

    @Throws(IOException::class, GeneralSecurityException::class)
    fun decryptFile(
        input: InputStream,
        output: OutputStream,
        onProgress: (processedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Fst2DecryptResult {
        val dis = DataInputStream(input)

        // Read and validate prefix
        readAndValidateFst2Prefix(dis)

        val encryptedSessionKey = readLengthBytes(dis, "encryptedSessionKey")
        val nonceSeed = readLengthBytes(dis, "nonceSeed")
        require(nonceSeed.size == CryptoOperations.NONCE_SEED_BYTES) { "Invalid FST2 nonce seed length" }

        val headerCiphertextLen = readNonNegativeLength(dis, "headerCiphertextLength")
        require(headerCiphertextLen <= MAX_HEADER_CIPHERTEXT_BYTES) {
            "FST2 header is too large"
        }
        val headerCiphertext = readExact(dis, headerCiphertextLen, "FST2 header truncated")

        val headerTag = readLengthBytes(dis, "headerTag")
        require(headerTag.size == CryptoOperations.GCM_TAG_BYTES) { "Invalid FST2 header tag length" }

        // Decrypt session key via RSA
        val aesKey = rsaDecryptor(Base64.getEncoder().encodeToString(encryptedSessionKey))

        // Decrypt header
        val headerAad = fst2HeaderAad(encryptedSessionKey, nonceSeed, headerCiphertextLen)
        val headerPlain = CryptoOperations.decryptChunk(
            CryptoOperations.deriveNonce(nonceSeed, "FST2-header", 0),
            headerCiphertext, headerTag, aesKey, headerAad
        )

        val header = CborLite.decodeMap(headerPlain)
        val originalFileName = header["fileName"] as? String ?: "decrypted-file"
        val fileSize = (header["fileSize"] as? Number)?.toLong() ?: throw IllegalArgumentException("Invalid FST2 file size")
        val chunkSize = (header["chunkSizeBytes"] as? Number)?.toInt() ?: DEFAULT_CHUNK_SIZE
        val totalBlocks = (header["totalBlocks"] as? Number)?.toInt() ?: throw IllegalArgumentException("Invalid FST2 total blocks")

        validateHeaderValues(fileSize, chunkSize, totalBlocks)

        // Decrypt blocks
        var writtenBytes = 0L
        for (blockIndex in 0 until totalBlocks) {
            if (isCancelled()) throw java.util.concurrent.CancellationException("File task cancelled")
            val idx = dis.readInt()
            val plaintextLen = dis.readInt()
            val ciphertextLen = readNonNegativeLength(dis, "ciphertextLength")

            require(idx == blockIndex) { "FST2 block index mismatch: expected $blockIndex, got $idx" }
            validatePlaintextLength(fileSize, chunkSize, totalBlocks, blockIndex, plaintextLen)
            require(ciphertextLen == plaintextLen) { "FST2 ciphertext length mismatch" }

            val ciphertext = readExact(dis, ciphertextLen, "FST2 block ciphertext truncated")

            val tag = readLengthBytes(dis, "blockTag")
            require(tag.size == CryptoOperations.GCM_TAG_BYTES) { "Invalid FST2 block tag length" }

            val aad = fst2BlockAad(blockIndex, plaintextLen, ciphertextLen)
            val plain = CryptoOperations.decryptChunk(
                CryptoOperations.deriveNonce(nonceSeed, "FST2-block", blockIndex),
                ciphertext, tag, aesKey, aad
            )
            require(plain.size == plaintextLen) { "FST2 plaintext length mismatch" }
            output.write(plain)
            writtenBytes += plain.size
            onProgress(writtenBytes, fileSize)
        }

        require(dis.read() == -1) { "FST2 contains trailing bytes" }
        require(writtenBytes == fileSize) { "FST2 final byte count mismatch: expected $fileSize, got $writtenBytes" }

        return Fst2DecryptResult(true, originalFileName, fileSize, totalBlocks)
    }

    /**
     * Decrypts the authenticated header before asking the caller to create the
     * final output. This prevents malformed containers from creating a visible
     * MediaStore item and lets the caller use the authenticated original name.
     */
    fun decryptFileTo(
        input: InputStream,
        outputFactory: (authenticatedFileName: String) -> OutputStream,
        onProgress: (processedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Fst2DecryptResult {
        val dis = DataInputStream(input)
        readAndValidateFst2Prefix(dis)
        val encryptedSessionKey = readLengthBytes(dis, "encryptedSessionKey")
        val nonceSeed = readLengthBytes(dis, "nonceSeed")
        require(nonceSeed.size == CryptoOperations.NONCE_SEED_BYTES) { "Invalid FST2 nonce seed length" }
        val headerCiphertextLen = readNonNegativeLength(dis, "headerCiphertextLength")
        require(headerCiphertextLen <= MAX_HEADER_CIPHERTEXT_BYTES) {
            "FST2 header is too large"
        }
        val headerCiphertext = readExact(dis, headerCiphertextLen, "FST2 header truncated")
        val headerTag = readLengthBytes(dis, "headerTag")
        require(headerTag.size == CryptoOperations.GCM_TAG_BYTES) { "Invalid FST2 header tag length" }

        val aesKey = rsaDecryptor(Base64.getEncoder().encodeToString(encryptedSessionKey))
        val headerPlain = CryptoOperations.decryptChunk(
            CryptoOperations.deriveNonce(nonceSeed, "FST2-header", 0),
            headerCiphertext,
            headerTag,
            aesKey,
            fst2HeaderAad(encryptedSessionKey, nonceSeed, headerCiphertextLen)
        )
        val header = CborLite.decodeMap(headerPlain)
        val fileName = header["fileName"] as? String ?: "decrypted-file"
        val fileSize = (header["fileSize"] as? Number)?.toLong()
            ?: throw IllegalArgumentException("Invalid FST2 file size")
        val chunkSize = (header["chunkSizeBytes"] as? Number)?.toInt() ?: DEFAULT_CHUNK_SIZE
        val totalBlocks = (header["totalBlocks"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Invalid FST2 total blocks")
        validateHeaderValues(fileSize, chunkSize, totalBlocks)

        outputFactory(fileName).use { output ->
            var writtenBytes = 0L
            for (blockIndex in 0 until totalBlocks) {
                if (isCancelled()) throw java.util.concurrent.CancellationException("File task cancelled")
                val idx = dis.readInt()
                val plaintextLen = dis.readInt()
                val ciphertextLen = readNonNegativeLength(dis, "ciphertextLength")
                require(idx == blockIndex) { "FST2 block index mismatch" }
                validatePlaintextLength(fileSize, chunkSize, totalBlocks, blockIndex, plaintextLen)
                require(ciphertextLen == plaintextLen) { "FST2 ciphertext length mismatch" }
                val ciphertext = readExact(dis, ciphertextLen, "FST2 block ciphertext truncated")
                val tag = readLengthBytes(dis, "blockTag")
                require(tag.size == CryptoOperations.GCM_TAG_BYTES) { "Invalid FST2 block tag length" }
                val plain = CryptoOperations.decryptChunk(
                    CryptoOperations.deriveNonce(nonceSeed, "FST2-block", blockIndex),
                    ciphertext,
                    tag,
                    aesKey,
                    fst2BlockAad(blockIndex, plaintextLen, ciphertextLen)
                )
                require(plain.size == plaintextLen) { "FST2 plaintext length mismatch" }
                output.write(plain)
                writtenBytes += plain.size
                onProgress(writtenBytes, fileSize)
            }
            require(dis.read() == -1) { "FST2 contains trailing bytes" }
            require(writtenBytes == fileSize) { "FST2 final byte count mismatch" }
        }
        return Fst2DecryptResult(true, fileName, fileSize, totalBlocks)
    }

    // ═══════════════════════════════════════════════════════════
    //  FST-TEXT1 Text Encryption
    // ═══════════════════════════════════════════════════════════

    data class FstTextEncryptResult(
        val success: Boolean,
        val payload: String,
        val plaintextLength: Int
    )

    @Throws(GeneralSecurityException::class)
    fun encryptText(text: String, receiverPublicKeyPem: String): FstTextEncryptResult {
        val plain = validateText(text)
        val aesKey = CryptoOperations.generateAesKey()
        val encryptedSessionKey = rsaEncryptor(aesKey, receiverPublicKeyPem)
        val encryptedSessionKeyBytes = Base64.getDecoder().decode(encryptedSessionKey)
        val nonce = CryptoOperations.randomBytes(CryptoOperations.GCM_NONCE_BYTES)
        val aad = fstTextAad(encryptedSessionKeyBytes, nonce, plain.size)
        val encrypted = CryptoOperations.encryptChunk(plain, aesKey, nonce, aad)

        val payload = LinkedHashMap<String, Any?>()
        payload["version"] = FST2_VERSION.toInt()
        payload["keyWrapAlg"] = ALG_KEY_WRAP_RSA.toInt()
        payload["contentAlg"] = ALG_AES_256_GCM.toInt()
        payload["encryptedSessionKey"] = encryptedSessionKeyBytes
        payload["nonce"] = nonce
        payload["ciphertext"] = encrypted.ciphertext
        payload["tag"] = encrypted.tag
        payload["plaintextLength"] = plain.size

        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(CborLite.encodeCanonical(payload))
        return FstTextEncryptResult(true, "$FST_TEXT_PREFIX$encoded", plain.size)
    }

    // ═══════════════════════════════════════════════════════════
    //  FST-TEXT1 Text Decryption
    // ═══════════════════════════════════════════════════════════

    data class FstTextDecryptResult(
        val success: Boolean,
        val text: String,
        val plaintextLength: Int
    )

    @Throws(GeneralSecurityException::class)
    fun decryptText(payloadText: String): FstTextDecryptResult {
        val encoded = normalizeFstTextPayload(payloadText)
        val payloadBytes = decodeBase64Url(encoded)
        val payload = CborLite.decodeMap(payloadBytes)

        val version = (payload["version"] as? Number)?.toInt() ?: throw IllegalArgumentException("Missing FST-TEXT1 version")
        val keyWrapAlg = (payload["keyWrapAlg"] as? Number)?.toInt() ?: throw IllegalArgumentException("Missing keyWrapAlg")
        val contentAlg = (payload["contentAlg"] as? Number)?.toInt() ?: throw IllegalArgumentException("Missing contentAlg")

        require(version == FST2_VERSION.toInt() && keyWrapAlg == ALG_KEY_WRAP_RSA.toInt() && contentAlg == ALG_AES_256_GCM.toInt()) {
            "Unsupported FST-TEXT1 version or algorithm"
        }

        val encryptedSessionKey = payload["encryptedSessionKey"] as? ByteArray ?: throw IllegalArgumentException("Missing encryptedSessionKey")
        val nonce = payload["nonce"] as? ByteArray ?: throw IllegalArgumentException("Missing nonce")
        val ciphertext = payload["ciphertext"] as? ByteArray ?: throw IllegalArgumentException("Missing ciphertext")
        val tag = payload["tag"] as? ByteArray ?: throw IllegalArgumentException("Missing tag")
        val plaintextLength = (payload["plaintextLength"] as? Number)?.toInt() ?: throw IllegalArgumentException("Missing plaintextLength")

        require(nonce.size == CryptoOperations.GCM_NONCE_BYTES) { "Invalid FST-TEXT1 nonce length" }
        require(tag.size == CryptoOperations.GCM_TAG_BYTES) { "Invalid FST-TEXT1 tag length" }
        require(plaintextLength in 1..MAX_TEXT_BYTES) { "FST-TEXT1 plaintext length exceeds limit" }

        val aesKey = rsaDecryptor(Base64.getEncoder().encodeToString(encryptedSessionKey))
        val aad = fstTextAad(encryptedSessionKey, nonce, plaintextLength)
        val plain = CryptoOperations.decryptChunk(nonce, ciphertext, tag, aesKey, aad)
        require(plain.size == plaintextLength) { "FST-TEXT1 plaintext length mismatch" }

        val text = decodeUtf8Strict(plain)
        return FstTextDecryptResult(true, text, plaintextLength)
    }

    // ═══════════════════════════════════════════════════════════
    //  Private helpers
    // ═══════════════════════════════════════════════════════════

    private fun writeEncryptedBlocks(
        source: InputStream,
        out: DataOutputStream,
        aesKey: SecretKey,
        nonceSeed: ByteArray,
        chunkSize: Int,
        fileSize: Long,
        totalBlocks: Int
        ,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val buffer = ByteArray(chunkSize)
        var processedBytes = 0L
        for (blockIndex in 0 until totalBlocks) {
            if (isCancelled()) throw java.util.concurrent.CancellationException("File task cancelled")
            val expectedLength = minOf(chunkSize.toLong(), fileSize - processedBytes).toInt()
            val length = readUpTo(source, buffer, expectedLength)
            require(length == expectedLength) {
                "Input file ended early: expected $fileSize bytes, read ${processedBytes + length}"
            }
            val plain = buffer.copyOf(length)
            val aad = fst2BlockAad(blockIndex, length, length)
            val encrypted = CryptoOperations.encryptChunk(
                plain, aesKey,
                CryptoOperations.deriveNonce(nonceSeed, "FST2-block", blockIndex),
                aad
            )
            out.writeInt(blockIndex)
            out.writeInt(length)
            out.writeInt(encrypted.ciphertext.size)
            out.write(encrypted.ciphertext)
            writeLengthBytes(out, encrypted.tag)
            processedBytes += length
            onProgress(processedBytes, fileSize)
        }
        require(source.read() == -1) {
            "Input file is larger than its reported size of $fileSize bytes"
        }
    }

    private fun readUpTo(source: InputStream, buffer: ByteArray, expectedLength: Int): Int {
        var offset = 0
        while (offset < expectedLength) {
            val read = source.read(buffer, offset, expectedLength - offset)
            if (read < 0) break
            if (read == 0) {
                val singleByte = source.read()
                if (singleByte < 0) break
                buffer[offset++] = singleByte.toByte()
            } else {
                offset += read
            }
        }
        return offset
    }

    private fun validateText(text: String): ByteArray {
        require(text.isNotBlank()) { "Text cannot be empty" }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_TEXT_BYTES) { "Text exceeds ${MAX_TEXT_BYTES / 1024} KiB limit" }
        return bytes
    }

    private fun normalizeFstTextPayload(payload: String): String {
        val trimmed = payload.trim()
        require(trimmed.startsWith(FST_TEXT_PREFIX)) { "Not an FST-TEXT1 payload" }
        val encoded = trimmed.removePrefix(FST_TEXT_PREFIX)
        val compact = encoded.filter { !it.isWhitespace() }
        require(compact.length <= MAX_TEXT_PAYLOAD_CHARS) { "FST-TEXT1 payload is too large" }
        return compact
    }

    private fun decodeBase64Url(encoded: String): ByteArray {
        var normalized = encoded
        val remainder = normalized.length % 4
        if (remainder != 0) {
            normalized = normalized + "=".repeat(4 - remainder)
        }
        return Base64.getUrlDecoder().decode(normalized)
    }

    private fun decodeUtf8Strict(plain: ByteArray): String {
        return try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plain)).toString()
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("FST-TEXT1 plaintext is not valid UTF-8", e)
        }
    }

    // ── FST2 prefix I/O ──────────────────────────────────────

    private fun readAndValidateFst2Prefix(dis: DataInputStream) {
        val magic = readExact(dis, FST2_MAGIC.size, "FST2 prefix truncated")
        require(Arrays.equals(magic, FST2_MAGIC)) { "Invalid magic; not an FST2 file" }
        val version = dis.readByte()
        val keyWrapAlg = dis.readByte()
        val contentAlg = dis.readByte()
        val nonceDerivationAlg = dis.readByte()
        require(
            version == FST2_VERSION &&
                    keyWrapAlg == ALG_KEY_WRAP_RSA &&
                    contentAlg == ALG_AES_256_GCM &&
                    nonceDerivationAlg == ALG_NONCE_HMAC_SHA256
        ) { "Unsupported FST2 version or algorithm" }
    }

    // ── Length-prefixed byte I/O ─────────────────────────────

    private fun writeLengthBytes(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readLengthBytes(dis: DataInputStream, fieldName: String): ByteArray {
        val length = readNonNegativeLength(dis, fieldName)
        require(length <= MAX_LENGTH_PREFIXED_FIELD_BYTES) {
            "FST2 field is too large: $fieldName"
        }
        return readExact(dis, length, "FST2 field truncated: $fieldName")
    }

    private fun readNonNegativeLength(dis: DataInputStream, fieldName: String): Int {
        val length = dis.readInt()
        require(length >= 0) { "Negative FST2 length: $fieldName" }
        return length
    }

    private fun readExact(dis: DataInputStream, length: Int, truncatedMessage: String): ByteArray {
        val bytes = ByteArray(length)
        try {
            dis.readFully(bytes)
        } catch (failure: java.io.EOFException) {
            throw IllegalArgumentException(truncatedMessage, failure)
        }
        return bytes
    }

    // ── AAD construction ─────────────────────────────────────

    private fun fst2HeaderAad(
        encryptedSessionKey: ByteArray,
        nonceSeed: ByteArray,
        headerCiphertextLength: Int
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        val dos = DataOutputStream(buf)
        dos.write(FST2_MAGIC)
        dos.writeByte(FST2_VERSION.toInt())
        dos.writeByte(ALG_KEY_WRAP_RSA.toInt())
        dos.writeByte(ALG_AES_256_GCM.toInt())
        dos.writeByte(ALG_NONCE_HMAC_SHA256.toInt())
        writeLengthBytes(dos, encryptedSessionKey)
        writeLengthBytes(dos, nonceSeed)
        dos.writeInt(headerCiphertextLength)
        return buf.toByteArray()
    }

    private fun fst2BlockAad(blockIndex: Int, plaintextLength: Int, ciphertextLength: Int): ByteArray {
        val buf = ByteArrayOutputStream()
        val dos = DataOutputStream(buf)
        dos.write(FST2_MAGIC)
        dos.writeByte(FST2_VERSION.toInt())
        dos.writeByte(ALG_KEY_WRAP_RSA.toInt())
        dos.writeByte(ALG_AES_256_GCM.toInt())
        dos.writeByte(ALG_NONCE_HMAC_SHA256.toInt())
        dos.writeInt(blockIndex)
        dos.writeInt(plaintextLength)
        dos.writeInt(ciphertextLength)
        return buf.toByteArray()
    }

    private fun fstTextAad(encryptedSessionKey: ByteArray, nonce: ByteArray, plaintextLength: Int): ByteArray {
        val aad = LinkedHashMap<String, Any?>()
        aad["protocol"] = FST_TEXT_PREFIX
        aad["version"] = FST2_VERSION.toInt()
        aad["keyWrapAlg"] = ALG_KEY_WRAP_RSA.toInt()
        aad["contentAlg"] = ALG_AES_256_GCM.toInt()
        aad["encryptedSessionKey"] = encryptedSessionKey
        aad["nonce"] = nonce
        aad["plaintextLength"] = plaintextLength
        return CborLite.encodeCanonical(aad)
    }

    // ── Validation ───────────────────────────────────────────

    private fun computeTotalBlocks(fileSize: Long, chunkSize: Int): Int {
        require(chunkSize in 1..MAX_CHUNK_SIZE) {
            "Chunk size must be between 1 and $MAX_CHUNK_SIZE bytes"
        }
        require(fileSize >= 0) { "File size must not be negative" }
        if (fileSize == 0L) return 0
        val total = ((fileSize - 1) / chunkSize) + 1
        require(total <= Int.MAX_VALUE.toLong()) { "File too large for FST2" }
        return total.toInt()
    }

    private fun validateHeaderValues(fileSize: Long, chunkSize: Int, totalBlocks: Int) {
        require(fileSize >= 0) { "Invalid FST2 file size" }
        val expected = computeTotalBlocks(fileSize, chunkSize)
        require(totalBlocks == expected) { "Invalid FST2 total block count: expected $expected, got $totalBlocks" }
    }

    private fun validatePlaintextLength(fileSize: Long, chunkSize: Int, totalBlocks: Int, blockIndex: Int, length: Int) {
        require(length in 0..chunkSize) { "Invalid FST2 block plaintext length" }
        if (totalBlocks == 0) return
        val expected = if (blockIndex == totalBlocks - 1) (fileSize - blockIndex.toLong() * chunkSize).toInt() else chunkSize
        require(length == expected) { "FST2 block plaintext length mismatch: expected $expected, got $length" }
    }
}
