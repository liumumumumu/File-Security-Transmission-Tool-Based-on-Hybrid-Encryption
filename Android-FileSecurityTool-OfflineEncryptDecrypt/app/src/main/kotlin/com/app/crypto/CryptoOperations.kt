package com.filesecuritytool.android.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Port of CryptoSupport.java's AES-GCM, HMAC, and SecureRandom operations.
 * Pure JCE — no Android dependencies.
 */
object CryptoOperations {

    const val GCM_NONCE_BYTES = 12
    const val GCM_TAG_BYTES = 16
    const val GCM_TAG_BITS = 128
    const val NONCE_SEED_BYTES = 32
    const val AES_KEY_BITS = 256

    private val secureRandom = SecureRandom()

    fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_BITS, secureRandom)
        return keyGen.generateKey()
    }

    fun encryptChunk(
        plain: ByteArray,
        aesKey: SecretKey,
        nonce: ByteArray,
        aad: ByteArray?
    ): AesGcmChunk {
        validateAes256Key(aesKey)
        require(nonce.size == GCM_NONCE_BYTES) {
            "AES-GCM nonce must be $GCM_NONCE_BYTES bytes"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
        if (aad != null && aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        val encrypted = cipher.doFinal(plain)
        val ciphertextLength = encrypted.size - GCM_TAG_BYTES
        require(ciphertextLength >= 0) { "AES-GCM output shorter than tag length" }
        val ciphertext = encrypted.copyOfRange(0, ciphertextLength)
        val tag = encrypted.copyOfRange(ciphertextLength, encrypted.size)
        return AesGcmChunk(nonce, ciphertext, tag)
    }

    fun decryptChunk(
        nonce: ByteArray,
        ciphertext: ByteArray,
        tag: ByteArray,
        aesKey: SecretKey,
        aad: ByteArray?
    ): ByteArray {
        validateAes256Key(aesKey)
        require(nonce.size == GCM_NONCE_BYTES) { "AES-GCM nonce must be $GCM_NONCE_BYTES bytes" }
        require(tag.size == GCM_TAG_BYTES) { "AES-GCM tag must be $GCM_TAG_BYTES bytes" }
        val encrypted = ByteArray(ciphertext.size + tag.size)
        System.arraycopy(ciphertext, 0, encrypted, 0, ciphertext.size)
        System.arraycopy(tag, 0, encrypted, ciphertext.size, tag.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
        if (aad != null && aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(encrypted)
    }

    /**
     * HMAC-SHA256 based nonce derivation for FST2.
     * domain = "FST2-header" or "FST2-block", index = block number (0-based).
     * Returns 12 bytes (GCM nonce length).
     */
    fun deriveNonce(seed: ByteArray, domain: String, index: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(seed, "HmacSHA256"))
        mac.update(domain.toByteArray(Charsets.US_ASCII))
        mac.update(0)
        mac.update(
            ByteArray(4).apply {
                this[0] = ((index shr 24) and 0xff).toByte()
                this[1] = ((index shr 16) and 0xff).toByte()
                this[2] = ((index shr 8) and 0xff).toByte()
                this[3] = (index and 0xff).toByte()
            }
        )
        return mac.doFinal().copyOf(GCM_NONCE_BYTES)
    }

    fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    private fun validateAes256Key(aesKey: SecretKey) {
        val encoded = aesKey.encoded
        require(encoded != null && encoded.size == AES_KEY_BITS / 8) {
            "AES-256 key must be 32 bytes"
        }
    }
}
