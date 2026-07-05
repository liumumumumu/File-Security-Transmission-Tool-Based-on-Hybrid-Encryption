package com.filesecuritytool.android.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.filesecuritytool.android.R
import com.filesecuritytool.android.feature.offline.OfflineSection
import com.filesecuritytool.android.feature.offline.OfflineUiState
import com.filesecuritytool.android.feature.offline.OfflineViewModel
import com.filesecuritytool.android.feature.offline.Recipient
import com.filesecuritytool.android.service.FileTaskState
import com.filesecuritytool.android.service.Operation

@Composable
fun OfflineScreen(
    viewModel: OfflineViewModel,
    authenticateForDecryption: (onAuthenticated: () -> Unit) -> Unit,
    startFileTaskService: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshKey() }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier.padding(8.dp).horizontalScroll(rememberScrollState())
        ) {
            OfflineSection.entries.forEachIndexed { index, section ->
                SegmentedButton(
                    selected = state.section == section,
                    onClick = { viewModel.selectSection(section) },
                    shape = SegmentedButtonDefaults.itemShape(index, OfflineSection.entries.size)
                ) {
                    Text(stringResource(section.titleResource()))
                }
            }
        }
        when (state.section) {
            OfflineSection.FILE_ENCRYPT -> FileEncryptPanel(
                state, viewModel, startFileTaskService
            )
            OfflineSection.FILE_DECRYPT -> FileDecryptPanel(
                state, viewModel, authenticateForDecryption, startFileTaskService
            )
            OfflineSection.TEXT_ENCRYPT -> TextEncryptPanel(state, viewModel)
            OfflineSection.TEXT_DECRYPT -> TextDecryptPanel(
                state, viewModel, authenticateForDecryption
            )
        }
    }
}

@Composable
private fun FileEncryptPanel(
    state: OfflineUiState,
    viewModel: OfflineViewModel,
    startService: () -> Unit
) {
    val context = LocalContext.current
    var file by rememberSaveable { mutableStateOf<Uri?>(null) }
    var fileName by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.fileTask) {
        if (state.fileTask is FileTaskState.Completed &&
            state.fileTask.operation == Operation.ENCRYPT
        ) {
            file = null
            fileName = ""
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        file = uri
        fileName = uri?.displayName(context).orEmpty()
        if (uri != null) viewModel.clearFileResult()
    }
    OperationPanel(
        R.string.file_encrypt, state, viewModel::cancelFileTask, viewModel::clearFileResult
    ) {
        FileChooser(fileName) { picker.launch(arrayOf("*/*")) }
        RecipientChooser(state, viewModel)
        Button(
            onClick = {
                if (viewModel.startFileEncrypt(requireNotNull(file))) startService()
            },
            enabled = file != null && state.selectedRecipient != null &&
                state.fileTask !is FileTaskState.Running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                state.selectedRecipient?.let {
                    stringResource(R.string.encrypt_for_recipient, recipientDisplayName(it))
                } ?: stringResource(R.string.select_receiver_first)
            )
        }
    }
}

@Composable
private fun FileDecryptPanel(
    state: OfflineUiState,
    viewModel: OfflineViewModel,
    authenticate: (() -> Unit) -> Unit,
    startService: () -> Unit
) {
    val context = LocalContext.current
    var file by rememberSaveable { mutableStateOf<Uri?>(null) }
    var fileName by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.externalDecryptUri) {
        state.externalDecryptUri?.let {
            file = it
            fileName = state.externalDecryptName
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        file = uri
        fileName = uri?.displayName(context).orEmpty()
        if (uri != null) viewModel.clearFileResult()
    }
    OperationPanel(
        R.string.file_decrypt, state, viewModel::cancelFileTask, viewModel::clearFileResult
    ) {
        FileChooser(fileName) { picker.launch(arrayOf("application/octet-stream", "*/*")) }
        if (!state.hasLocalKey) Text(stringResource(R.string.no_key_pair_hint))
        Button(
            onClick = {
                authenticate {
                    if (viewModel.startFileDecrypt(requireNotNull(file))) startService()
                }
            },
            enabled = file != null && fileName.endsWith(".fst2", true) &&
                state.hasLocalKey && state.fileTask !is FileTaskState.Running,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.decrypt)) }
    }
}

