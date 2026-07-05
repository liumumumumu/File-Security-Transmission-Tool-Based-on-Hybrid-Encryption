package com.filesecuritytool.android.feature.keys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.filesecuritytool.android.core.crypto.HardwareKeyStore
import com.filesecuritytool.android.core.crypto.PublicKeyQrExporter
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KeyUiState(
    val status: HardwareKeyStore.KeyStatus = HardwareKeyStore.KeyStatus(false),
    val hasSecureLockScreen: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val exportedUri: Uri? = null,
    val exportedName: String? = null
)

class KeyViewModel(
    private val keys: HardwareKeyStore,
    private val qrExporter: PublicKeyQrExporter
) : ViewModel() {
    private val _state = MutableStateFlow(KeyUiState())
    val state: StateFlow<KeyUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        runCatching { keys.status() to keys.hasSecureLockScreen() }
            .onSuccess { (status, secure) ->
                _state.value = KeyUiState(status = status, hasSecureLockScreen = secure)
            }
            .onFailure { _state.value = KeyUiState(error = it.message) }
    }

    fun generate() = viewModelScope.launch(Dispatchers.IO) {
        if (_state.value.status.exists || _state.value.busy ||
            !_state.value.hasSecureLockScreen) return@launch
        _state.value = _state.value.copy(busy = true, error = null)
        runCatching(keys::generate)
            .onSuccess {
                _state.value = KeyUiState(status = it, hasSecureLockScreen = true)
            }
            .onFailure {
                _state.value = _state.value.copy(busy = false, error = it.message)
            }
    }

    fun deleteAfterAuthentication() = viewModelScope.launch(Dispatchers.IO) {
        if (_state.value.busy) return@launch
        _state.value = _state.value.copy(busy = true, error = null)
        runCatching(keys::delete)
            .onSuccess {
                _state.value = KeyUiState(hasSecureLockScreen = keys.hasSecureLockScreen())
            }
            .onFailure { _state.value = _state.value.copy(busy = false, error = it.message) }
    }

    fun exportPublicKeyQr() = viewModelScope.launch(Dispatchers.IO) {
        val pem = _state.value.status.publicKeyPem ?: return@launch
        _state.value = _state.value.copy(busy = true, error = null)
        runCatching { qrExporter.export(pem) }
            .onSuccess {
                _state.value = _state.value.copy(
                    busy = false,
                    exportedUri = it.uri,
                    exportedName = it.displayName
                )
            }
            .onFailure {
                _state.value = _state.value.copy(busy = false, error = it.message)
            }
    }

    class Factory(
        private val keys: HardwareKeyStore,
        private val qrExporter: PublicKeyQrExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KeyViewModel(keys, qrExporter) as T
    }
}
