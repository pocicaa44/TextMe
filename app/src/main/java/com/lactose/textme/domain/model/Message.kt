package com.lactose.textme.domain.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderIdentityId: String,
    val ciphertext: String,
    val nonce: String,
    val createdAt: Long,
    val text: String? = null
)
