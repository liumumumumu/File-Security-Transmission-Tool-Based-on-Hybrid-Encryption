package com.filesecuritytool.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.filesecuritytool.android.BuildConfig
import com.filesecuritytool.android.R
import com.filesecuritytool.android.data.settings.AppLanguage
import com.filesecuritytool.android.data.settings.AppSettings
import com.filesecuritytool.android.data.settings.AppSettingsRepository
import com.filesecuritytool.android.data.settings.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: AppSettings, repository: AppSettingsRepository) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(
                    Modifier.horizontalScroll(rememberScrollState())
                ) {
                    AppLanguage.entries.forEachIndexed { index, language ->
                        SegmentedButton(
                            selected = settings.language == language,
                            onClick = { scope.launch { repository.setLanguage(language) } },
                            shape = SegmentedButtonDefaults.itemShape(
                                index, AppLanguage.entries.size
                            )
                        ) {
                            Text(stringResource(
                                if (language == AppLanguage.ENGLISH) R.string.english
                                else R.string.chinese
                            ))
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(
                    Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { repository.setTheme(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                        ) {
                            Text(stringResource(when (mode) {
                                ThemeMode.SYSTEM -> R.string.follow_system
                                ThemeMode.LIGHT -> R.string.light_theme
                                ThemeMode.DARK -> R.string.dark_theme
                            }))
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
                Text("FileSecurityTool ${BuildConfig.VERSION_NAME}")
                Text(stringResource(R.string.app_description))
            }
        }
    }
}
