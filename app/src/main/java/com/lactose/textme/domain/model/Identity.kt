package com.lactose.textme.domain.model

data class Identity(
    val id: String,
    val userId: String,
    val publicId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: IdentityStatus,
    val autoRotationEnabled: Boolean = false
) {
    val isExpired: Boolean
        get() = (autoRotationEnabled && System.currentTimeMillis() >= expiresAt) || status != IdentityStatus.ACTIVE

    val remainingTimeMillis: Long
        get() = if (autoRotationEnabled) (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L) else 0L
}

enum class IdentityStatus {
    ACTIVE,
    EXPIRED,
    REVOKED
}
