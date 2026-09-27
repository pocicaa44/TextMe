package com.lactose.textme.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteIdentity(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("public_id") val publicId: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val status: String = "active",
    @SerialName("auto_rotation_enabled") val autoRotationEnabled: Boolean = false
)
