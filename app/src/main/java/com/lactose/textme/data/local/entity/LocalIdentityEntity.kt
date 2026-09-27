package com.lactose.textme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identities")
data class LocalIdentityEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val publicId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: String,
    val autoRotationEnabled: Boolean = false
)
