package com.filesecuritytool.android.core.crypto

import com.filesecuritytool.android.crypto.OfflineCryptoService
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

/**
 * Application-facing FST2/FST-TEXT1 engine.
 *
 * The container codec remains isolated behind this class while it is verified
 * against Java-generated vectors. RSA parameters and private-key access are
 * owned by the new core implementation.
 */
class FstCryptoEngine(
    private val keys: HardwareKeyStore,
    private val authentication: AuthenticationSession
) {
    private val taskPreauthorized = ThreadLocal.withInitial { false }
    private val service = OfflineCryptoService(
        rsaEncryptor = { aesKey, receiverPublicKeyPem ->
            Base64.getEncoder().encodeToString(
                RsaOaep.encrypt(aesKey.encoded, PublicKeyCodec.parse(receiverPublicKeyPem))
            )
        },
        rsaDecryptor = { encryptedSessionKey ->
            if (taskPreauthorized.get() != true) authentication.requireValid()
            keys.decryptSessionKey(encryptedSessionKey)
        }
    )

    fun encryptText(text: String, receiverPublicKeyPem: String) =
        service.encryptText(text, PublicKeyCodec.normalizePem(receiverPublicKeyPem))

    fun decryptText(payload: String): OfflineCryptoService.FstTextDecryptResult {
        authentication.requireValid()
        return service.decryptText(payload)
    }

    fun encryptFile(
        input: InputStream,
        fileName: String,
        fileSize: Long,
        receiverPublicKeyPem: String,
        output: OutputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) = service.encryptFile(
        input,
        fileName,
        fileSize,
        PublicKeyCodec.normalizePem(receiverPublicKeyPem),
        output = output,
        onProgress = onProgress,
        isCancelled = isCancelled
    )

    fun decryptFile(
        input: InputStream,
        output: OutputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): OfflineCryptoService.Fst2DecryptResult {
        authentication.requireValid()
        return service.decryptFile(input, output, onProgress, isCancelled)
    }

    fun decryptFileTo(
        input: InputStream,
        outputFactory: (String) -> OutputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
        preauthorizedTask: Boolean = false
    ): OfflineCryptoService.Fst2DecryptResult {
        if (!preauthorizedTask) authentication.requireValid()
        return if (preauthorizedTask) {
            taskPreauthorized.set(true)
            try {
                service.decryptFileTo(input, outputFactory, onProgress, isCancelled)
            } finally {
                taskPreauthorized.remove()
            }
        } else {
            service.decryptFileTo(input, outputFactory, onProgress, isCancelled)
        }
    }

    fun requireAuthentication() = authentication.requireValid()
}
