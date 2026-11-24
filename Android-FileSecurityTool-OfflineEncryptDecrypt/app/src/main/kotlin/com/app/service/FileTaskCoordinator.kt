package com.filesecuritytool.android.service

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.filesecuritytool.android.core.crypto.FstCryptoEngine
import com.filesecuritytool.android.core.files.DownloadsOutputStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

sealed interface FileTaskState {
    data object Idle : FileTaskState
    data class Running(
        val operation: Operation,
        val inputName: String,
        val processedBytes: Long,
        val totalBytes: Long
    ) : FileTaskState {
        val progress: Float
            get() = if (totalBytes <= 0) 0f else (processedBytes.toDouble() / totalBytes).toFloat()
    }
    data class Completed(
        val operation: Operation,
        val outputUri: Uri,
        val outputName: String,
        val sizeBytes: Long
    ) : FileTaskState
    data class Failed(val operation: Operation, val message: String) : FileTaskState
    data object Cancelled : FileTaskState
}

enum class Operation { ENCRYPT, DECRYPT }

class FileTaskCoordinator(
    private val resolver: ContentResolver,
    private val crypto: FstCryptoEngine,
    private val outputs: DownloadsOutputStore = DownloadsOutputStore(resolver),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val availableBytes: () -> Long? = { null }
) {
    private val _state = MutableStateFlow<FileTaskState>(FileTaskState.Idle)
    val state: StateFlow<FileTaskState> = _state.asStateFlow()
    private val cancelled = AtomicBoolean(false)

    @Synchronized
    fun startEncrypt(inputUri: Uri, receiverPublicKeyPem: String) {
        ensureNotRunning()
        val metadata = metadata(inputUri)
        ensureSpace(estimatedEncryptedSize(metadata.size))
        cancelled.set(false)
        _state.value = FileTaskState.Running(Operation.ENCRYPT, metadata.name, 0, metadata.size)
        scope.launch {
            var pending: DownloadsOutputStore.PendingOutput? = null
            try {
                pending = outputs.create(DownloadsOutputStore.fst2ArtifactName(), FST2_MIME)
                resolver.openInputStream(inputUri).use { input ->
                    requireNotNull(input) { "Unable to open input file" }
                    crypto.encryptFile(
                        input,
                        metadata.name,
                        metadata.size,
                        receiverPublicKeyPem,
                        pending.stream,
                        ::updateEncryptProgress,
                        cancelled::get
                    )
                }
                outputs.complete(pending)
                _state.value = FileTaskState.Completed(
                    Operation.ENCRYPT, pending.uri, pending.displayName, metadata.size
                )
            } catch (_: CancellationException) {
                pending?.let(outputs::discard)
                _state.value = FileTaskState.Cancelled
            } catch (failure: Throwable) {
                pending?.let(outputs::discard)
                _state.value = FileTaskState.Failed(Operation.ENCRYPT, safeMessage(failure))
            }
        }
    }

    @Synchronized
    fun startDecrypt(inputUri: Uri) {
        ensureNotRunning()
        crypto.requireAuthentication()
        val metadata = metadata(inputUri)
        require(metadata.name.endsWith(".fst2", ignoreCase = true)) { "A .fst2 file is required" }
        ensureSpace(metadata.size)
        cancelled.set(false)
        _state.value = FileTaskState.Running(Operation.DECRYPT, metadata.name, 0, metadata.size)
        scope.launch {
            var pending: DownloadsOutputStore.PendingOutput? = null
            try {
                val result = resolver.openInputStream(inputUri).use { input ->
                    requireNotNull(input) { "Unable to open input file" }
                    crypto.decryptFileTo(
                        input,
                        outputFactory = { authenticatedName ->
                            outputs.create(
                                DownloadsOutputStore.sanitizeFileName(authenticatedName),
                                "application/octet-stream"
                            ).also { pending = it }.stream
                        },
                        onProgress = ::updateDecryptProgress,
                        isCancelled = cancelled::get,
                        preauthorizedTask = true
                    )
                }
                val finalOutput = requireNotNull(pending)
                outputs.complete(finalOutput)
                _state.value = FileTaskState.Completed(
                    Operation.DECRYPT, finalOutput.uri, finalOutput.displayName, result.fileSize
                )
            } catch (_: CancellationException) {
                pending?.let(outputs::discard)
                _state.value = FileTaskState.Cancelled
            } catch (failure: Throwable) {
                pending?.let(outputs::discard)
                _state.value = FileTaskState.Failed(Operation.DECRYPT, "Unable to decrypt")
            }
        }
    }

    fun cancel() {
        if (_state.value is FileTaskState.Running) cancelled.set(true)
    }

    fun clearTerminalState() {
        if (_state.value !is FileTaskState.Running) _state.value = FileTaskState.Idle
    }

    private fun updateEncryptProgress(processed: Long, total: Long) =
        updateProgress(Operation.ENCRYPT, processed, total)

    private fun updateDecryptProgress(processed: Long, total: Long) =
        updateProgress(Operation.DECRYPT, processed, total)

    private fun updateProgress(operation: Operation, processed: Long, total: Long) {
        val running = _state.value as? FileTaskState.Running ?: return
        _state.value = running.copy(
            operation = operation,
            processedBytes = processed,
            totalBytes = total
        )
    }

    private fun ensureNotRunning() {
        check(_state.value !is FileTaskState.Running) { "Another file task is already running" }
    }

    private fun metadata(uri: Uri): Metadata {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: "input"
                    val reportedSize = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                    val size = if (reportedSize >= 0) reportedSize else measure(uri)
                    return Metadata(DownloadsOutputStore.sanitizeFileName(name), size)
                }
            }
        throw IllegalArgumentException("Unable to read input metadata")
    }

    private fun measure(uri: Uri): Long {
        resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length >= 0) return descriptor.length
        }
        return resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open input file" }
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
            }
            total
        }
    }

    private fun safeMessage(failure: Throwable): String =
        if (failure is SecurityException) "File access was denied"
        else failure.message?.take(240) ?: "File task failed"

    private fun ensureSpace(requiredBytes: Long) {
        val available = availableBytes() ?: return
        require(available >= requiredBytes) {
            "Insufficient storage: approximately $requiredBytes bytes are required"
        }
    }

    private fun estimatedEncryptedSize(inputBytes: Long): Long {
        if (inputBytes < 0) return 0
        val blocks = (inputBytes + (1024 * 1024 - 1)) / (1024 * 1024)
        return inputBytes + blocks * 32 + 16 * 1024
    }

    private data class Metadata(val name: String, val size: Long)

    companion object {
        private const val FST2_MIME = "application/octet-stream"
    }
}
