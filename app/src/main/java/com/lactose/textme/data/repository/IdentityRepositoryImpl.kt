package com.lactose.textme.data.repository

import android.util.Log
import com.lactose.textme.core.common.Constants
import com.lactose.textme.core.network.SupabaseNetworkClient
import com.lactose.textme.data.local.dao.ChatRequestDao
import com.lactose.textme.data.local.dao.ConversationDao
import com.lactose.textme.data.local.dao.IdentityDao
import com.lactose.textme.data.local.entity.LocalIdentityEntity
import com.lactose.textme.data.remote.model.RemoteIdentity
import com.lactose.textme.domain.model.Identity
import com.lactose.textme.domain.model.IdentityStatus
import com.lactose.textme.domain.repository.IdentityRepository
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class IdentityRepositoryImpl(
    private val identityDao: IdentityDao,
    private val conversationDao: ConversationDao? = null,
    private val chatRequestDao: ChatRequestDao? = null
) : IdentityRepository {

    override fun getActiveIdentityFlow(): Flow<Identity?> {
        return identityDao.getActiveIdentityFlow().map { entity ->
            entity?.toDomainModel()
        }
    }

    override suspend fun getActiveIdentity(): Identity? = withContext(Dispatchers.IO) {
        val existingLocal = identityDao.getActiveIdentity()
        if (existingLocal != null && !existingLocal.toDomainModel().isExpired) {
            return@withContext existingLocal.toDomainModel()
        }
        val sessionResult = restoreSession()
        sessionResult.getOrNull()
    }

    override suspend fun restoreSession(): Result<Identity?> = withContext(Dispatchers.IO) {
        if (!SupabaseNetworkClient.isConfigured()) {
            val err = IllegalStateException("Supabase configuration is missing or invalid. Check local.properties.")
            Log.e(TAG, "[Startup] Configuration check failed: ${err.message}")
            return@withContext Result.failure(err)
        }

        try {
            SupabaseNetworkClient.awaitAuthInitialized()
        } catch (e: Exception) {
            Log.e(TAG, "[Auth] Error during auth initialization", e)
            return@withContext Result.failure(e)
        }

        val user = SupabaseNetworkClient.auth.currentUserOrNull()
        if (user == null) {
            Log.d(TAG, "[Auth] No saved session found. User is unauthenticated (first launch).")
            return@withContext Result.success(null)
        }

        Log.d(TAG, "[Auth] Restored session for user ${user.id}. Checking canonical identity on Supabase...")
        var existingLocal = identityDao.getActiveIdentity()
        if (existingLocal != null && existingLocal.userId != user.id) {
            Log.w(TAG, "[Auth] Local cache userId (${existingLocal.userId}) != current user (${user.id}). Purging local cache.")
            identityDao.clearAll()
            existingLocal = null
        }

        // Query Supabase for canonical identity
        try {
            val remoteList = SupabaseNetworkClient.postgrest["identities"]
                .select {
                    filter {
                        eq("user_id", user.id)
                        eq("status", "active")
                    }
                }
                .decodeList<RemoteIdentity>()

            val canonicalRemote = remoteList.firstOrNull { remote ->
                val expiresMs = parseIsoTimestamp(remote.expiresAt)
                expiresMs > System.currentTimeMillis()
            }

            if (canonicalRemote != null && canonicalRemote.publicId.isNotEmpty()) {
                val canonicalEntity = canonicalRemote.toLocalEntity(
                    overrideAutoRotation = existingLocal?.autoRotationEnabled
                )
                if (existingLocal == null || existingLocal.publicId != canonicalEntity.publicId) {
                    Log.d(TAG, "[Profile] Updating local Room cache with Supabase canonical Public ID: ${canonicalEntity.publicId}")
                    identityDao.clearAll()
                    identityDao.insertIdentity(canonicalEntity)
                }
                return@withContext Result.success(canonicalEntity.toDomainModel())
            } else {
                Log.w(TAG, "[Profile] Authenticated user ${user.id} has no active identity on Supabase. Recovering/creating...")
                return@withContext createIdentity()
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("42501") || errorMsg.contains("permission denied for schema public")) {
                Log.e(TAG, "[Profile] RLS / Permission denied (42501). Database schema grants required.", e)
                return@withContext Result.failure(
                    IllegalStateException("Database permission denied (42501). Run the grants in Supabase SQL editor.")
                )
            }
            Log.e(TAG, "[Profile] Failed to fetch active identity from Supabase: $errorMsg", e)

            // Fallback to local Room cache if available and not expired (offline mode)
            if (existingLocal != null && !existingLocal.toDomainModel().isExpired) {
                Log.d(TAG, "[Profile] Offline fallback: Using cached active identity ${existingLocal.publicId}")
                return@withContext Result.success(existingLocal.toDomainModel())
            }

            return@withContext Result.failure(e)
        }
    }

    override suspend fun createIdentity(): Result<Identity> = withContext(Dispatchers.IO) {
        if (!SupabaseNetworkClient.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase configuration is missing. Check local.properties."))
        }

        val authResult = ensureAnonymousSession()
        val user = authResult.getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val existingLocal = identityDao.getActiveIdentity()
        if (existingLocal != null && existingLocal.userId != user.id) {
            identityDao.clearAll()
        }

        val initialAutoRotation = existingLocal?.autoRotationEnabled ?: false

        // 1. Check if active identity already exists on Supabase (e.g. created by trigger or concurrent call)
        try {
            val remoteList = SupabaseNetworkClient.postgrest["identities"]
                .select {
                    filter {
                        eq("user_id", user.id)
                        eq("status", "active")
                    }
                }
                .decodeList<RemoteIdentity>()

            val existingRemote = remoteList.firstOrNull { remote ->
                val expiresMs = parseIsoTimestamp(remote.expiresAt)
                expiresMs > System.currentTimeMillis()
            }

            if (existingRemote != null && existingRemote.publicId.isNotEmpty()) {
                val localEntity = existingRemote.toLocalEntity(
                    overrideAutoRotation = initialAutoRotation
                )
                identityDao.clearAll()
                identityDao.insertIdentity(localEntity)
                Log.d(TAG, "[Profile] Found existing active identity on Supabase: publicId=${localEntity.publicId}")
                return@withContext Result.success(localEntity.toDomainModel())
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("42501") || msg.contains("permission denied for schema public")) {
                Log.e(TAG, "[Profile] Database permission denied (42501) checking existing identity.", e)
                return@withContext Result.failure(
                    IllegalStateException("Database permission denied (42501). Run the grants in Supabase SQL editor.")
                )
            }
            Log.w(TAG, "[Profile] Could not check existing remote identity before creation: ${e.message}")
        }

        // 2. Call RPC get_or_create_active_identity as primary server function
        try {
            Log.d(TAG, "[Profile] Calling RPC get_or_create_active_identity for user ${user.id}...")
            val remoteIdentities = SupabaseNetworkClient.postgrest.rpc(
                function = "get_or_create_active_identity",
                parameters = buildJsonObject { put("p_user_id", user.id) }
            ).decodeList<RemoteIdentity>()

            val canonicalRemote = remoteIdentities.firstOrNull()
            if (canonicalRemote != null && canonicalRemote.publicId.isNotEmpty()) {
                Log.d(TAG, "[Profile] SUCCESS! Canonical Public ID (${canonicalRemote.publicId}) from RPC.")
                val localEntity = canonicalRemote.toLocalEntity(
                    overrideAutoRotation = initialAutoRotation
                )
                identityDao.clearAll()
                identityDao.insertIdentity(localEntity)
                return@withContext Result.success(localEntity.toDomainModel())
            }
        } catch (rpcErr: Exception) {
            val msg = rpcErr.message ?: ""
            if (msg.contains("42501") || msg.contains("permission denied for schema public")) {
                Log.e(TAG, "[Profile] RPC permission denied (42501). Database schema grants required.", rpcErr)
                return@withContext Result.failure(
                    IllegalStateException("Database permission denied (42501). Run the grants in Supabase SQL editor.")
                )
            }
            Log.w(TAG, "[Profile] RPC get_or_create_active_identity failed, attempting direct insert", rpcErr)
        }

        // 3. Fallback: Revoke old active identity and perform direct insert into Supabase identities table
        try {
            SupabaseNetworkClient.postgrest["identities"].update(
                mapOf("status" to "revoked")
            ) {
                filter {
                    eq("user_id", user.id)
                    eq("status", "active")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Profile] Note: Failed to revoke previous active identity on Supabase: ${e.message}")
        }

        try {
            Log.d(TAG, "[Profile] Requesting server-generated Public ID from Supabase identities table...")
            val remoteIdentity = SupabaseNetworkClient.postgrest["identities"]
                .insert(
                    mapOf(
                        "user_id" to user.id,
                        "status" to "active",
                        "auto_rotation_enabled" to initialAutoRotation
                    )
                ) {
                    select()
                }
                .decodeSingle<RemoteIdentity>()

            Log.d(TAG, "[Profile] SUCCESS! Canonical Public ID (${remoteIdentity.publicId}) generated by Supabase.")
            val localEntity = remoteIdentity.toLocalEntity(
                overrideAutoRotation = initialAutoRotation
            )
            identityDao.clearAll()
            identityDao.insertIdentity(localEntity)
            return@withContext Result.success(localEntity.toDomainModel())
        } catch (e: Exception) {
            val msg = e.message ?: ""
            Log.e(TAG, "[Profile] CRITICAL ERROR: Failed to obtain server-generated Public ID from Supabase!", e)
            if (msg.contains("42501") || msg.contains("permission denied for schema public")) {
                return@withContext Result.failure(
                    IllegalStateException("Database permission denied (42501). Run the grants in Supabase SQL editor.")
                )
            }
            return@withContext Result.failure(e)
        }
    }

    override suspend fun rotateIdentity(): Result<Identity> = withContext(Dispatchers.IO) {
        try {
            val authResult = ensureAnonymousSession()
            if (authResult.isFailure) {
                return@withContext Result.failure(authResult.exceptionOrNull()!!)
            }

            val current = identityDao.getActiveIdentity()
            if (current != null) {
                identityDao.updateStatus(current.id, IdentityStatus.REVOKED.name.lowercase())
                try {
                    SupabaseNetworkClient.postgrest["identities"].update(
                        mapOf("status" to "revoked")
                    ) {
                        filter {
                            eq("id", current.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[Profile] Failed to revoke old identity on Supabase", e)
                }

                try {
                    SupabaseNetworkClient.postgrest["conversations"].update(
                        mapOf(
                            "status" to "expired",
                            "terminated_reason" to "identity_rotated",
                            "terminated_by" to current.id
                        )
                    ) {
                        filter {
                            or {
                                eq("participant_a", current.id)
                                eq("participant_b", current.id)
                            }
                            eq("status", "active")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[Profile] Note: Failed to update conversations on Supabase during rotation: ${e.message}")
                }

                conversationDao?.markConversationsTerminatedByIdentity(
                    identityId = current.id,
                    reason = "identity_rotated",
                    terminatedBy = current.id
                )

                try {
                    SupabaseNetworkClient.postgrest["chat_requests"].update(
                        mapOf("status" to "cancelled")
                    ) {
                        filter {
                            or {
                                eq("sender_identity_id", current.id)
                                eq("receiver_identity_id", current.id)
                            }
                            eq("status", "pending")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[Profile] Note: Failed to cancel chat requests on Supabase during rotation: ${e.message}")
                }

                chatRequestDao?.markAllPendingRequestsCancelledForIdentity(
                    identityId = current.id,
                    status = "cancelled",
                    updatedAt = System.currentTimeMillis()
                )
            }
            identityDao.clearAll()
            createIdentity()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateAutoRotation(autoRotationEnabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val active = identityDao.getActiveIdentity()
            ?: return@withContext Result.failure(IllegalStateException("No active identity"))

        try {
            val newExpiresAt = if (autoRotationEnabled) {
                System.currentTimeMillis() + Constants.IDENTITY_LIFESPAN_MS
            } else {
                active.expiresAt
            }

            if (autoRotationEnabled) {
                identityDao.updateAutoRotationWithExpiration(active.id, true, newExpiresAt)
            } else {
                identityDao.updateAutoRotation(active.id, false)
            }

            try {
                ensureAnonymousSession()
                val updatePayload = if (autoRotationEnabled) {
                    mapOf(
                        "auto_rotation_enabled" to true,
                        "expires_at" to Instant.ofEpochMilli(newExpiresAt).toString()
                    )
                } else {
                    mapOf("auto_rotation_enabled" to false)
                }

                SupabaseNetworkClient.postgrest["identities"].update(
                    updatePayload
                ) {
                    filter {
                        eq("id", active.id)
                    }
                }
                Log.d(TAG, "[Profile] Updated auto_rotation_enabled on Supabase: $autoRotationEnabled, expiresAt: $newExpiresAt")
            } catch (e: Exception) {
                Log.e(TAG, "[Profile] Failed to update auto_rotation_enabled on Supabase", e)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun ensureAnonymousSession(): Result<UserInfo> = authMutex.withLock {
        SupabaseNetworkClient.awaitAuthInitialized()
        var currentUser = SupabaseNetworkClient.auth.currentUserOrNull()

        if (currentUser != null) {
            Log.d(TAG, "[Auth] Active Supabase auth session found: ${currentUser.id}")
            return@withLock Result.success(currentUser)
        }

        Log.d(TAG, "[Auth] Attempting Supabase anonymous sign in...")
        return@withLock try {
            SupabaseNetworkClient.auth.signInAnonymously()
            currentUser = SupabaseNetworkClient.auth.currentUserOrNull()
            if (currentUser != null) {
                Log.d(TAG, "[Auth] Supabase anonymous sign in successful. User ID in auth.users: ${currentUser.id}")
                Result.success(currentUser)
            } else {
                val err = IllegalStateException("Supabase anonymous sign-in completed, but currentUser is null.")
                Log.e(TAG, "[Auth] Sign-in verification failed", err)
                Result.failure(err)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Auth] signInAnonymously failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun RemoteIdentity.toLocalEntity(overrideAutoRotation: Boolean? = null): LocalIdentityEntity {
        val createdEpoch = parseIsoTimestamp(createdAt)
        val expiresEpoch = parseIsoTimestamp(expiresAt)
        return LocalIdentityEntity(
            id = id.ifEmpty { UUID.randomUUID().toString() },
            userId = userId,
            publicId = publicId,
            createdAt = createdEpoch,
            expiresAt = if (expiresEpoch <= System.currentTimeMillis()) System.currentTimeMillis() + Constants.IDENTITY_LIFESPAN_MS else expiresEpoch,
            status = status,
            autoRotationEnabled = overrideAutoRotation ?: autoRotationEnabled
        )
    }

    private fun parseIsoTimestamp(isoString: String?): Long {
        if (isoString.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun LocalIdentityEntity.toDomainModel(): Identity {
        return Identity(
            id = id,
            userId = userId,
            publicId = publicId,
            createdAt = createdAt,
            expiresAt = expiresAt,
            status = when (status.lowercase()) {
                "active" -> IdentityStatus.ACTIVE
                "expired" -> IdentityStatus.EXPIRED
                else -> IdentityStatus.REVOKED
            },
            autoRotationEnabled = autoRotationEnabled
        )
    }

    companion object {
        private const val TAG = "IdentityRepo"
        private val authMutex = Mutex()
    }
}
