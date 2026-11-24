package com.filesecuritytool.android.data.contact

import com.filesecuritytool.android.core.crypto.PublicKeyCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Locale

class ContactRepositoryTest {
    private val dao = FakeContactDao()
    private var now = 100L
    private val repository = ContactRepository(dao) { now++ }

    @Test
    fun `fingerprint is unique and rescanning returns existing contact`() = runBlocking {
        val pem = publicKey()
        val first = repository.add(" Alice ", pem)
        val second = repository.add("Different name", pem)
        assertSame(first, second)
        assertEquals("Alice", first.displayName)
        assertEquals(1, dao.values.value.size)
    }

    @Test
    fun `display name must contain one to forty Unicode code points`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.add(" ", publicKey()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.add("😀".repeat(41), publicKey()) }
        }
    }

    @Test
    fun `replacement refuses fingerprint owned by another contact`() = runBlocking {
        val first = repository.add("First", publicKey())
        val secondKey = publicKey()
        repository.add("Second", secondKey)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.replaceKey(first.fingerprint, secondKey) }
        }
        Unit
    }

    @Test
    fun `contacts sort by display name then fingerprint`() = runBlocking {
        repository.add("Zulu", publicKey())
        repository.add("Alpha", publicKey())
        val result = repository.observe(Locale.ENGLISH).first()
        assertEquals(listOf("Alpha", "Zulu"), result.map(Contact::displayName))
    }

    private fun publicKey(): String = PublicKeyCodec.toPem(
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
    )

    private class FakeContactDao : ContactDao {
        val values = MutableStateFlow<List<Contact>>(emptyList())
        override fun observeAll(): Flow<List<Contact>> = values
        override suspend fun find(fingerprint: String): Contact? =
            values.value.firstOrNull { it.fingerprint == fingerprint }
        override suspend fun upsert(contact: Contact) {
            values.value = values.value.filterNot {
                it.fingerprint == contact.fingerprint
            } + contact
        }
        override suspend fun delete(contact: Contact) {
            values.value = values.value - contact
        }
    }
}
