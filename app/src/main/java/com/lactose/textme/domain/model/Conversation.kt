package com.lactose.textme.domain.model

data class Conversation(
    val id: String,
    val participantAId: String,
    val participantAPublicId: String,
    val participantBId: String,
    val participantBPublicId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: ConversationStatus,
    val terminatedReason: String? = null,
    val terminatedBy: String? = null,
    val otherParticipantPublicId: String = "",
    val isPinned: Boolean = false,
    val lastMessageTimestamp: Long? = null,
    val unreadCount: Int = 0
) {
    val displayTimestamp: Long
        get() = lastMessageTimestamp ?: createdAt

    fun getOtherParticipantPublicId(currentPublicId: String): String {
        if (otherParticipantPublicId.isNotEmpty()) return otherParticipantPublicId
        return if (participantAPublicId == currentPublicId) participantBPublicId else participantAPublicId
    }
}

enum class ConversationStatus {
    ACTIVE,
    EXPIRED,
    DELETED
}
