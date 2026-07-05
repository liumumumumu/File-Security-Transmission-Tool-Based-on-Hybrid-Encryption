package com.filesecuritytool.android.core.crypto

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object PublicKeyCodec {
    private const val BEGIN = "-----BEGIN PUBLIC KEY-----"
    private const val END = "-----END PUBLIC KEY-----"

    fun toPem(key: PublicKey): String = toPem(key.encoded)

    fun toPem(der: ByteArray): String {
        val body = Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(der)
        return "$BEGIN\n$body\n$END\n"
    }

    fun normalizePem(value: String): String = toPem(parse(value).encoded)

    fun parse(value: String): PublicKey {
        val compact = value
            .replace(BEGIN, "")
            .replace(END, "")
            .filterNot(Char::isWhitespace)
        val der = Base64.getDecoder().decode(compact)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    /** Compatible with Java CryptoEngine.fingerprintPemText. */
    fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizePem(value).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
