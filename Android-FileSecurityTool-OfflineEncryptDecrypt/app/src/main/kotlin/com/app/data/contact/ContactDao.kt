package com.filesecuritytool.android.data.contact

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts_v2")
    fun observeAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts_v2 WHERE fingerprint = :fingerprint")
    suspend fun find(fingerprint: String): Contact?

    @Upsert
    suspend fun upsert(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)
}
