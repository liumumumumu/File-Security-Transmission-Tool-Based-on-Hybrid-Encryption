package com.filesecuritytool.android.data.contact

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filesecuritytool.android.core.crypto.PublicKeyCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ContactDatabaseTest {
    private lateinit var database: ContactDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ContactDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun uniqueFingerprintPersistsAndSorts() = runBlocking {
        val repository = ContactRepository(database.contacts())
        repository.add("Zulu", publicKey())
        repository.add("Alpha", publicKey())
        assertEquals(
            listOf("Alpha", "Zulu"),
            repository.observe(Locale.ENGLISH).first().map(Contact::displayName)
        )
    }

    private fun publicKey(): String = PublicKeyCodec.toPem(
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
    )
}
