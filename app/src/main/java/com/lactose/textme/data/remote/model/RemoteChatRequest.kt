package com.lactose.textme.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteChatRequest(
    val id: String = "",
    @SerialName("sender_identity_id") val senderIdentityId: String = "",
    @SerialName("sender_public_id") val senderPublicId: String = "",
    @SerialName("receiver_identity_id") val receiverIdentityId: String = "",
    @SerialName("receiver_public_id") val receiverPublicId: String = "",
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
