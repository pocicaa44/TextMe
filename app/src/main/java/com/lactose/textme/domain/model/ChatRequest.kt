package com.lactose.textme.domain.model

data class ChatRequest(
    val id: String,
    val senderIdentityId: String,
    val senderPublicId: String,
    val receiverIdentityId: String,
    val receiverPublicId: String = "",
    val status: RequestStatus,
    val createdAt: Long,
    val updatedAt: Long
)

enum class RequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
