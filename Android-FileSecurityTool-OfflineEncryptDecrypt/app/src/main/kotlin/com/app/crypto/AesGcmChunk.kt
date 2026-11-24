package com.filesecuritytool.android.crypto

/**
 * Port of AesGcmChunk.java — holds the three components of an AES-256-GCM encryption output.
 * Nonce is 12 bytes, ciphertext is variable length, tag is 16 bytes.
 */
data class AesGcmChunk(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AesGcmChunk) return false
        return nonce.contentEquals(other.nonce) &&
                ciphertext.contentEquals(other.ciphertext) &&
                tag.contentEquals(other.tag)
    }

    override fun hashCode(): Int {
        var result = nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "AesGcmChunk(nonce=${nonce.joinToString("") { "%02x".format(it) }.take(12)}..., " +
                "ciphertext=${ciphertext.size}b, tag=${tag.joinToString("") { "%02x".format(it) }.take(12)}...)"
    }
}
