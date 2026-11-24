package com.filesecuritytool.android.feature.offline

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.filesecuritytool.android.core.crypto.FstCryptoEngine
import com.filesecuritytool.android.core.crypto.HardwareKeyStore
import com.filesecuritytool.android.core.crypto.PublicKeyQrDecoder
import com.filesecuritytool.android.data.contact.ContactRepository
import com.filesecuritytool.android.service.FileTaskCoordinator
import com.filesecuritytool.android.service.FileTaskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

enum class OfflineSection { FILE_ENCRYPT, FILE_DECRYPT, TEXT_ENCRYPT, TEXT_DECRYPT }

data class Recipient(
    val id: String,
    val name: String,
    val fingerprint: String,
    val publicKeyPem: String,
    val source: Source
) {
    enum class Source { SELF, CONTACT, TEMPORARY_QR }
}

data class OfflineUiState(
    val section: OfflineSection = OfflineSection.FILE_ENCRYPT,
    val recipients: List<Recipient> = emptyList(),
    val selectedRecipientId: String? = null,
    val textEncryptInput: String = "",
    val textEncryptOutput: String? = null,
    val textEncryptOutputStale: Boolean = false,
    val textDecryptInput: String = "",
    val textDecryptOutput: String? = null,
    val externalDecryptUri: Uri? = null,
    val externalDecryptName: String = "",
    val hasLocalKey: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val decryptionFailed: Boolean = false,
    val fileTask: FileTaskState = FileTaskState.Idle
) {
    val selectedRecipient: Recipient?
        get() = recipients.firstOrNull { it.id == selectedRecipientId }
}

