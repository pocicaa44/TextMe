package com.lactose.textme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_requests")
data class LocalChatRequestEntity(
    @PrimaryKey val id: String,
    val senderIdentityId: String,
    val senderPublicId: String,
    val receiverIdentityId: String,
    val receiverPublicId: String = "",
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
