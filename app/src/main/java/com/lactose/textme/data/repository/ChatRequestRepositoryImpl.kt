package com.lactose.textme.data.repository

import com.lactose.textme.core.network.SupabaseNetworkClient
import com.lactose.textme.data.local.dao.ChatRequestDao
import com.lactose.textme.data.local.dao.ConversationDao
import com.lactose.textme.data.local.dao.IdentityDao
import com.lactose.textme.data.local.entity.LocalChatRequestEntity
import com.lactose.textme.data.local.entity.LocalConversationEntity
import com.lactose.textme.data.remote.model.RemoteChatRequest
import com.lactose.textme.data.remote.model.RemoteConversation
import com.lactose.textme.data.remote.model.RemoteIdentity
import com.lactose.textme.domain.model.ChatRequest
import com.lactose.textme.domain.model.ConversationStatus
import com.lactose.textme.domain.model.RequestStatus
import com.lactose.textme.domain.repository.ChatRequestRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

class ChatRequestRepositoryImpl(
    private val chatRequestDao: ChatRequestDao,
    private val conversationDao: ConversationDao,
    private val identityDao: IdentityDao
) : ChatRequestRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPendingRequestsFlow(): Flow<List<ChatRequest>> {
        return identityDao.getActiveIdentityFlow().flatMapLatest { activeIdentity ->
            if (activeIdentity == null) {
                flowOf(emptyList())
            } else {
                chatRequestDao.getPendingIncomingRequestsFlow(activeIdentity.id).map { list ->
                    list.map { it.toDomainModel() }
                }
            }
        }
    }

    override suspend fun syncIncomingChatRequests(): Result<List<ChatRequest>> = withContext(Dispatchers.IO) {
        val currentIdentity = identityDao.getActiveIdentity()
            ?: return@withContext Result.failure(IllegalStateException("No active identity found"))

        try {
            val now = System.currentTimeMillis()

            // 1. Fetch pending requests where receiver is current identity
            val remoteRequests = SupabaseNetworkClient.postgrest["chat_requests"]
                .select {
                    filter {
                        eq("receiver_identity_id", currentIdentity.id)
                        eq("status", "pending")
                    }
                }
                .decodeList<RemoteChatRequest>()

            // Filter out any requests sent from identities that this user has blocked
            val blockedSenderIds = try {
                SupabaseNetworkClient.postgrest["blocks"]
                    .select {
                        filter {
                            eq("blocker_identity_id", currentIdentity.id)
                        }
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { it["blocked_identity_id"]?.jsonPrimitive?.contentOrNull }
                    .toSet()
            } catch (_: Exception) {
                emptySet()
            }

            val validRemoteRequests = remoteRequests.filter { it.senderIdentityId !in blockedSenderIds }

            if (validRemoteRequests.isEmpty()) {
                // Invalidate local pending incoming requests that no longer exist remotely
                chatRequestDao.markAllPendingIncomingRequestsCancelled(
                    myIdentityId = currentIdentity.id,
                    status = RequestStatus.CANCELLED.name.lowercase(),
                    updatedAt = now
                )
                return@withContext Result.success(emptyList())
            }

            // Reconcile Room cache: Mark local pending incoming requests NOT in active remote list as cancelled
            val activeRemoteIds = validRemoteRequests.map { it.id }
            chatRequestDao.markMissingRequestsCancelled(
                myIdentityId = currentIdentity.id,
                activeRemoteIds = activeRemoteIds,
                status = RequestStatus.CANCELLED.name.lowercase(),
                updatedAt = now
            )

            // 2. Fetch sender public IDs only for legacy records that might have blank sender_public_id
            val missingSenderIds = validRemoteRequests
                .filter { it.senderPublicId.isBlank() }
                .map { it.senderIdentityId }
                .distinct()

            val fallbackSenderIdentities = if (missingSenderIds.isNotEmpty()) {
                try {
                    SupabaseNetworkClient.postgrest["identities"]
                        .select {
                            filter {
                                isIn("id", missingSenderIds)
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

            // 3. Map to local entities and upsert
            val localEntities = validRemoteRequests.map { req ->
                val senderPublicId = req.senderPublicId.ifBlank {
                    fallbackSenderIdentities[req.senderIdentityId]?.publicId ?: "UNKNOWN"
                }
                LocalChatRequestEntity(
                    id = req.id,
                    senderIdentityId = req.senderIdentityId,
                    senderPublicId = senderPublicId,
                    receiverIdentityId = req.receiverIdentityId,
                    receiverPublicId = req.receiverPublicId.ifBlank { currentIdentity.publicId },
                    status = req.status,
                    createdAt = parseIsoTimestamp(req.createdAt),
                    updatedAt = parseIsoTimestamp(req.updatedAt)
                )
            }
            chatRequestDao.insertRequests(localEntities)

            Result.success(localEntities.map { it.toDomainModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchPublicId(publicId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val currentIdentity = identityDao.getActiveIdentity()
            ?: return@withContext Result.failure(IllegalStateException("No active identity found"))

        val cleanPublicId = publicId.trim().uppercase()
        if (cleanPublicId == currentIdentity.publicId) {
            return@withContext Result.failure(IllegalArgumentException("You cannot message yourself."))
        }

        if (cleanPublicId.length != 8 || !cleanPublicId.all { it.isLetterOrDigit() }) {
            return@withContext Result.failure(IllegalArgumentException("ID does not match standard format."))
        }

        try {
            val matches = SupabaseNetworkClient.postgrest["identities"]
                .select {
                    filter {
                        eq("public_id", cleanPublicId)
                        eq("status", "active")
                    }
                }
                .decodeList<RemoteIdentity>()

            if (matches.isNotEmpty()) {
                Result.success(true)
            } else {
                Result.failure(IllegalArgumentException("Public ID not found or expired."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendChatRequest(receiverPublicId: String): Result<ChatRequest> = withContext(Dispatchers.IO) {
        val currentIdentity = identityDao.getActiveIdentity()
            ?: return@withContext Result.failure(IllegalStateException("No active identity found"))

        val cleanReceiverPublicId = receiverPublicId.trim().uppercase()
        if (cleanReceiverPublicId == currentIdentity.publicId) {
            return@withContext Result.failure(IllegalArgumentException("You cannot message yourself."))
        }

        if (cleanReceiverPublicId.length != 8 || !cleanReceiverPublicId.all { it.isLetterOrDigit() }) {
            return@withContext Result.failure(IllegalArgumentException("Public ID must be exactly 8 alphanumeric characters."))
        }

        try {
            // 1. Find receiver identity on Supabase
            val targetIdentity = SupabaseNetworkClient.postgrest["identities"]
                .select {
                    filter {
                        eq("public_id", cleanReceiverPublicId)
                        eq("status", "active")
                    }
                }
                .decodeList<RemoteIdentity>()
                .firstOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("Recipient Public ID not found or expired."))

            if (targetIdentity.id == currentIdentity.id) {
                return@withContext Result.failure(IllegalArgumentException("You cannot message yourself."))
            }

            // 2. Check if either party has blocked the other
            val isBlocked = try {
                SupabaseNetworkClient.postgrest["blocks"]
                    .select {
                        filter {
                            or {
                                and {
                                    eq("blocker_identity_id", targetIdentity.id)
                                    eq("blocked_identity_id", currentIdentity.id)
                                }
                                and {
                                    eq("blocker_identity_id", currentIdentity.id)
                                    eq("blocked_identity_id", targetIdentity.id)
                                }
                            }
                        }
                    }
                    .decodeList<JsonObject>()
                    .isNotEmpty()
            } catch (_: Exception) {
                false
            }

            if (isBlocked) {
                return@withContext Result.failure(IllegalStateException("Unable to send request to this ID."))
            }

            // 3. Insert chat request into Supabase (not inserted into local incoming inbox)
            val remoteRequest = SupabaseNetworkClient.postgrest["chat_requests"]
                .insert(
                    mapOf(
                        "sender_identity_id" to currentIdentity.id,
                        "sender_public_id" to currentIdentity.publicId,
                        "receiver_identity_id" to targetIdentity.id,
                        "receiver_public_id" to cleanReceiverPublicId,
                        "status" to "pending"
                    )
                ) {
                    select()
                }
                .decodeSingle<RemoteChatRequest>()

            val domainModel = ChatRequest(
                id = remoteRequest.id,
                senderIdentityId = currentIdentity.id,
                senderPublicId = currentIdentity.publicId,
                receiverIdentityId = targetIdentity.id,
                receiverPublicId = cleanReceiverPublicId,
                status = RequestStatus.PENDING,
                createdAt = parseIsoTimestamp(remoteRequest.createdAt),
                updatedAt = parseIsoTimestamp(remoteRequest.updatedAt)
            )
            Result.success(domainModel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptChatRequest(requestId: String): Result<String> = withContext(Dispatchers.IO) {
        val currentIdentity = identityDao.getActiveIdentity()
            ?: return@withContext Result.failure(IllegalStateException("No active identity found"))

        try {
            // 1. Call RPC accept_chat_request on Supabase
            val rpcResult = SupabaseNetworkClient.postgrest.rpc(
                function = "accept_chat_request",
                parameters = mapOf("p_request_id" to requestId)
            ).decodeList<RemoteConversation>()

            val remoteConv = rpcResult.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("Failed to create conversation via RPC"))

            // 2. Resolve other participant's public ID
            val localReq = chatRequestDao.getRequestById(requestId)
            val otherParticipantId = if (remoteConv.participantA == currentIdentity.id) {
                remoteConv.participantB
            } else {
                remoteConv.participantA
            }

            val otherPublicId = if (localReq != null && localReq.senderIdentityId == otherParticipantId && localReq.senderPublicId.isNotBlank() && localReq.senderPublicId != "UNKNOWN") {
                localReq.senderPublicId
            } else {
                try {
                    SupabaseNetworkClient.postgrest["identities"]
                        .select {
                            filter {
                                eq("id", otherParticipantId)
                            }
                        }
                        .decodeList<RemoteIdentity>()
                        .firstOrNull()?.publicId ?: "ANON"
                } catch (_: Exception) {
                    "ANON"
                }
            }

            // 3. Upsert LocalConversationEntity
            val localConv = LocalConversationEntity(
                id = remoteConv.id,
                participantAId = remoteConv.participantA,
                participantAPublicId = if (remoteConv.participantA == currentIdentity.id) currentIdentity.publicId else otherPublicId,
                participantBId = remoteConv.participantB,
                participantBPublicId = if (remoteConv.participantB == currentIdentity.id) currentIdentity.publicId else otherPublicId,
                createdAt = parseIsoTimestamp(remoteConv.createdAt),
                expiresAt = parseIsoTimestamp(remoteConv.expiresAt),
                status = ConversationStatus.ACTIVE.name.lowercase(),
                terminatedReason = null,
                terminatedBy = null,
                otherParticipantPublicId = otherPublicId
            )
            conversationDao.insertConversation(localConv)

            // 4. Update local request status
            val now = System.currentTimeMillis()
            chatRequestDao.updateStatus(requestId, RequestStatus.ACCEPTED.name.lowercase(), now)

            Result.success(remoteConv.id)
        } catch (e: Exception) {
            val errorMsg = e.message.orEmpty()
            val now = System.currentTimeMillis()
            if (errorMsg.contains("changed or expired", ignoreCase = true) ||
                errorMsg.contains("no longer pending", ignoreCase = true) ||
                errorMsg.contains("no longer active", ignoreCase = true) ||
                errorMsg.contains("identity missing or expired", ignoreCase = true)
            ) {
                chatRequestDao.updateStatus(requestId, RequestStatus.CANCELLED.name.lowercase(), now)
                return@withContext Result.failure(
                    IllegalStateException("The person who sent this request has changed their Public ID. This request is no longer valid.", e)
                )
            }
            Result.failure(e)
        }
    }

    override suspend fun rejectChatRequest(requestId: String, alsoBlockSender: Boolean): Result<Unit> {
        return rejectChatRequests(listOf(requestId), alsoBlockSender)
    }

    override suspend fun rejectChatRequests(requestIds: List<String>, alsoBlockSender: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (requestIds.isEmpty()) return@withContext Result.success(Unit)
        try {
            val now = System.currentTimeMillis()
            val currentIdentity = identityDao.getActiveIdentity()

            // 1. Identify senderIdentityId before updating status if alsoBlockSender is true
            val senderIdentityId = if (alsoBlockSender) {
                requestIds.firstNotNullOfOrNull { id ->
                    chatRequestDao.getRequestById(id)?.senderIdentityId
                }
            } else null

            // 2. Mark local request(s) as rejected
            requestIds.forEach { id ->
                chatRequestDao.updateStatus(id, RequestStatus.REJECTED.name.lowercase(), now)
            }

            // 3. Mark remote request(s) as rejected
            try {
                SupabaseNetworkClient.postgrest["chat_requests"]
                    .update(
                        mapOf(
                            "status" to "rejected",
                            "updated_at" to Instant.ofEpochMilli(now).toString()
                        )
                    ) {
                        filter {
                            isIn("id", requestIds)
                        }
                    }
            } catch (_: Exception) {
                // Ignore network error on remote reject update
            }

            // 4. If alsoBlockSender is true, add to blocks table and cancel all requests from that identity
            if (alsoBlockSender && senderIdentityId != null && currentIdentity != null) {
                try {
                    SupabaseNetworkClient.postgrest["blocks"]
                        .insert(
                            mapOf(
                                "blocker_identity_id" to currentIdentity.id,
                                "blocked_identity_id" to senderIdentityId
                            )
                        )
                } catch (_: Exception) {
                    // Ignore if already blocked or duplicate
                }

                chatRequestDao.markAllPendingRequestsCancelledForIdentity(
                    identityId = senderIdentityId,
                    status = RequestStatus.CANCELLED.name.lowercase(),
                    updatedAt = now
                )
            }

            Result.success(Unit)
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

    private fun LocalChatRequestEntity.toDomainModel(): ChatRequest {
        return ChatRequest(
            id = id,
            senderIdentityId = senderIdentityId,
            senderPublicId = senderPublicId,
            receiverIdentityId = receiverIdentityId,
            receiverPublicId = receiverPublicId,
            status = when (status.lowercase()) {
                "accepted" -> RequestStatus.ACCEPTED
                "rejected" -> RequestStatus.REJECTED
                "cancelled" -> RequestStatus.CANCELLED
                "expired" -> RequestStatus.EXPIRED
                else -> RequestStatus.PENDING
            },
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