class OfflineViewModel(
    private val keys: HardwareKeyStore,
    private val contacts: ContactRepository,
    private val qrDecoder: PublicKeyQrDecoder,
    private val crypto: FstCryptoEngine,
    private val fileTasks: FileTaskCoordinator
) : ViewModel() {
    private val local = MutableStateFlow(LocalState())
    private val contactFlow = contacts.observe(Locale.getDefault())

    val state: StateFlow<OfflineUiState> = combine(
        local, contactFlow, fileTasks.state
    ) { current, savedContacts, fileTask ->
        val recipients = buildList {
            current.self?.let { add(it) }
            addAll(savedContacts.map {
                Recipient(
                    "contact:${it.fingerprint}", it.displayName, it.fingerprint,
                    it.publicKeyPem, Recipient.Source.CONTACT
                )
            })
            current.temporary?.let { temporary ->
                if (none { it.fingerprint == temporary.fingerprint }) add(temporary)
            }
        }
        OfflineUiState(
            section = current.section,
            recipients = recipients,
            selectedRecipientId = current.selectedRecipientId
                ?.takeIf { id -> recipients.any { it.id == id } },
            textEncryptInput = current.textEncryptInput,
            textEncryptOutput = current.textEncryptOutput,
            textEncryptOutputStale = current.textEncryptOutputStale,
            textDecryptInput = current.textDecryptInput,
            textDecryptOutput = current.textDecryptOutput,
            externalDecryptUri = current.externalDecryptUri,
            externalDecryptName = current.externalDecryptName,
            hasLocalKey = current.self != null,
            busy = current.busy,
            error = current.error,
            decryptionFailed = current.decryptionFailed,
            fileTask = fileTask
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflineUiState())

    init { refreshKey() }

    fun refreshKey() = viewModelScope.launch(Dispatchers.IO) {
        val status = runCatching(keys::status).getOrNull()
        local.value = local.value.copy(
            self = status?.takeIf { it.exists && !it.permanentlyInvalidated }?.let {
                Recipient(
                    SELF_ID, "Self", it.fingerprint.orEmpty(), it.publicKeyPem.orEmpty(),
                    Recipient.Source.SELF
                )
            }
        )
    }

    fun selectSection(section: OfflineSection) {
        local.value = local.value.copy(section = section, error = null)
    }

    fun selectRecipient(id: String?) {
        val changed = local.value.selectedRecipientId != id
        local.value = local.value.copy(
            selectedRecipientId = id,
            textEncryptOutputStale = local.value.textEncryptOutput != null && changed
        )
    }

    fun importTemporaryRecipient(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val result = qrDecoder.decodePng(uri)
            Recipient(
                "temporary:${result.fingerprint}", "Temporary QR",
                result.fingerprint, result.publicKeyPem, Recipient.Source.TEMPORARY_QR
            )
        }.onSuccess {
            local.value = local.value.copy(
                temporary = it,
                selectedRecipientId = it.id,
                textEncryptOutputStale = local.value.textEncryptOutput != null,
                error = null
            )
        }.onFailure { local.value = local.value.copy(error = it.message) }
    }

    fun saveTemporaryRecipient(displayName: String) = viewModelScope.launch(Dispatchers.IO) {
        val temporary = local.value.temporary ?: return@launch
        runCatching { contacts.add(displayName, temporary.publicKeyPem) }
            .onSuccess { saved ->
                local.value = local.value.copy(
                    temporary = null,
                    selectedRecipientId = "contact:${saved.fingerprint}",
                    error = null
                )
            }
            .onFailure { local.value = local.value.copy(error = it.message) }
    }

    fun setTextEncryptInput(value: String) {
        local.value = local.value.copy(
            textEncryptInput = value,
            textEncryptOutputStale = local.value.textEncryptOutput != null
        )
    }

    fun encryptText() = viewModelScope.launch(Dispatchers.Default) {
        val recipient = state.value.selectedRecipient ?: return@launch
        local.value = local.value.copy(busy = true, error = null)
        runCatching { crypto.encryptText(local.value.textEncryptInput, recipient.publicKeyPem).payload }
            .onSuccess {
                local.value = local.value.copy(
                    busy = false, textEncryptOutput = it, textEncryptOutputStale = false
                )
            }
            .onFailure { local.value = local.value.copy(busy = false, error = it.message) }
    }

    fun clearTextEncrypt() {
        local.value = local.value.copy(
            textEncryptInput = "", textEncryptOutput = null,
            textEncryptOutputStale = false, selectedRecipientId = null, temporary = null
        )
    }

    fun setTextDecryptInput(value: String) {
        local.value = local.value.copy(
            textDecryptInput = value, textDecryptOutput = null,
            decryptionFailed = false, error = null
        )
    }

    fun prefillTextDecrypt(value: String) {
        local.value = local.value.copy(
            section = OfflineSection.TEXT_DECRYPT,
            textDecryptInput = value,
            textDecryptOutput = null
        )
    }

    fun prefillFileDecrypt(uri: Uri, displayName: String) {
        local.value = local.value.copy(
            section = OfflineSection.FILE_DECRYPT,
            externalDecryptUri = uri,
            externalDecryptName = displayName
        )
    }

    fun decryptText() = viewModelScope.launch(Dispatchers.Default) {
        local.value = local.value.copy(busy = true, error = null)
        runCatching { crypto.decryptText(local.value.textDecryptInput).text }
            .onSuccess { local.value = local.value.copy(busy = false, textDecryptOutput = it) }
            .onFailure {
                local.value = local.value.copy(
                    busy = false, error = null, decryptionFailed = true
                )
                refreshKey()
            }
    }

    fun clearTextDecrypt() {
        local.value = local.value.copy(
            textDecryptInput = "", textDecryptOutput = null, decryptionFailed = false
        )
    }

    fun startFileEncrypt(uri: Uri): Boolean {
        val recipient = state.value.selectedRecipient ?: return false
        return runCatching {
            fileTasks.startEncrypt(uri, recipient.publicKeyPem)
            true
        }.getOrElse {
            local.value = local.value.copy(error = it.message)
            false
        }
    }

    fun startFileDecrypt(uri: Uri): Boolean {
        return runCatching {
            fileTasks.startDecrypt(uri)
            true
        }.getOrElse {
            local.value = local.value.copy(error = it.message)
            false
        }
    }

    fun cancelFileTask() = fileTasks.cancel()
    fun clearFileResult() = fileTasks.clearTerminalState()

    private data class LocalState(
        val section: OfflineSection = OfflineSection.FILE_ENCRYPT,
        val self: Recipient? = null,
        val temporary: Recipient? = null,
        val selectedRecipientId: String? = null,
        val textEncryptInput: String = "",
        val textEncryptOutput: String? = null,
        val textEncryptOutputStale: Boolean = false,
        val textDecryptInput: String = "",
        val textDecryptOutput: String? = null,
        val externalDecryptUri: Uri? = null,
        val externalDecryptName: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        val decryptionFailed: Boolean = false
    )

    class Factory(
        private val keys: HardwareKeyStore,
        private val contacts: ContactRepository,
        private val qrDecoder: PublicKeyQrDecoder,
        private val crypto: FstCryptoEngine,
        private val fileTasks: FileTaskCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OfflineViewModel(keys, contacts, qrDecoder, crypto, fileTasks) as T
    }

    companion object { private const val SELF_ID = "self" }
}
