package com.filesecuritytool.android.ui.screens

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.filesecuritytool.android.R
import com.filesecuritytool.android.feature.keys.KeyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KeyManagementScreen(
    viewModel: KeyViewModel,
    authenticateForGeneration: (onAuthenticated: () -> Unit) -> Unit,
    authenticateForDeletion: (onAuthenticated: () -> Unit) -> Unit,
    openSecuritySettings: () -> Unit,
    isFileTaskRunning: Boolean
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var qrPreview by remember(state.exportedUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(state.exportedUri) {
        qrPreview = withContext(Dispatchers.IO) {
            state.exportedUri?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.key_management), style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (state.status.exists) R.string.key_pair_ready else R.string.no_key_pair),
                    style = MaterialTheme.typography.titleMedium
                )
                if (!state.status.exists) {
                    Text(stringResource(R.string.key_missing_hint))
                } else {
                    if (state.status.permanentlyInvalidated) {
                        Text(
                            stringResource(R.string.key_permanently_invalidated),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.status.requiresRegeneration) {
                        Text(
                            stringResource(R.string.legacy_key_requires_regeneration),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.fingerprint_label))
                    Text(state.status.fingerprint.orEmpty().chunked(8).joinToString(" "))
                    Text(
                        when (state.status.securityLevel) {
                            com.filesecuritytool.android.core.crypto.HardwareKeyStore.SecurityLevel.STRONGBOX ->
                                "StrongBox"
                            com.filesecuritytool.android.core.crypto.HardwareKeyStore.SecurityLevel.TEE -> "TEE"
                            null -> ""
                        }
                    )
                }
            }
        }

        if (state.status.exists) {
            OutlinedButton(
                onClick = viewModel::exportPublicKeyQr,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.export_pubkey_qr)) }
            Button(
                onClick = { confirmDelete = true },
                enabled = !state.busy && !isFileTaskRunning,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.delete_key_pair)) }
            if (isFileTaskRunning) {
                Text(
                    stringResource(R.string.key_delete_blocked_by_file_task),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (state.hasSecureLockScreen) {
                Button(
                    onClick = { authenticateForGeneration(viewModel::generate) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.generate_key_pair)) }
            } else {
                Text(stringResource(R.string.secure_lock_required))
                OutlinedButton(
                    onClick = openSecuritySettings,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.open_security_settings)) }
            }
        }

        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.exportedName?.let { name ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.qr_public_saved, "Downloads/FileSecurity/$name"))
                    qrPreview?.let {
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(R.string.public_key_qr_preview),
                            modifier = Modifier.size(240.dp)
                        )
                    }
                    Text(
                        state.status.fingerprint.orEmpty().chunked(8).joinToString(" "),
                        style = MaterialTheme.typography.bodySmall
                    )
                    state.exportedUri?.let { uri ->
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/png")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    clipData = ClipData.newUri(context.contentResolver, name, uri)
                                }
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.open_in_file_manager)) }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    clipData = ClipData.newUri(context.contentResolver, name, uri)
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, context.getString(R.string.share_public_key_qr))
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.share_public_key_qr)) }
                    }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.confirm_delete_key_title)) },
            text = { Text(stringResource(R.string.confirm_delete_key_message)) },
            confirmButton = {
                TextButton(
                    enabled = !isFileTaskRunning,
                    onClick = {
                        if (!isFileTaskRunning) {
                            confirmDelete = false
                            authenticateForDeletion(viewModel::deleteAfterAuthentication)
                        }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
