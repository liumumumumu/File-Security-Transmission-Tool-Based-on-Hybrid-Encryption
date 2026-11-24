package com.filesecuritytool.android

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.filesecuritytool.android.data.settings.AppSettingsRepository

class FileSecurityToolApplication : Application() {
    var noKeyBannerDismissed: Boolean = false
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        val language = getSharedPreferences(
            AppSettingsRepository.BOOT_PREFS,
            MODE_PRIVATE
        ).getString(AppSettingsRepository.BOOT_LANGUAGE, "en") ?: "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.authenticationSession.clear()
            }
        })
    }
}
