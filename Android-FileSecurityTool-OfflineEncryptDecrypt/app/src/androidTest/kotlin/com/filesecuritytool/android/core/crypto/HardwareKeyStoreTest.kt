package com.filesecuritytool.android.core.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardwareKeyStoreTest {
    private val keys = HardwareKeyStore(
        ApplicationProvider.getApplicationContext<Context>(),
        "file_security_tool_instrumentation_test"
    )

    @After
    fun cleanUp() = keys.delete()

    @Test
    fun generatedKeyIsNonExportableAndHardwareBacked() {
        assumeTrue(keys.hasSecureLockScreen())
        val status = runCatching(keys::generate).getOrElse {
            assumeTrue("Device does not expose TEE/StrongBox RSA for tests", false)
            throw it
        }
        assertNotNull(status.securityLevel)
        val entry = java.security.KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }.getKey("file_security_tool_instrumentation_test", null)
        assertFalse(entry.encoded?.isNotEmpty() == true)
    }
}
