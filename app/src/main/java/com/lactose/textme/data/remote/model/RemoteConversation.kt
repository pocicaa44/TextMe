package com.lactose.textme.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteConversation(
    val id: String = "",
    @SerialName("participant_a") val participantA: String = "",
    @SerialName("participant_b") val participantB: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val status: String = "active",
    @SerialName("terminated_reason") val terminatedReason: String? = null,
    @SerialName("terminated_by") val terminatedBy: String? = null
)