@Composable
private fun TextEncryptPanel(state: OfflineUiState, viewModel: OfflineViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    OperationPanel(
        R.string.text_encrypt, state, viewModel::cancelFileTask, viewModel::clearFileResult
    ) {
        OutlinedTextField(
            value = state.textEncryptInput,
            onValueChange = viewModel::setTextEncryptInput,
            label = { Text(stringResource(R.string.input_text_max)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
        )
        RecipientChooser(state, viewModel)
        Button(
            onClick = viewModel::encryptText,
            enabled = state.textEncryptInput.toByteArray().size in 1..16_384 &&
                state.selectedRecipient != null && !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                state.selectedRecipient?.let {
                    stringResource(R.string.encrypt_for_recipient, recipientDisplayName(it))
                } ?: stringResource(R.string.select_receiver_first)
            )
        }
        state.textEncryptOutput?.let { output ->
            ResultText(output, state.textEncryptOutputStale)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    clipboard.nativeClipboard.setPrimaryClip(
                        ClipData.newPlainText("FST-TEXT1", output)
                    )
                }) { Text(stringResource(R.string.copy)) }
                TextButton(
                    enabled = !state.textEncryptOutputStale,
                    onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, output)
                        }, context.getString(R.string.share_encrypted_text)))
                    }
                ) { Text(stringResource(R.string.share)) }
            }
        }
        OutlinedButton(onClick = viewModel::clearTextEncrypt) {
            Text(stringResource(R.string.clear))
        }
    }
}

