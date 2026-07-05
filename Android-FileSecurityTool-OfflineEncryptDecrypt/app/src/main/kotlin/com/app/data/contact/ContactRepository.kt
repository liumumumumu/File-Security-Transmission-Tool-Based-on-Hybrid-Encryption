package com.filesecuritytool.android.data.contact

import com.filesecuritytool.android.core.crypto.PublicKeyCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale

class ContactRepository(
    private val dao: ContactDao,
    private val now: () -> Long = System::currentTimeMillis
) {
    fun observe(locale: Locale): Flow<List<Contact>> {
        val collator = Collator.getInstance(locale)
        return dao.observeAll().map { contacts ->
            contacts.sortedWith { left, right ->
                collator.compare(left.displayName, right.displayName)
                    .takeIf { it != 0 }
                    ?: left.fingerprint.compareTo(right.fingerprint)
            }
        }
    }

    suspend fun add(displayName: String, publicKeyText: String): Contact {
        val name = validateName(displayName)
        val pem = PublicKeyCodec.normalizePem(publicKeyText)
        val fingerprint = PublicKeyCodec.fingerprint(pem)
        dao.find(fingerprint)?.let { return it }
        val timestamp = now()
        val contact = Contact(fingerprint, name, pem, timestamp, timestamp)
        dao.upsert(contact)
        return contact
    }

    suspend fun find(fingerprint: String): Contact? = dao.find(fingerprint)

    suspend fun rename(fingerprint: String, displayName: String): Contact {
        val existing = dao.find(fingerprint) ?: throw IllegalArgumentException("Contact not found")
        val updated = existing.copy(
            displayName = validateName(displayName),
            updatedAtEpochMs = now()
        )
        dao.upsert(updated)
        return updated
    }

    suspend fun replaceKey(oldFingerprint: String, publicKeyText: String): Contact {
        val existing = dao.find(oldFingerprint) ?: throw IllegalArgumentException("Contact not found")
        val pem = PublicKeyCodec.normalizePem(publicKeyText)
        val newFingerprint = PublicKeyCodec.fingerprint(pem)
        if (newFingerprint != oldFingerprint && dao.find(newFingerprint) != null) {
            throw IllegalArgumentException("This public key already belongs to another contact")
        }
        if (newFingerprint != oldFingerprint) dao.delete(existing)
        val updated = existing.copy(
            fingerprint = newFingerprint,
            publicKeyPem = pem,
            updatedAtEpochMs = now()
        )
        dao.upsert(updated)
        return updated
    }

    suspend fun delete(fingerprint: String) {
        val existing = dao.find(fingerprint) ?: throw IllegalArgumentException("Contact not found")
        dao.delete(existing)
    }

    private fun validateName(value: String): String {
        val normalized = value.trim()
        require(normalized.codePointCount(0, normalized.length) in 1..40) {
            "Display name must contain 1 to 40 characters"
        }
        return normalized
    }
}
