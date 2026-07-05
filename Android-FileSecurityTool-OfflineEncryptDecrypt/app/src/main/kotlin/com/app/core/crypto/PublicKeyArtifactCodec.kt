package com.filesecuritytool.android.core.crypto

import com.google.gson.JsonParser
import java.util.Base64

object PublicKeyArtifactCodec {
    private const val ARTIFACT_TYPE = "FST-KEY1"
    private const val KEY_TYPE = "public"
    private const val LEGACY_PREFIX = "FST-PUB1:"

    /**
     * Android exports the compact legacy payload required by the product
     * contract. The Java client accepts this form and its newer FST-KEY1 JSON
     * form remains accepted by [decode].
     */
    fun encode(publicKeyPem: String): String =
        LEGACY_PREFIX + Base64.getEncoder().encodeToString(
            PublicKeyCodec.parse(publicKeyPem).encoded
        )

    fun decode(payload: String): String {
        val text = payload.trim().removePrefix("\uFEFF").trim()
        val keyText = when {
            text.startsWith(LEGACY_PREFIX) -> text.removePrefix(LEGACY_PREFIX)
            text.startsWith("{") -> {
                val json = JsonParser.parseString(text).asJsonObject
                when {
                    json.get("artifactType")?.asString == ARTIFACT_TYPE -> {
                        require(json.get("keyType")?.asString == KEY_TYPE) {
                            "Artifact is not a public key"
                        }
                        json.get("publicKey")?.asString
                            ?: throw IllegalArgumentException("Public key is missing")
                    }
                    json.has("qrText") -> return decode(json.get("qrText").asString)
                    json.has("publicKey") -> json.get("publicKey").asString
                    else -> throw IllegalArgumentException("Unsupported key artifact")
                }
            }
            else -> throw IllegalArgumentException("QR payload is not a public key artifact")
        }
        return PublicKeyCodec.normalizePem(keyText)
    }
}
