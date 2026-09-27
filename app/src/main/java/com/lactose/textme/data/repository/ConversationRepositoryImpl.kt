package com.lactose.textme.data.repository

import com.lactose.textme.core.crypto.CryptoEngine
import com.lactose.textme.core.network.SupabaseNetworkClient
import com.lactose.textme.data.local.dao.ConversationDao
import com.lactose.textme.data.local.dao.IdentityDao
import com.lactose.textme.data.local.dao.MessageDao
import com.lactose.textme.data.local.entity.LocalConversationEntity
import com.lactose.textme.data.local.entity.LocalMessageEntity
import com.lactose.textme.data.remote.model.RemoteConversation
import com.lactose.textme.data.remote.model.RemoteIdentity
import com.lactose.textme.data.remote.model.RemoteMessage
import com.lactose.textme.domain.model.Conversation
import com.lactose.textme.domain.model.ConversationStatus
import com.lactose.textme.domain.model.Message
import com.lactose.textme.domain.repository.ConversationRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class ConversationRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val identityDao: IdentityDao
) : ConversationRepository {

    private var activeOpenConversationId: String? = null

    override fun getActiveConversationsFlow(): Flow<List<Conversation>> {
        return conversationDao.getActiveConversationsFlow().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override fun getConversationFlow(conversationId: String): Flow<Conversation?> {
        return conversationDao.getConversationByIdFlow(conversationId).map { it?.toDomainModel() }
    }

    override suspend fun getConversationById(conversationId: String): Conversation? = withContext(Dispatchers.IO) {
        conversationDao.getConversationById(conversationId)?.toDomainModel()
    }

    override suspend fun syncConversationStatus(conversationId: String): Result<Conversation?> = withContext(Dispatchers.IO) {
        val currentIdentity = identityDao.getActiveIdentity()
        val localConv = conversationDao.getConversationById(conversationId)
        if (localConv?.status == "deleted") {
            return@withContext Result.success(null)
        }

        // Self rotation detection: if local conversation exists but current identity is not a participant
        if (localConv != null && currentIdentity != null) {
            val isParticipant = currentIdentity.id == localConv.participantAId || currentIdentity.id == localConv.participantBId
            if (!isParticipant) {
                val selfOldId = if (localConv.participantAId == localConv.terminatedBy) localConv.participantAId else localConv.participantBId
                conversationDao.markConversationTerminated(
                    conversationId = conversationId,
                    reason = "identity_rotated",
                    terminatedBy = selfOldId
                )
                return@withContext Result.success(conversationDao.getConversationById(conversationId)?.toDomainModel())
            }
        }

        try {
            val remoteConv = SupabaseNetworkClient.postgrest["conversations"]
                .select {
                    filter {
                        eq("id", conversationId)
                    }
                }
                .decodeSingleOrNull<RemoteConversation>()

            if (remoteConv != null) {
                if (remoteConv.terminatedReason == "identity_rotated" || remoteConv.status == "expired") {
                    conversationDao.markConversationTerminated(
                        conversationId = conversationId,
                        reason = remoteConv.terminatedReason ?: "identity_rotated",
                        terminatedBy = remoteConv.terminatedBy
                    )
                }

                val partnerId = if (currentIdentity != null && remoteConv.participantA == currentIdentity.id) {
                    remoteConv.participantB
                } else {
                    remoteConv.participantA
                }

                if (remoteConv.terminatedReason == null) {
                    try {
                        val partnerIdentity = SupabaseNetworkClient.postgrest["identities"]
                            .select {
                                filter {
                                    eq("id", partnerId)
                                }
                            }
                            .decodeSingleOrNull<RemoteIdentity>()

                        if (partnerIdentity != null && partnerIdentity.status == "revoked") {
                            conversationDao.markConversationTerminated(
                                conversationId = conversationId,
                                reason = "identity_rotated",
                                terminatedBy = partnerId
                            )
                        }
                    } catch (_: Exception) {}
                }
            } else {
                if (localConv != null && currentIdentity != null) {
                    val partnerId = if (localConv.participantAId == currentIdentity.id) localConv.participantBId else localConv.participantAId
                    try {
                        val partnerIdentity = SupabaseNetworkClient.postgrest["identities"]
                            .select {
                                filter {
                                    eq("id", partnerId)
                                }
                            }
                            .decodeSingleOrNull<RemoteIdentity>()

                        if (partnerIdentity == null || partnerIdentity.status == "revoked") {
                            conversationDao.markConversationTerminated(
                                conversationId = conversationId,
                                reason = "identity_rotated",
                                terminatedBy = partnerId
                            )
                        }
                    } catch (_: Exception) {}
                }
            }

            val updated = conversationDao.getConversationById(conversationId)
            Result.success(updated?.toDomainModel())
        } catch (e: Exception) {
            val fallback = conversationDao.getConversationById(conversationId)
            Result.success(fallback?.toDomainModel())
        }
    }

    override suspend fun syncActiveConversations(): Result<List<Conversation>> = withContext(Dispatchers.IO) {
        val currentIdentity = identityDao.getActiveIdentity()
            ?: return@withContext Result.failure(IllegalStateException("No active identity found"))

        try {
            // 1. Query Supabase strictly for active conversations for this identity
            val remoteConversations = SupabaseNetworkClient.postgrest["conversations"]
                .select {
                    filter {
                        or {
                            eq("participant_a", currentIdentity.id)
                            eq("participant_b", currentIdentity.id)
                        }
                        eq("status", "active")
                    }
                }
                .decodeList<RemoteConversation>()

            // 2. Filter non-expired active conversations
            val now = System.currentTimeMillis()
            val validActiveConversations = remoteConversations.filter { conv ->
                conv.status == "active" && parseIsoTimestamp(conv.expiresAt) > now
            }

            // 3. Exclude any conversations that the user explicitly deleted locally
            val deletedIds = conversationDao.getDeletedConversationIds().toSet()
            val activeToUpsert = validActiveConversations.filter { it.id !in deletedIds }

            // 4. Resolve other participants' identities for valid active conversations
            val otherParticipantIds = activeToUpsert.map { conv ->
                if (conv.participantA == currentIdentity.id) conv.participantB else conv.participantA
            }.distinct()

            val otherIdentities = if (otherParticipantIds.isNotEmpty()) {
                try {
                    SupabaseNetworkClient.postgrest["identities"]
                        .select {
                            filter {
                                isIn("id", otherParticipantIds)
                            }
                        }
                        .decodeList<RemoteIdentity>()
                        .associateBy { it.id }
                } catch (_: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }

            // 5. Map to LocalConversationEntity and upsert
            val localEntities = activeToUpsert.map { conv ->
                val otherId = if (conv.participantA == currentIdentity.id) conv.participantB else conv.participantA
                val otherPublicId = otherIdentities[otherId]?.publicId ?: "ANON"
                val existingLocal = conversationDao.getConversationById(conv.id)
                LocalConversationEntity(
                    id = conv.id,
                    participantAId = conv.participantA,
                    participantAPublicId = if (conv.participantA == currentIdentity.id) currentIdentity.publicId else otherPublicId,
                    participantBId = conv.participantB,
                    participantBPublicId = if (conv.participantB == currentIdentity.id) currentIdentity.publicId else otherPublicId,
                    createdAt = parseIsoTimestamp(conv.createdAt),
                    expiresAt = parseIsoTimestamp(conv.expiresAt),
                    status = conv.status,
                    terminatedReason = conv.terminatedReason,
                    terminatedBy = conv.terminatedBy,
                    otherParticipantPublicId = otherPublicId,
                    isPinned = existingLocal?.isPinned ?: false
                )
            }
            if (localEntities.isNotEmpty()) {
                conversationDao.insertConversations(localEntities)
            }

            // 6. Check for locally active conversations that are no longer active remotely (e.g. partner rotated ID or expired)
            val currentLocal = conversationDao.getAllConversations()
            val activeRemoteIds = activeToUpsert.map { it.id }.toSet()
            val locallyActiveNoLongerRemote = currentLocal.filter {
                it.status == "active" && it.id !in activeRemoteIds && it.id !in deletedIds
            }

            for (localConv in locallyActiveNoLongerRemote) {
                syncConversationStatus(localConv.id)
            }

            try {
                syncAllActiveConversationsMessages()
            } catch (_: Exception) {}

            val resultList = conversationDao.getAllConversations()
                .filter { it.status != "deleted" }
                .map { it.toDomainModel() }

            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getMessagesFlow(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversationFlow(conversationId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun syncMessages(conversationId: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        val currentIdentity = identityDao.getActiveIdentity()
            ?: return@withContext Result.failure(IllegalStateException("No active identity found"))

        try {
            // 1. Fetch remote messages for this conversation from Supabase
            val remoteMessages = SupabaseNetworkClient.postgrest["messages"]
                .select {
                    filter {
                        eq("conversation_id", conversationId)
                    }
                }
                .decodeList<RemoteMessage>()

            if (remoteMessages.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // 2. Optimization: Filter out messages already cached in Room
            val existingIds = messageDao.getExistingMessageIds(conversationId).toSet()
            val newRemoteMessages = remoteMessages.filter { it.id !in existingIds }

            if (newRemoteMessages.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // 3. Derive conversation key and decrypt new incoming messages
            val conversationKey = CryptoEngine.deriveConversationKey(conversationId)
            val isOpen = activeOpenConversationId == conversationId

            val newEntities = newRemoteMessages.map { remote ->
                val decrypted = try {
                    CryptoEngine.decryptMessage(conversationKey, remote.ciphertext, remote.nonce)
                } catch (_: Exception) {
                    "[Encrypted Message]"
                }

                val isSentByMe = remote.senderIdentityId == currentIdentity.id
                val isRead = isSentByMe || isOpen

                LocalMessageEntity(
                    id = remote.id,
                    conversationId = conversationId,
                    senderIdentityId = remote.senderIdentityId,
                    ciphertext = remote.ciphertext,
                    nonce = remote.nonce,
                    createdAt = parseIsoTimestamp(remote.createdAt),
                    decryptedText = decrypted,
                    isRead = isRead
                )
            }

            // 4. Batch insert into Room
            messageDao.insertMessages(newEntities)
            Result.success(newEntities.map { it.toDomainModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncAllActiveConversationsMessages(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val activeConvs = conversationDao.getAllConversations().filter { it.status == "active" }
            for (conv in activeConvs) {
                syncMessages(conv.id)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(conversationId: String, text: String): Result<Message> = withContext(Dispatchers.IO) {
        val currentIdentity = identityDao.getActiveIdentity()
            ?: return@withContext Result.failure(IllegalStateException("No active identity"))

        if (System.currentTimeMillis() >= currentIdentity.expiresAt) {
            return@withContext Result.failure(IllegalStateException("Your identity has expired."))
        }

        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        try {
            // 1. Derive symmetric AES key from conversationId and encrypt locally
            val conversationKey = CryptoEngine.deriveConversationKey(conversationId)
            val encryptedPayload = CryptoEngine.encryptMessage(conversationKey, text)

            // 2. Optimistic local insert into Room immediately (0ms UI latency)
            val localEntity = LocalMessageEntity(
                id = messageId,
                conversationId = conversationId,
                senderIdentityId = currentIdentity.id,
                ciphertext = encryptedPayload.ciphertext,
                nonce = encryptedPayload.nonce,
                createdAt = now,
                decryptedText = text,
                isRead = true
            )
            messageDao.insertMessage(localEntity)

            // 3. Fast asynchronous network insert to Supabase without blocking select()
            try {
                SupabaseNetworkClient.postgrest["messages"]
                    .insert(
                        mapOf(
                            "id" to messageId,
                            "conversation_id" to conversationId,
                            "sender_identity_id" to currentIdentity.id,
                            "ciphertext" to encryptedPayload.ciphertext,
                            "nonce" to encryptedPayload.nonce,
                            "created_at" to Instant.ofEpochMilli(now).toString()
                        )
                    )
            } catch (networkError: Exception) {
                return@withContext Result.failure(networkError)
            }

            Result.success(localEntity.toDomainModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun setActiveOpenConversation(conversationId: String?) {
        activeOpenConversationId = conversationId
    }

    override suspend fun markConversationAsRead(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            messageDao.markMessagesAsRead(conversationId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteConversation(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            messageDao.deleteMessagesForConversation(conversationId)
            conversationDao.markConversationDeleted(conversationId)

            try {
                SupabaseNetworkClient.postgrest["conversations"].update(
                    mapOf("status" to "deleted")
                ) {
                    filter {
                        eq("id", conversationId)
                    }
                }
            } catch (_: Exception) {
                // Ignore network/RLS issues; local deletion is authoritative
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun togglePinConversation(conversationId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val existing = conversationDao.getConversationById(conversationId)
                ?: return@withContext Result.failure(IllegalArgumentException("Conversation not found"))
            val newPinned = !existing.isPinned
            conversationDao.updatePinned(conversationId, newPinned)
            Result.success(newPinned)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseIsoTimestamp(isoString: String?): Long {
        if (isoString.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun com.lactose.textme.data.local.dao.LocalConversationWithLastMessage.toDomainModel(): Conversation {
        return conversation.toDomainModel(
            lastMessageTimestamp = this.lastMessageTimestamp,
            unreadCount = this.unreadCount
        )
    }

    private fun LocalConversationEntity.toDomainModel(
        lastMessageTimestamp: Long? = null,
        unreadCount: Int = 0
    ): Conversation {
        return Conversation(
            id = id,
            participantAId = participantAId,
            participantAPublicId = participantAPublicId,
            participantBId = participantBId,
            participantBPublicId = participantBPublicId,
            createdAt = createdAt,
            expiresAt = expiresAt,
            status = when (status.lowercase()) {
                "expired" -> ConversationStatus.EXPIRED
                "deleted" -> ConversationStatus.DELETED
                else -> ConversationStatus.ACTIVE
            },
            terminatedReason = terminatedReason,
            terminatedBy = terminatedBy,
            otherParticipantPublicId = otherParticipantPublicId,
            isPinned = isPinned,
            lastMessageTimestamp = lastMessageTimestamp,
            unreadCount = unreadCount
        )
    }

    private fun LocalMessageEntity.toDomainModel(): Message {
        return Message(
            id = id,
            conversationId = conversationId,
            senderIdentityId = senderIdentityId,
            ciphertext = ciphertext,
            nonce = nonce,
            createdAt = createdAt,
            text = decryptedText ?: ciphertext
        )
    }
}