@Composable
private fun TextDecryptPanel(
    state: OfflineUiState,
    viewModel: OfflineViewModel,
    authenticate: (() -> Unit) -> Unit
) {
    val clipboard = LocalClipboard.current
    OperationPanel(
        R.string.text_decrypt, state, viewModel::cancelFileTask, viewModel::clearFileResult
    ) {
        OutlinedTextField(
            value = state.textDecryptInput,
            onValueChange = viewModel::setTextDecryptInput,
            label = { Text(stringResource(R.string.paste_fst_text1)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
        )
        if (!state.hasLocalKey) Text(stringResource(R.string.no_key_pair_hint))
        Button(
            onClick = { authenticate(viewModel::decryptText) },
            enabled = state.textDecryptInput.startsWith("FST-TEXT1:") &&
                state.hasLocalKey && !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.decrypt)) }
        state.textDecryptOutput?.let { output ->
            ResultText(output, false)
            TextButton(onClick = {
                clipboard.nativeClipboard.setPrimaryClip(
                    ClipData.newPlainText("decrypted text", output)
                )
            }) { Text(stringResource(R.string.copy)) }
        }
        OutlinedButton(onClick = viewModel::clearTextDecrypt) {
            Text(stringResource(R.string.clear))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientChooser(state: OfflineUiState, viewModel: OfflineViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var saveTemporary by remember { mutableStateOf(false) }
    val pngPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(viewModel::importTemporaryRecipient)
    }
    Text(stringResource(R.string.receiver_source), style = MaterialTheme.typography.titleMedium)
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = state.selectedRecipient?.let {
                "${recipientDisplayName(it)} · ${it.fingerprint.takeLast(8)}"
            }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.select_contact)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            state.recipients.forEach { recipient ->
                DropdownMenuItem(
                    text = {
                        Text("${recipientDisplayName(recipient)} · ${recipient.fingerprint.takeLast(8)}")
                    },
                    onClick = {
                        viewModel.selectRecipient(recipient.id)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.select_public_key_png)) },
                onClick = {
                    expanded = false
                    pngPicker.launch("image/png")
                }
            )
        }
    }
    state.selectedRecipient?.let {
        Text(
            "${recipientSourceLabel(it.source)} · ${it.fingerprint.takeLast(8)}",
            style = MaterialTheme.typography.bodySmall
        )
        if (it.source == Recipient.Source.TEMPORARY_QR) {
            TextButton(onClick = { saveTemporary = true }) {
                Text(stringResource(R.string.save_as_contact))
            }
        }
    }
    if (saveTemporary) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveTemporary = false },
            title = { Text(stringResource(R.string.save_as_contact)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.contact_display_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.trim().codePointCount(0, name.trim().length) in 1..40,
                    onClick = {
                        viewModel.saveTemporaryRecipient(name)
                        saveTemporary = false
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { saveTemporary = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun OperationPanel(
    title: Int,
    state: OfflineUiState,
    onCancelFileTask: () -> Unit,
    onClearFileTask: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        content()
        if (state.busy) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.decryptionFailed) {
            Text(
                stringResource(R.string.decryption_failed_user_message),
                color = MaterialTheme.colorScheme.error
            )
        }
        FileTaskCard(state.fileTask, onCancelFileTask, onClearFileTask)
    }
}

@Composable
private fun FileChooser(name: String, onChoose: () -> Unit) {
    OutlinedButton(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
        Text(name.ifBlank { stringResource(R.string.tap_to_select_file) })
    }
}

@Composable
private fun FileTaskCard(task: FileTaskState, onCancel: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    when (task) {
        is FileTaskState.Running -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.inputName)
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(task.progress * 100).toInt()}%")
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
        is FileTaskState.Completed -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.outputName)
                Text("Downloads/FileSecurity/${task.outputName}")
                if (task.operation == Operation.DECRYPT) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(task.outputUri, "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }
                    }) { Text(stringResource(R.string.open_file)) }
                }
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                task.outputUri,
                                if (task.operation == Operation.ENCRYPT)
                                    "application/octet-stream" else "*/*"
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }
                }) { Text(stringResource(R.string.open_in_file_manager)) }
                if (task.operation == Operation.ENCRYPT) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, task.outputUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = ClipData.newUri(
                                context.contentResolver, task.outputName, task.outputUri
                            )
                        }, context.getString(R.string.share)))
                    }) { Text(stringResource(R.string.share)) }
                }
                TextButton(onClick = onClear) { Text(stringResource(R.string.dismiss)) }
            }
        }
        is FileTaskState.Failed -> Column {
            Text(
                if (task.operation == Operation.DECRYPT) {
                    stringResource(R.string.decryption_failed_user_message)
                } else task.message,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.dismiss)) }
        }
        FileTaskState.Cancelled -> {
            Text(stringResource(R.string.file_task_cancelled))
            TextButton(onClick = onClear) { Text(stringResource(R.string.dismiss)) }
        }
        FileTaskState.Idle -> Unit
    }
}

@Composable
private fun ResultText(value: String, stale: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(if (stale) R.string.result_outdated else R.string.result)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
    )
}

private fun OfflineSection.titleResource() = when (this) {
    OfflineSection.FILE_ENCRYPT -> R.string.file_encrypt
    OfflineSection.FILE_DECRYPT -> R.string.file_decrypt
    OfflineSection.TEXT_ENCRYPT -> R.string.text_encrypt
    OfflineSection.TEXT_DECRYPT -> R.string.text_decrypt
}

@Composable
private fun recipientDisplayName(recipient: Recipient): String = when (recipient.source) {
    Recipient.Source.SELF -> stringResource(R.string.self_device)
    Recipient.Source.CONTACT -> recipient.name
    Recipient.Source.TEMPORARY_QR -> stringResource(R.string.temporary_qr)
}

@Composable
private fun recipientSourceLabel(source: Recipient.Source): String = when (source) {
    Recipient.Source.SELF -> stringResource(R.string.self_device)
    Recipient.Source.CONTACT -> stringResource(R.string.contact_source)
    Recipient.Source.TEMPORARY_QR -> stringResource(R.string.temporary_qr)
}

private fun Uri.displayName(context: android.content.Context): String =
    context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty()
