package com.filesecuritytool.android.core.crypto

import com.filesecuritytool.android.crypto.OfflineCryptoService
import com.fst.protocol.FstMulti
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

    private fun configured() {
        val status = keys.status()
        check(status.exists && !status.permanentlyInvalidated && !status.requiresRegeneration) { "Create or repair your local key first" }
    }

    private fun unwrap(bytes: ByteArray) = keys.decryptSessionKey(Base64.getEncoder().encodeToString(bytes)).encoded

    fun encryptMultiText(text: String, recipients: List<String>): String {
        configured()
        return FstMulti.encryptText(text, recipients.map(PublicKeyCodec::parse))
    }

    fun encryptMultiFile(input: InputStream, name: String, size: Long, recipients: List<String>, output: OutputStream,
                         onProgress: (Long,Long) -> Unit, cancelled: () -> Boolean) {
        configured()
        FstMulti.encryptFile(input, output, size, name, recipients.map(PublicKeyCodec::parse)) { done, total ->
            if (cancelled()) throw java.util.concurrent.CancellationException()
            onProgress(done,total)
        }
    }

    fun encryptText(text: String, receiverPublicKeyPem: String) =
        service.encryptText(text, PublicKeyCodec.normalizePem(receiverPublicKeyPem))

    fun decryptText(payload: String): OfflineCryptoService.FstTextDecryptResult {
        authentication.requireValid()
        if (payload.trim().startsWith(FstMulti.TEXT)) {
            val plain = FstMulti.decryptText(payload, keys.publicKey(), ::unwrap)
            return OfflineCryptoService.FstTextDecryptResult(true, plain, plain.toByteArray().size)
        }
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
        // The caller owns this stream; decryptFileTo closes the stream returned by its factory.
        return decryptFileTo(input, { object : java.io.FilterOutputStream(output) {
            override fun close() = flush()
        } }, onProgress, isCancelled)
    }

    fun decryptFileTo(
        input: InputStream,
        outputFactory: (String) -> OutputStream,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
        preauthorizedTask: Boolean = false
    ): OfflineCryptoService.Fst2DecryptResult {
        if (!preauthorizedTask) authentication.requireValid()
        val source = input.buffered()
        source.mark(4)
        val magic = ByteArray(4)
        java.io.DataInputStream(source).readFully(magic)
        source.reset()
        if (magic.contentEquals("FST3".toByteArray())) {
            var name = ""
            val size = FstMulti.decryptFile(source, keys.publicKey(), ::unwrap, {
                name = it
                outputFactory(it)
            }) { done,total ->
                if (isCancelled()) throw java.util.concurrent.CancellationException()
                onProgress(done,total)
            }
            return OfflineCryptoService.Fst2DecryptResult(true,name,size,((size+1048575)/1048576).toInt())
        }
        return if (preauthorizedTask) {
            taskPreauthorized.set(true)
            try {
                service.decryptFileTo(source, outputFactory, onProgress, isCancelled)
            } finally {
                taskPreauthorized.remove()
            }
        } else {
            service.decryptFileTo(source, outputFactory, onProgress, isCancelled)
        }
    }

    fun requireAuthentication() = authentication.requireValid()
}
