package com.filesecuritytool.android.data.contact

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts_v2")
data class Contact(
    @PrimaryKey val fingerprint: String,
    val displayName: String,
    val publicKeyPem: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
