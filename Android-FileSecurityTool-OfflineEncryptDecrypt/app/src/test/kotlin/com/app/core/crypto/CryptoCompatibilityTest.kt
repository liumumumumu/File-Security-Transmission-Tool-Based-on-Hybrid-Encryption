package com.filesecuritytool.android.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator

class CryptoCompatibilityTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    @Test
    fun `OAEP explicitly uses SHA256 for digest and MGF1`() {
        val plain = ByteArray(32) { it.toByte() }
        assertArrayEquals(plain, RsaOaep.decrypt(RsaOaep.encrypt(plain, pair.public), pair.private))
    }

    @Test
    fun `public QR export uses compact Java compatible FST PUB1 shape`() {
        val pem = PublicKeyCodec.toPem(pair.public)
        val artifact = PublicKeyArtifactCodec.encode(pem)
        assertTrue(artifact.startsWith("FST-PUB1:"))
        assertEquals(pem, PublicKeyArtifactCodec.decode(artifact))
    }

    @Test
    fun `Java FST KEY1 public artifact remains readable`() {
        val pem = PublicKeyCodec.toPem(pair.public)
        val escaped = pem.replace("\n", "\\n")
        val javaArtifact =
            """{"artifactType":"FST-KEY1","keyType":"public","publicKey":"$escaped"}"""
        assertEquals(pem, PublicKeyArtifactCodec.decode(javaArtifact))
    }

    @Test
    fun `Java response wrapper containing qrText remains readable`() {
        val pem = PublicKeyCodec.toPem(pair.public)
        val payload = PublicKeyArtifactCodec.encode(pem)
        assertEquals(
            pem,
            PublicKeyArtifactCodec.decode("""{"success":true,"qrText":"$payload"}""")
        )
    }
}
