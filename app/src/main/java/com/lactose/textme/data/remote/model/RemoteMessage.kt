package com.lactose.textme.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteMessage(
    val id: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("sender_identity_id") val senderIdentityId: String = "",
    val ciphertext: String = "",
    val nonce: String = "",
    @SerialName("created_at") val createdAt: String? = null
)
