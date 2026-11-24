package com.filesecuritytool.android.core.crypto

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Stores a portable RSA key pair while keeping its PKCS#8 private key encrypted
 * by an authenticated, hardware-backed Android Keystore AES key.
 *
 * RSA operations intentionally run through the regular crypto provider so
 * OAEP SHA-256 + MGF1-SHA256 exactly matches the desktop protocol on Android
 * versions whose Android Keystore RSA implementation only authorizes MGF1-SHA1.
 */
class HardwareKeyStore(
    context: Context,
    private val alias: String = KEY_ALIAS
) {
    private val appContext = context.applicationContext
    private val keyguardManager = appContext.getSystemService(KeyguardManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val permanentlyInvalidated = AtomicBoolean(false)

    enum class SecurityLevel { STRONGBOX, TEE }

    data class KeyStatus(
        val exists: Boolean,
        val publicKeyPem: String? = null,
        val fingerprint: String? = null,
        val securityLevel: SecurityLevel? = null,
        val permanentlyInvalidated: Boolean = false,
        val requiresRegeneration: Boolean = false
    )

    private val wrappingAlias: String
        get() = "${alias}_private_key_wrap_v2"

    private val store: KeyStore
        get() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun status(): KeyStatus {
        val publicKeyPem = preferences.getString(publicKeyPreference, null)
        val encryptedPrivateKey = preferences.getString(privateKeyPreference, null)
        val nonce = preferences.getString(noncePreference, null)
        val hasPortableKey = publicKeyPem != null && encryptedPrivateKey != null &&
            nonce != null && store.containsAlias(wrappingAlias)
        if (hasPortableKey) {
            val normalized = PublicKeyCodec.normalizePem(requireNotNull(publicKeyPem))
            return KeyStatus(
                exists = true,
                publicKeyPem = normalized,
                fingerprint = PublicKeyCodec.fingerprint(normalized),
                securityLevel = wrappingKeySecurityLevel(),
                permanentlyInvalidated = permanentlyInvalidated.get()
            )
        }

        // The previous implementation stored a non-exportable RSA key under
        // this alias. It cannot be migrated to the cross-platform OAEP scheme.
        if (store.containsAlias(alias)) {
            val publicKey = store.getCertificate(alias)?.publicKey
            val pem = publicKey?.let(PublicKeyCodec::toPem)
            return KeyStatus(
                exists = true,
                publicKeyPem = pem,
                fingerprint = pem?.let(PublicKeyCodec::fingerprint),
                securityLevel = legacyKeySecurityLevel(),
                requiresRegeneration = true
            )
        }
        return KeyStatus(exists = false)
    }

    fun generate(): KeyStatus {
        check(hasSecureLockScreen()) { "A secure screen lock is required" }
        check(!status().exists) { "A key already exists" }

        try {
            generateWrappingKey(strongBox = true)
        } catch (failure: Throwable) {
            if (failure !is StrongBoxUnavailableException && failure !is ProviderException) {
                throw failure
            }
            deleteWrappingKey()
            generateWrappingKey(strongBox = false)
        }

        return try {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val privateKeyBytes = requireNotNull(pair.private.encoded) {
                "Generated RSA private key is not exportable"
            }
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
            val encryptedPrivateKey = try {
                cipher.doFinal(privateKeyBytes)
            } finally {
                privateKeyBytes.fill(0)
            }
            val publicKeyPem = PublicKeyCodec.toPem(pair.public)
            preferences.edit()
                .putString(publicKeyPreference, publicKeyPem)
                .putString(
                    privateKeyPreference,
                    Base64.getEncoder().encodeToString(encryptedPrivateKey)
                )
                .putString(noncePreference, Base64.getEncoder().encodeToString(cipher.iv))
                .commit()
                .also { check(it) { "Unable to persist encrypted private key" } }
            status().also {
                check(it.securityLevel != null) { "Software-backed wrapping keys are not allowed" }
                // Verify the exact desktop OAEP parameters before exposing the public key.
                val probe = ByteArray(32) { index -> index.toByte() }
                check(probe.contentEquals(RsaOaep.decrypt(RsaOaep.encrypt(probe, pair.public), privateKey()))) {
                    "RSA-OAEP compatibility self-test failed"
                }
            }
        } catch (failure: Throwable) {
            deletePortableKey()
            throw failure
        }
    }

    fun hasSecureLockScreen(): Boolean = keyguardManager.isDeviceSecure

    fun delete() {
        deletePortableKey()
        val keyStore = store
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        permanentlyInvalidated.set(false)
    }

    fun publicKey() = status().publicKeyPem?.let(PublicKeyCodec::parse)
        ?: throw IllegalStateException("No local key")

    fun decryptSessionKey(encryptedBase64: String): SecretKey {
        check(!status().requiresRegeneration) {
            "The legacy Android key must be deleted and regenerated for desktop compatibility"
        }
        val plain = RsaOaep.decrypt(
            Base64.getDecoder().decode(encryptedBase64),
            privateKey()
        )
        require(plain.size == 32) { "Invalid AES-256 session key" }
        return SecretKeySpec(plain, "AES")
    }

    private fun privateKey(): PrivateKey {
        val encrypted = preferences.getString(privateKeyPreference, null)
            ?.let(Base64.getDecoder()::decode)
            ?: throw IllegalStateException("No local private key")
        val nonce = preferences.getString(noncePreference, null)
            ?.let(Base64.getDecoder()::decode)
            ?: throw IllegalStateException("No local private key nonce")
        val plain = try {
            Cipher.getInstance(AES_TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
                doFinal(encrypted)
            }
        } catch (failure: KeyPermanentlyInvalidatedException) {
            permanentlyInvalidated.set(true)
            throw failure
        }
        return try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(plain))
        } finally {
            plain.fill(0)
        }
    }

    private fun generateWrappingKey(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(
            wrappingAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                AUTH_WINDOW_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
            .setInvalidatedByBiometricEnrollment(false)
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun wrappingKey(): SecretKey = store.getKey(wrappingAlias, null) as? SecretKey
        ?: throw IllegalStateException("Private-key wrapping key is missing")

    private fun deletePortableKey() {
        preferences.edit()
            .remove(publicKeyPreference)
            .remove(privateKeyPreference)
            .remove(noncePreference)
            .commit()
        deleteWrappingKey()
    }

    private fun deleteWrappingKey() {
        val keyStore = store
        if (keyStore.containsAlias(wrappingAlias)) keyStore.deleteEntry(wrappingAlias)
    }

    private fun wrappingKeySecurityLevel(): SecurityLevel? {
        val key = wrappingKey()
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        return mapSecurityLevel(info)
    }

    private fun legacyKeySecurityLevel(): SecurityLevel? {
        val privateKey = store.getKey(alias, null) as? PrivateKey ?: return null
        return securityLevel(privateKey)
    }

    private fun securityLevel(key: java.security.Key): SecurityLevel? {
        val info = KeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            .getKeySpec(key, KeyInfo::class.java)
        return mapSecurityLevel(info)
    }

    private fun mapSecurityLevel(info: KeyInfo): SecurityLevel? {
        return when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> SecurityLevel.STRONGBOX
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityLevel.TEE
            else -> null
        }
    }

    private val publicKeyPreference get() = "${alias}_public_pem_v2"
    private val privateKeyPreference get() = "${alias}_private_pkcs8_ciphertext_v2"
    private val noncePreference get() = "${alias}_private_pkcs8_nonce_v2"

    companion object {
        const val KEY_ALIAS = "file_security_tool_rsa_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFERENCES_NAME = "encrypted_rsa_key_material"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val AUTH_WINDOW_SECONDS = 5 * 60
    }
}
