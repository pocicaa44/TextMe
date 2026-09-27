package com.lactose.textme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class LocalMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderIdentityId: String,
    val ciphertext: String,
    val nonce: String,
    val createdAt: Long,
    val decryptedText: String? = null,
    val isRead: Boolean = true
)

