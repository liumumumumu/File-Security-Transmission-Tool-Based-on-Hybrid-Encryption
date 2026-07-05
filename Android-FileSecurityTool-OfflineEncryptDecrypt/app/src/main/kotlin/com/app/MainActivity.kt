package com.filesecuritytool.android

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.filesecuritytool.android.R
import com.filesecuritytool.android.ui.navigation.Screen
import com.filesecuritytool.android.ui.screens.ContactsScreen
import com.filesecuritytool.android.ui.screens.KeyManagementScreen
import com.filesecuritytool.android.ui.screens.OfflineScreen
import com.filesecuritytool.android.ui.screens.SettingsScreen
import com.filesecuritytool.android.ui.theme.AppTheme
import com.filesecuritytool.android.feature.contacts.ContactViewModel
import com.filesecuritytool.android.feature.keys.KeyViewModel
import com.filesecuritytool.android.service.FileTaskState
import com.filesecuritytool.android.feature.offline.OfflineViewModel
import com.filesecuritytool.android.service.FileTaskService
import com.filesecuritytool.android.data.settings.AppSettings
import com.filesecuritytool.android.data.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {
    private val incomingIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingIntent.value = intent

        enableEdgeToEdge()

        setContent {
            val container = (application as FileSecurityToolApplication).container
            val appSettings by container.settings.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            val darkTheme = when (appSettings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AppTheme(darkTheme = darkTheme) {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }

                val offlineVM: OfflineViewModel = viewModel(
                    factory = OfflineViewModel.Factory(
                        container.hardwareKeyStore,
                        container.contacts,
                        container.publicKeyQrDecoder,
                        container.fstCrypto,
                        container.fileTasks
                    )
                )
                val contactVM: ContactViewModel = viewModel(
                    factory = ContactViewModel.Factory(
                        container.contacts,
                        container.publicKeyQrDecoder
                    )
                )
                val keyVM: KeyViewModel = viewModel(
                    factory = KeyViewModel.Factory(
                        container.hardwareKeyStore,
                        container.publicKeyQrExporter
                    )
                )
                val keyUi by keyVM.state.collectAsStateWithLifecycle()
                val fileTaskState by container.fileTasks.state.collectAsStateWithLifecycle()
                val receivedIntent by incomingIntent.collectAsStateWithLifecycle()
                LaunchedEffect(receivedIntent) {
                    receivedIntent?.let { incoming ->
                        when {
                            incoming.action == Intent.ACTION_VIEW && incoming.data != null -> {
                                val uri = requireNotNull(incoming.data)
                                offlineVM.prefillFileDecrypt(
                                    uri,
                                    uriDisplayName(uri)
                                )
                                selectedTab = 0
                            }
                            incoming.action == Intent.ACTION_SEND &&
                                incoming.type == "text/plain" -> {
                                incoming.getStringExtra(Intent.EXTRA_TEXT)
                                    ?.takeIf { it.trim().startsWith("FST-TEXT1:") }
                                    ?.let {
                                        offlineVM.prefillTextDecrypt(it)
                                        selectedTab = 0
                                    }
                            }
                        }
                    }
                    incomingIntent.value = null
                }
                val app = application as FileSecurityToolApplication
                var noKeyDismissed by remember {
                    mutableStateOf(app.noKeyBannerDismissed)
                }
                var explainNotifications by remember { mutableStateOf(false) }
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                val launchFileTaskService = {
                    FileTaskService.start(this@MainActivity)
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        explainNotifications = true
                    }
                }
                LaunchedEffect(keyUi.status.exists) {
                    if (keyUi.status.exists) noKeyDismissed = false
                }
                val screenContent: @Composable () -> Unit = {
                    Column(Modifier.fillMaxSize()) {
                        if (!keyUi.status.exists && !noKeyDismissed) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp)) {
                                    TextButton(
                                        onClick = { selectedTab = 2 },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(stringResource(R.string.no_local_key_banner)) }
                                    TextButton(onClick = {
                                        noKeyDismissed = true
                                        app.noKeyBannerDismissed = true
                                    }) {
                                        Text(stringResource(R.string.dismiss))
                                    }
                                }
                            }
                        }
                        (fileTaskState as? FileTaskState.Running)?.let { task ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(onClick = { selectedTab = 0 }) {
                                    Text(
                                        stringResource(
                                            R.string.view_current_file_task,
                                            (task.progress * 100).toInt()
                                        )
                                    )
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            when (selectedTab) {
                                0 -> OfflineScreen(
                                    viewModel = offlineVM,
                                    authenticateForDecryption = { action ->
                                        if (container.authenticationSession.isValid()) {
                                            action()
                                        } else {
                                            authenticateForDecryption {
                                                container.authenticationSession.grant()
                                                action()
                                            }
                                        }
                                    },
                                    startFileTaskService = launchFileTaskService
                                )
                                1 -> ContactsScreen(viewModel = contactVM)
                                2 -> KeyManagementScreen(
                                    viewModel = keyVM,
                                    authenticateForGeneration = { generateKey ->
                                        authenticateForKeyGeneration(generateKey)
                                    },
                                    authenticateForDeletion = { deleteKey ->
                                        authenticateForKeyDeletion {
                                            if (container.fileTasks.state.value !is FileTaskState.Running) {
                                                deleteKey()
                                            }
                                        }
                                    },
                                    openSecuritySettings = {
                                        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                    },
                                    isFileTaskRunning = fileTaskState is FileTaskState.Running
                                )
                                3 -> SettingsScreen(appSettings, container.settings)
                            }
                        }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= 600.dp) {
                        Row(Modifier.fillMaxSize()) {
                            NavigationRail {
                                Screen.bottomNavItems.forEachIndexed { index, screen ->
                                    NavigationRailItem(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        icon = {
                                            Icon(
                                                if (selectedTab == index) screen.selectedIcon
                                                else screen.unselectedIcon,
                                                contentDescription = stringResource(screen.titleResId)
                                            )
                                        },
                                        label = { Text(stringResource(screen.titleResId)) }
                                    )
                                }
                            }
                            Surface(Modifier.weight(1f)) { screenContent() }
                        }
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                        NavigationBar {
                            Screen.bottomNavItems.forEachIndexed { index, screen ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == index) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = stringResource(screen.titleResId)
                                        )
                                    },
                                    label = { Text(stringResource(screen.titleResId)) }
                                )
                            }
                        }
                            }
                        ) { innerPadding ->
                            Surface(
                                Modifier.fillMaxSize().padding(innerPadding)
                            ) { screenContent() }
                        }
                    }
                }
                if (explainNotifications) {
                    AlertDialog(
                        onDismissRequest = { explainNotifications = false },
                        title = { Text(stringResource(R.string.notification_permission_title)) },
                        text = { Text(stringResource(R.string.notification_permission_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                explainNotifications = false
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationPermission.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            }) { Text(stringResource(R.string.continue_label)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { explainNotifications = false }) {
                                Text(stringResource(R.string.not_now))
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntent.value = intent
    }

    private fun uriDisplayName(uri: android.net.Uri): String =
        contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: uri.lastPathSegment.orEmpty()

    private fun authenticateForKeyDeletion(onAuthenticated: () -> Unit) {
        authenticate(
            getString(R.string.confirm_delete_key_title),
            getString(R.string.confirm_delete_key_message),
            onAuthenticated
        )
    }

    private fun authenticateForKeyGeneration(onAuthenticated: () -> Unit) {
        authenticate(
            getString(R.string.authenticate_to_generate_key),
            getString(R.string.authentication_window_message),
            onAuthenticated
        )
    }

    private fun authenticateForDecryption(onAuthenticated: () -> Unit) {
        authenticate(
            getString(R.string.authenticate_to_decrypt),
            getString(R.string.authentication_window_message),
            onAuthenticated
        )
    }

    private fun authenticate(title: String, subtitle: String, onAuthenticated: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthenticated()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}
