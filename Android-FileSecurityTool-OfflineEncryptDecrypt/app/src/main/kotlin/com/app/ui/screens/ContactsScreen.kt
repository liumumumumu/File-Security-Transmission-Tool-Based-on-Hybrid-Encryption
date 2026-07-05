package com.filesecuritytool.android.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.filesecuritytool.android.R
import com.filesecuritytool.android.data.contact.Contact
import com.filesecuritytool.android.feature.contacts.ContactViewModel
import com.filesecuritytool.android.ui.qr.PublicKeyQrScanner

@Composable
fun ContactsScreen(viewModel: ContactViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<Contact?>(null) }
    var detailTarget by remember { mutableStateOf<Contact?>(null) }
    var replacementTarget by remember { mutableStateOf<Contact?>(null) }
    var scannedReplacementTarget by remember { mutableStateOf<Contact?>(null) }
    var scannerVisible by rememberSaveable { mutableStateOf(false) }
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    val pngPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(viewModel::importPng)
    }
    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        val target = replacementTarget
        replacementTarget = null
        if (it != null && target != null) viewModel.importReplacementPng(it, target)
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scannerVisible = granted
        cameraDenied = !granted
        if (!granted) scannedReplacementTarget = null
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.contacts_tab), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.search_contacts)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { pngPicker.launch("image/png") },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.select_public_key_png)) }
            OutlinedButton(
                onClick = {
                    scannedReplacementTarget = null
                    when {
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED -> scannerVisible = true
                        cameraDenied -> context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                        else -> cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.scan_public_key_qr)) }
        }
        if (state.busy) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.contacts.isEmpty()) {
            Text(stringResource(R.string.no_contacts_yet))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.contacts, key = { it.fingerprint }) { contact ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                contact.fingerprint.chunked(8).joinToString(" "),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row {
                                TextButton(onClick = { detailTarget = contact }) {
                                    Text(stringResource(R.string.contact_details))
                                }
                                TextButton(onClick = { deleteTarget = contact }) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingKey?.let { pending ->
        var name by remember(pending.fingerprint) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissPending,
            title = {
                Text(
                    stringResource(when {
                        pending.updateTarget != null -> R.string.confirm_contact_key_update
                        pending.existingContact == null -> R.string.confirm_public_key
                        else -> R.string.contact_already_exists
                    })
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pending.updateTarget?.let {
                        Text(stringResource(R.string.old_fingerprint))
                        Text(it.fingerprint.chunked(8).joinToString(" "))
                        Text(stringResource(R.string.new_fingerprint))
                    }
                    pending.existingContact?.takeIf { pending.updateTarget == null }?.let {
                        Text(it.displayName)
                    }
                    Text(pending.fingerprint.chunked(8).joinToString(" "))
                    if (pending.updateTarget != null) {
                        Text(stringResource(R.string.contact_key_update_warning))
                    }
                    if (pending.existingContact == null && pending.updateTarget == null) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.contact_display_name)) },
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                if (pending.updateTarget != null) {
                    TextButton(onClick = viewModel::confirmReplacement) {
                        Text(stringResource(R.string.update))
                    }
                } else if (pending.existingContact == null) {
                    TextButton(
                        enabled = name.trim().codePointCount(0, name.trim().length) in 1..40,
                        onClick = { viewModel.savePending(name) }
                    ) { Text(stringResource(R.string.save)) }
                } else {
                    TextButton(onClick = {
                        detailTarget = pending.existingContact
                        viewModel.dismissPending()
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            },
            dismissButton = if (pending.existingContact == null || pending.updateTarget != null) {
                { TextButton(onClick = viewModel::dismissPending) {
                    Text(stringResource(R.string.cancel))
                } }
            } else null
        )
    }

    deleteTarget?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_contact_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_contact_message,
                        contact.displayName,
                        contact.fingerprint.takeLast(8)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(contact)
                    deleteTarget = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    detailTarget?.let { contact ->
        var editedName by remember(contact.fingerprint, contact.displayName) {
            mutableStateOf(contact.displayName)
        }
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text(stringResource(R.string.contact_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text(stringResource(R.string.contact_display_name)) },
                        singleLine = true
                    )
                    Text(stringResource(R.string.fingerprint_label))
                    Text(contact.fingerprint.chunked(8).joinToString(" "))
                    OutlinedButton(onClick = {
                        replacementTarget = contact
                        detailTarget = null
                        replacementPicker.launch("image/png")
                    }) { Text(stringResource(R.string.update_contact_public_key)) }
                    OutlinedButton(onClick = {
                        scannedReplacementTarget = contact
                        detailTarget = null
                        when {
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED -> scannerVisible = true
                            cameraDenied -> context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                            else -> cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text(stringResource(R.string.scan_updated_public_key)) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editedName.trim().codePointCount(
                        0, editedName.trim().length
                    ) in 1..40,
                    onClick = {
                        viewModel.rename(contact, editedName)
                        detailTarget = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { detailTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (scannerVisible) {
        Dialog(onDismissRequest = { scannerVisible = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.scan_public_key_qr),
                        style = MaterialTheme.typography.titleLarge
                    )
                    PublicKeyQrScanner(
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                        onPayload = {
                            scannerVisible = false
                            val replacement = scannedReplacementTarget
                            scannedReplacementTarget = null
                            if (replacement == null) viewModel.acceptScannedPayload(it)
                            else viewModel.acceptScannedReplacementPayload(it, replacement)
                        }
                    )
                    TextButton(onClick = { scannerVisible = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}
