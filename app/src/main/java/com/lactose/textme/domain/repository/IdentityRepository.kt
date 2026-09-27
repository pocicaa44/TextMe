package com.lactose.textme.domain.repository

import com.lactose.textme.domain.model.Identity
import kotlinx.coroutines.flow.Flow

interface IdentityRepository {
    fun getActiveIdentityFlow(): Flow<Identity?>
    suspend fun getActiveIdentity(): Identity?
    suspend fun restoreSession(): Result<Identity?>
    suspend fun createIdentity(): Result<Identity>
    suspend fun rotateIdentity(): Result<Identity>
    suspend fun updateAutoRotation(autoRotationEnabled: Boolean): Result<Unit>
}
