package com.filesecuritytool.android

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.room.Room
import com.filesecuritytool.android.core.crypto.HardwareKeyStore
import com.filesecuritytool.android.core.crypto.AuthenticationSession
import com.filesecuritytool.android.core.crypto.FstCryptoEngine
import com.filesecuritytool.android.core.crypto.PublicKeyQrExporter
import com.filesecuritytool.android.core.crypto.PublicKeyQrDecoder
import com.filesecuritytool.android.data.contact.ContactDatabase
import com.filesecuritytool.android.data.contact.ContactRepository
import com.filesecuritytool.android.data.settings.AppSettingsRepository
import com.filesecuritytool.android.core.files.DownloadsOutputStore
import com.filesecuritytool.android.service.FileTaskCoordinator

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val outputStore = DownloadsOutputStore(appContext.contentResolver).also {
        runCatching(it::cleanupInterruptedOutputs)
    }

    val contactDatabase: ContactDatabase = Room.databaseBuilder(
        appContext,
        ContactDatabase::class.java,
        "file_security_tool.db"
    ).build()

    val contacts = ContactRepository(contactDatabase.contacts())
    val settings = AppSettingsRepository(appContext)
    val hardwareKeyStore = HardwareKeyStore(appContext)
    val authenticationSession = AuthenticationSession()
    val fstCrypto = FstCryptoEngine(hardwareKeyStore, authenticationSession)
    val fileTasks = FileTaskCoordinator(
        appContext.contentResolver,
        fstCrypto,
        outputStore,
        availableBytes = {
            runCatching {
                StatFs(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ).absolutePath
                ).availableBytes
            }.getOrNull()
        }
    )
    val publicKeyQrExporter = PublicKeyQrExporter(
        outputStore
    )
    val publicKeyQrDecoder = PublicKeyQrDecoder(appContext.contentResolver)
}
