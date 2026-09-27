package com.lactose.textme.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class LocalConversationEntity(
    @PrimaryKey val id: String,
    val participantAId: String,
    val participantAPublicId: String,
    val participantBId: String,
    val participantBPublicId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: String,
    val terminatedReason: String? = null,
    val terminatedBy: String? = null,
    val otherParticipantPublicId: String = "",
    val isPinned: Boolean = false
)
