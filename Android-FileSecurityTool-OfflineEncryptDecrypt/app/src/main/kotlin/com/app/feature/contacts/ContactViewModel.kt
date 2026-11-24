package com.filesecuritytool.android.feature.contacts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.filesecuritytool.android.core.crypto.PublicKeyQrDecoder
import com.filesecuritytool.android.data.contact.Contact
import com.filesecuritytool.android.data.contact.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class PendingPublicKey(
    val publicKeyPem: String,
    val fingerprint: String,
    val existingContact: Contact? = null,
    val updateTarget: Contact? = null
)

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val query: String = "",
    val pendingKey: PendingPublicKey? = null,
    val busy: Boolean = false,
    val error: String? = null
)

class ContactViewModel(
    private val repository: ContactRepository,
    private val qrDecoder: PublicKeyQrDecoder
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val pending = MutableStateFlow<PendingPublicKey?>(null)
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<ContactsUiState> = combine(
        repository.observe(Locale.getDefault()), query, pending, busy, error
    ) { contacts, search, pendingKey, loading, failure ->
        val normalizedQuery = search.filterNot(Char::isWhitespace)
        val filtered = if (normalizedQuery.isBlank()) contacts else contacts.filter {
            it.displayName.contains(search.trim(), ignoreCase = true) ||
                it.fingerprint.contains(normalizedQuery, ignoreCase = true)
        }
        ContactsUiState(filtered, search, pendingKey, loading, failure)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun importPng(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        busy.value = true
        error.value = null
        runCatching {
            val decoded = qrDecoder.decodePng(uri)
            val existing = repository.find(decoded.fingerprint)
            PendingPublicKey(decoded.publicKeyPem, decoded.fingerprint, existing)
        }.onSuccess { pending.value = it }
            .onFailure { error.value = it.message }
        busy.value = false
    }

    fun importReplacementPng(uri: Uri, target: Contact) = viewModelScope.launch(Dispatchers.IO) {
        busy.value = true
        error.value = null
        runCatching {
            val decoded = qrDecoder.decodePng(uri)
            val owner = repository.find(decoded.fingerprint)
            if (owner != null && owner.fingerprint != target.fingerprint) {
                PendingPublicKey(decoded.publicKeyPem, decoded.fingerprint, owner, null)
            } else {
                PendingPublicKey(decoded.publicKeyPem, decoded.fingerprint, owner, target)
            }
        }.onSuccess { pending.value = it }
            .onFailure { error.value = it.message }
        busy.value = false
    }

    fun acceptScannedPayload(payload: String) = viewModelScope.launch(Dispatchers.Default) {
        busy.value = true
        error.value = null
        runCatching {
            val decoded = qrDecoder.decodePayload(payload)
            val existing = repository.find(decoded.fingerprint)
            PendingPublicKey(decoded.publicKeyPem, decoded.fingerprint, existing)
        }.onSuccess { pending.value = it }
            .onFailure { error.value = it.message }
        busy.value = false
    }

    fun acceptScannedReplacementPayload(payload: String, target: Contact) =
        viewModelScope.launch(Dispatchers.Default) {
            busy.value = true
            error.value = null
            runCatching {
                val decoded = qrDecoder.decodePayload(payload)
                val owner = repository.find(decoded.fingerprint)
                if (owner != null && owner.fingerprint != target.fingerprint) {
                    PendingPublicKey(decoded.publicKeyPem, decoded.fingerprint, owner, null)
                } else {
                    PendingPublicKey(decoded.publicKeyPem, decoded.fingerprint, owner, target)
                }
            }.onSuccess { pending.value = it }
                .onFailure { error.value = it.message }
            busy.value = false
        }

    fun savePending(displayName: String) = viewModelScope.launch(Dispatchers.IO) {
        val key = pending.value ?: return@launch
        if (key.existingContact != null) return@launch
        busy.value = true
        error.value = null
        runCatching { repository.add(displayName, key.publicKeyPem) }
            .onSuccess { pending.value = null }
            .onFailure { error.value = it.message }
        busy.value = false
    }

    fun dismissPending() {
        pending.value = null
    }

    fun confirmReplacement() = viewModelScope.launch(Dispatchers.IO) {
        val key = pending.value ?: return@launch
        val target = key.updateTarget ?: return@launch
        busy.value = true
        error.value = null
        runCatching { repository.replaceKey(target.fingerprint, key.publicKeyPem) }
            .onSuccess { pending.value = null }
            .onFailure { error.value = it.message }
        busy.value = false
    }

    fun rename(contact: Contact, displayName: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { repository.rename(contact.fingerprint, displayName) }
            .onFailure { error.value = it.message }
    }

    fun delete(contact: Contact) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { repository.delete(contact.fingerprint) }
            .onFailure { error.value = it.message }
    }

    class Factory(
        private val repository: ContactRepository,
        private val qrDecoder: PublicKeyQrDecoder
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ContactViewModel(repository, qrDecoder) as T
    }
}
