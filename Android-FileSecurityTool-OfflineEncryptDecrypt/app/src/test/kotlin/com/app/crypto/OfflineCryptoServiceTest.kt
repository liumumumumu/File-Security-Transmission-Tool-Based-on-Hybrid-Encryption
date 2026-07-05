package com.filesecuritytool.android.crypto

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import com.filesecuritytool.android.core.crypto.RsaOaep

/**
 * Tests for OfflineCryptoService — FST2 and FST-TEXT1 round-trip encryption/decryption.
 * Uses an in-memory RSA key pair (not Android Keystore) for testing.
 */
class OfflineCryptoServiceTest {

    private lateinit var service: OfflineCryptoService
    private lateinit var publicKeyPem: String
    private lateinit var privateKey: java.security.PrivateKey

    @Before
    fun setUp() {
        // Generate an in-memory RSA-2048 key pair for testing
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()

        publicKeyPem = toPemPublicKey(keyPair.public)
        privateKey = keyPair.private

        // Create service with in-memory RSA operations
        service = OfflineCryptoService(
            rsaEncryptor = { aesKey, _ ->
                Base64.getEncoder().encodeToString(RsaOaep.encrypt(aesKey.encoded, keyPair.public))
            },
            rsaDecryptor = { encryptedKeyBase64 ->
                val keyBytes = RsaOaep.decrypt(
                    Base64.getDecoder().decode(encryptedKeyBase64),
                    keyPair.private
                )
                javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            }
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  FST2 file round-trip tests
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `FST2 encrypt then decrypt small file`() {
        val original = "Hello, World! This is a test file for FST2 encryption.".toByteArray()
        val input = ByteArrayInputStream(original)

        // Encrypt
        val encryptedOut = ByteArrayOutputStream()
        val encryptResult = service.encryptFile(
            input = input,
            fileName = "test.txt",
            fileSize = original.size.toLong(),
            receiverPublicKeyPem = publicKeyPem,
            chunkSize = 64,
            output = encryptedOut
        )
        assertTrue(encryptResult.success)
        assertEquals("test.txt", encryptResult.outputFileName)
        assertEquals(original.size.toLong(), encryptResult.fileSize)

        // Decrypt
        val encryptedBytes = encryptedOut.toByteArray()
        assertTrue(encryptedBytes.size > 50) // Should have header + blocks

        val decryptInput = ByteArrayInputStream(encryptedBytes)
        val decryptOut = ByteArrayOutputStream()
        val decryptResult = service.decryptFile(decryptInput, decryptOut)

        assertTrue(decryptResult.success)
        assertEquals("test.txt", decryptResult.fileName)
        assertEquals(original.size.toLong(), decryptResult.fileSize)

        val decrypted = decryptOut.toByteArray()
        assertArrayEquals("Decrypted content must match original", original, decrypted)
    }

    @Test
    fun `FST2 encrypt then decrypt large file spanning multiple blocks`() {
        // Create a 200-byte file with 64-byte chunks = 4 blocks
        val original = ByteArray(200) { (it % 256).toByte() }
        val input = ByteArrayInputStream(original)
        val encryptedOut = ByteArrayOutputStream()

        val encryptResult = service.encryptFile(
            input = input,
            fileName = "large.bin",
            fileSize = original.size.toLong(),
            receiverPublicKeyPem = publicKeyPem,
            chunkSize = 64,
            output = encryptedOut
        )
        assertEquals(200L, encryptResult.fileSize)
        assertEquals(4, encryptResult.totalBlocks) // ceil(200/64) = 4

        // Decrypt
        val decryptInput = ByteArrayInputStream(encryptedOut.toByteArray())
        val decryptOut = ByteArrayOutputStream()
        val decryptResult = service.decryptFile(decryptInput, decryptOut)

        assertEquals(4, decryptResult.totalBlocks)
        assertArrayEquals(original, decryptOut.toByteArray())
    }

    @Test
    fun `FST2 encryption tolerates fragmented input reads`() {
        val original = ByteArray(200) { it.toByte() }
        val fragmented = object : InputStream() {
            private val delegate = ByteArrayInputStream(original)

            override fun read(): Int = delegate.read()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                delegate.read(buffer, offset, minOf(length, 7))
        }
        val encryptedOut = ByteArrayOutputStream()

        service.encryptFile(
            input = fragmented,
            fileName = "fragmented.bin",
            fileSize = original.size.toLong(),
            receiverPublicKeyPem = publicKeyPem,
            chunkSize = 64,
            output = encryptedOut
        )

        val decryptedOut = ByteArrayOutputStream()
        service.decryptFile(ByteArrayInputStream(encryptedOut.toByteArray()), decryptedOut)
        assertArrayEquals(original, decryptedOut.toByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `FST2 encryption rejects input shorter than reported size`() {
        service.encryptFile(
            input = ByteArrayInputStream(ByteArray(9)),
            fileName = "short.bin",
            fileSize = 10,
            receiverPublicKeyPem = publicKeyPem,
            chunkSize = 4,
            output = ByteArrayOutputStream()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `FST2 encryption rejects input longer than reported size`() {
        service.encryptFile(
            input = ByteArrayInputStream(ByteArray(11)),
            fileName = "long.bin",
            fileSize = 10,
            receiverPublicKeyPem = publicKeyPem,
            chunkSize = 4,
            output = ByteArrayOutputStream()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `FST2 encryption rejects oversized chunks`() {
        service.encryptFile(
            input = ByteArrayInputStream(byteArrayOf(1)),
            fileName = "oversized.bin",
            fileSize = 1,
            receiverPublicKeyPem = publicKeyPem,
            chunkSize = 1024 * 1024 + 1,
            output = ByteArrayOutputStream()
        )
    }

    @Test
    fun `FST2 encrypt empty file`() {
        val original = ByteArray(0)
        val input = ByteArrayInputStream(original)
        val encryptedOut = ByteArrayOutputStream()

        val encryptResult = service.encryptFile(
            input = input,
            fileName = "empty.txt",
            fileSize = 0L,
            receiverPublicKeyPem = publicKeyPem,
            output = encryptedOut
        )
        assertEquals(0, encryptResult.totalBlocks)

        val decryptInput = ByteArrayInputStream(encryptedOut.toByteArray())
        val decryptOut = ByteArrayOutputStream()
        val decryptResult = service.decryptFile(decryptInput, decryptOut)

        assertEquals(0L, decryptResult.fileSize)
        assertEquals(0, decryptOut.toByteArray().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `FST2 decrypt rejects non-FST2 file`() {
        val garbage = "This is not an FST2 file".toByteArray()
        val input = ByteArrayInputStream(garbage)
        val output = ByteArrayOutputStream()
        service.decryptFile(input, output)
    }

    @Test
    fun `FST2 magic bytes are written correctly`() {
        val original = "test".toByteArray()
        val encryptedOut = ByteArrayOutputStream()

        service.encryptFile(
            input = ByteArrayInputStream(original),
            fileName = "test.txt",
            fileSize = original.size.toLong(),
            receiverPublicKeyPem = publicKeyPem,
            output = encryptedOut
        )

        val bytes = encryptedOut.toByteArray()
        assertEquals('F'.code.toByte(), bytes[0])
        assertEquals('S'.code.toByte(), bytes[1])
        assertEquals('T'.code.toByte(), bytes[2])
        assertEquals('2'.code.toByte(), bytes[3])
        assertEquals(1.toByte(), bytes[4]) // version
    }

    // ═══════════════════════════════════════════════════════════
    //  FST-TEXT1 round-trip tests
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `FST-TEXT1 encrypt then decrypt text`() {
        val original = "这是一条测试消息 for FST-TEXT1 encryption."
        val encryptResult = service.encryptText(original, publicKeyPem)

        assertTrue(encryptResult.success)
        assertTrue(encryptResult.payload.startsWith("FST-TEXT1:"))
        assertEquals(original.toByteArray(Charsets.UTF_8).size, encryptResult.plaintextLength)

        val decryptResult = service.decryptText(encryptResult.payload)
        assertTrue(decryptResult.success)
        assertEquals(original, decryptResult.text)
    }

    @Test
    fun `FST-TEXT1 handles Chinese characters`() {
        val original = "你好世界！🔐 安全加密测试。"
        val encryptResult = service.encryptText(original, publicKeyPem)
        val decryptResult = service.decryptText(encryptResult.payload)

        assertEquals(original, decryptResult.text)
    }

    @Test
    fun `FST-TEXT1 handles max-size text`() {
        // 16 KiB limit
        val original = "A".repeat(16 * 1024)
        val encryptResult = service.encryptText(original, publicKeyPem)
        val decryptResult = service.decryptText(encryptResult.payload)

        assertEquals(original, decryptResult.text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `FST-TEXT1 rejects empty text`() {
        service.encryptText("   ", publicKeyPem)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `FST-TEXT1 rejects oversized text`() {
        val tooBig = "A".repeat(16 * 1024 + 1)
        service.encryptText(tooBig, publicKeyPem)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `FST-TEXT1 decrypt rejects non-prefixed payload`() {
        service.decryptText("Not an FST-TEXT1 payload")
    }

    @Test
    fun `FST-TEXT1 payload is URL-safe`() {
        val original = "Test message"
        val encryptResult = service.encryptText(original, publicKeyPem)

        val payload = encryptResult.payload.removePrefix("FST-TEXT1:")
        // Base64URL without padding — should not contain +, /, or =
        assertFalse("Payload should not contain '+'", payload.contains('+'))
        assertFalse("Payload should not contain '/'", payload.contains('/'))
        assertFalse("Payload should not contain '='", payload.contains('='))
    }

    // ═══════════════════════════════════════════════════════════
    //  Cross-compatibility note
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `FST2 header uses reference protocol identifiers`() {
        val original = "Java interop test content.".toByteArray()
        val encryptedOut = ByteArrayOutputStream()

        service.encryptFile(
            input = ByteArrayInputStream(original),
            fileName = "interop.txt",
            fileSize = original.size.toLong(),
            receiverPublicKeyPem = publicKeyPem,
            output = encryptedOut
        )

        // Verify the encrypted file starts with FST2 magic
        val fst2Bytes = encryptedOut.toByteArray()
        val magic = fst2Bytes.copyOfRange(0, 4)
        assertArrayEquals(byteArrayOf('F'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), '2'.code.toByte()), magic)

        // Verify we can read back the length-prefixed encryptedSessionKey
        val version = fst2Bytes[4]
        assertEquals(1, version.toInt())

        val keyWrapAlg = fst2Bytes[5]
        assertEquals(1, keyWrapAlg.toInt()) // RSA

        val contentAlg = fst2Bytes[6]
        assertEquals(1, contentAlg.toInt()) // AES-256-GCM

        val nonceAlg = fst2Bytes[7]
        assertEquals(1, nonceAlg.toInt()) // HMAC-SHA256
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private fun toPemPublicKey(publicKey: java.security.PublicKey): String {
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(publicKey.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----\n"
    }
}
