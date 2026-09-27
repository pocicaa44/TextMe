package com.lactose.textme

import com.lactose.textme.data.local.dao.ChatRequestDao
import com.lactose.textme.data.local.dao.ConversationDao
import com.lactose.textme.data.local.dao.IdentityDao
import com.lactose.textme.data.local.entity.LocalChatRequestEntity
import com.lactose.textme.data.local.entity.LocalConversationEntity
import com.lactose.textme.data.local.entity.LocalIdentityEntity
import com.lactose.textme.data.repository.ChatRequestRepositoryImpl
import com.lactose.textme.domain.model.ChatRequest
import com.lactose.textme.domain.model.Conversation
import com.lactose.textme.domain.model.ConversationStatus
import com.lactose.textme.domain.model.RequestStatus
import com.lactose.textme.presentation.inbox.InboxViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.min

class ChatRequestRoutingTest {

    private lateinit var fakeIdentityDao: FakeIdentityDao
    private lateinit var fakeChatRequestDao: FakeChatRequestDao
    private lateinit var fakeConversationDao: FakeConversationDao
    private lateinit var repository: ChatRequestRepositoryImpl

    private val userAIdentity = LocalIdentityEntity(
        id = "identity_user_a",
        userId = "user_a",
        publicId = "AAAA1111",
        createdAt = 1000L,
        expiresAt = 1000L + 86400000L,
        status = "active",
        autoRotationEnabled = false
    )

    private val userBIdentity = LocalIdentityEntity(
        id = "identity_user_b",
        userId = "user_b",
        publicId = "BBBB2222",
        createdAt = 1000L,
        expiresAt = 1000L + 43200000L, // Expires earlier
        status = "active",
        autoRotationEnabled = false
    )

    @Before
    fun setUp() {
        fakeIdentityDao = FakeIdentityDao(userAIdentity)
        fakeChatRequestDao = FakeChatRequestDao()
        fakeConversationDao = FakeConversationDao()
        repository = ChatRequestRepositoryImpl(
            chatRequestDao = fakeChatRequestDao,
            conversationDao = fakeConversationDao,
            identityDao = fakeIdentityDao
        )
    }

    @Test
    fun sent_request_is_not_displayed_in_sender_incoming_inbox() = runBlocking {
        // User A sends a request to User B
        val outgoingRequest = LocalChatRequestEntity(
            id = "req_1",
            senderIdentityId = userAIdentity.id,
            senderPublicId = userAIdentity.publicId,
            receiverIdentityId = userBIdentity.id,
            receiverPublicId = userBIdentity.publicId,
            status = "pending",
            createdAt = 2000L,
            updatedAt = 2000L
        )

        // Store into fake DAO
        fakeChatRequestDao.insertRequest(outgoingRequest)

        // User A's pending requests flow should ONLY show incoming requests for User A
        val userAPendingRequests = repository.getPendingRequestsFlow().first()
        assertTrue("User A should not see outgoing requests in inbox", userAPendingRequests.isEmpty())

        // Switch active user to User B
        fakeIdentityDao.setActiveIdentity(userBIdentity)
        val userBPendingRequests = repository.getPendingRequestsFlow().first()
        assertEquals("User B should see 1 incoming request", 1, userBPendingRequests.size)
        assertEquals("Sender should be User A", userAIdentity.publicId, userBPendingRequests.first().senderPublicId)
        assertEquals(RequestStatus.PENDING, userBPendingRequests.first().status)
    }

    @Test
    fun self_request_validation_fails() = runBlocking {
        val result = repository.sendChatRequest(userAIdentity.publicId)
        assertTrue(result.isFailure)
        assertEquals("You cannot message yourself.", result.exceptionOrNull()?.message)
    }

    @Test
    fun invalid_format_public_id_fails() = runBlocking {
        val shortResult = repository.sendChatRequest("ABC")
        assertTrue(shortResult.isFailure)

        val invalidCharResult = repository.sendChatRequest("ABCDEF!#")
        assertTrue(invalidCharResult.isFailure)
    }

    @Test
    fun conversation_expiration_is_least_of_both_identities() {
        val convExpiresAt = min(userAIdentity.expiresAt, userBIdentity.expiresAt)
        assertEquals(userBIdentity.expiresAt, convExpiresAt)
    }

    @Test
    fun conversation_resolves_other_participant_public_id_correctly() {
        val conversation = Conversation(
            id = "conv_1",
            participantAId = userAIdentity.id,
            participantAPublicId = userAIdentity.publicId,
            participantBId = userBIdentity.id,
            participantBPublicId = userBIdentity.publicId,
            createdAt = 1000L,
            expiresAt = 50000L,
            status = ConversationStatus.ACTIVE
        )

        assertEquals("BBBB2222", conversation.getOtherParticipantPublicId("AAAA1111"))
        assertEquals("AAAA1111", conversation.getOtherParticipantPublicId("BBBB2222"))
    }

    @Test
    fun mark_missing_requests_cancelled_updates_stale_inbox_requests() = runBlocking {
        fakeIdentityDao.setActiveIdentity(userBIdentity)

        val req1 = LocalChatRequestEntity(
            id = "req_1",
            senderIdentityId = userAIdentity.id,
            senderPublicId = userAIdentity.publicId,
            receiverIdentityId = userBIdentity.id,
            receiverPublicId = userBIdentity.publicId,
            status = "pending",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val req2 = LocalChatRequestEntity(
            id = "req_2",
            senderIdentityId = "other_sender",
            senderPublicId = "OTHR9999",
            receiverIdentityId = userBIdentity.id,
            receiverPublicId = userBIdentity.publicId,
            status = "pending",
            createdAt = 2000L,
            updatedAt = 2000L
        )
        fakeChatRequestDao.insertRequests(listOf(req1, req2))

        // Remote only has req_2 active. req_1 was cancelled or revoked.
        val updated = fakeChatRequestDao.markMissingRequestsCancelled(
            myIdentityId = userBIdentity.id,
            activeRemoteIds = listOf("req_2"),
            status = "cancelled",
            updatedAt = 3000L
        )
        assertEquals(1, updated)

        // User B's incoming pending flow should now only show req_2
        val pendingList = repository.getPendingRequestsFlow().first()
        assertEquals(1, pendingList.size)
        assertEquals("req_2", pendingList.first().id)
        assertEquals(RequestStatus.PENDING, pendingList.first().status)

        // req_1 in dao is now cancelled
        val cancelledReq1 = fakeChatRequestDao.getRequestById("req_1")
        assertNotNull(cancelledReq1)
        assertEquals("cancelled", cancelledReq1?.status)
    }

    @Test
    fun mark_all_pending_requests_cancelled_for_identity_on_rotation() = runBlocking {
        val req1 = LocalChatRequestEntity(
            id = "req_1",
            senderIdentityId = userAIdentity.id,
            senderPublicId = userAIdentity.publicId,
            receiverIdentityId = userBIdentity.id,
            receiverPublicId = userBIdentity.publicId,
            status = "pending",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val req2 = LocalChatRequestEntity(
            id = "req_2",
            senderIdentityId = userBIdentity.id,
            senderPublicId = userBIdentity.publicId,
            receiverIdentityId = userAIdentity.id,
            receiverPublicId = userAIdentity.publicId,
            status = "pending",
            createdAt = 2000L,
            updatedAt = 2000L
        )
        fakeChatRequestDao.insertRequests(listOf(req1, req2))

        // User A rotates ID - cancels all requests involving user A
        val cancelledCount = fakeChatRequestDao.markAllPendingRequestsCancelledForIdentity(
            identityId = userAIdentity.id,
            status = "cancelled",
            updatedAt = 3000L
        )
        assertEquals(2, cancelledCount)
        assertEquals("cancelled", fakeChatRequestDao.getRequestById("req_1")?.status)
        assertEquals("cancelled", fakeChatRequestDao.getRequestById("req_2")?.status)
    }

    @Test
    fun duplicate_requests_from_same_id_are_grouped_and_count_starts_at_2() {
        val req1 = ChatRequest(
            id = "req_1",
            senderIdentityId = "sender_1",
            senderPublicId = "BBBB2222",
            receiverIdentityId = "receiver_1",
            receiverPublicId = "AAAA1111",
            status = RequestStatus.PENDING,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val req2 = ChatRequest(
            id = "req_2",
            senderIdentityId = "sender_1",
            senderPublicId = "BBBB2222",
            receiverIdentityId = "receiver_1",
            receiverPublicId = "AAAA1111",
            status = RequestStatus.PENDING,
            createdAt = 2000L,
            updatedAt = 2000L
        )
        val singleReq = ChatRequest(
            id = "req_3",
            senderIdentityId = "sender_2",
            senderPublicId = "CCCC3333",
            receiverIdentityId = "receiver_1",
            receiverPublicId = "AAAA1111",
            status = RequestStatus.PENDING,
            createdAt = 1500L,
            updatedAt = 1500L
        )

        val grouped = InboxViewModel.groupRequests(listOf(req1, req2, singleReq))

        // Total 2 distinct senders
        assertEquals(2, grouped.size)

        // Senders are sorted by most recent request createdAt:
        // BBBB2222 latest is 2000L, CCCC3333 latest is 1500L
        val bGroup = grouped[0]
        assertEquals("BBBB2222", bGroup.senderPublicId)
        assertEquals(2, bGroup.count) // Combined into one with count 2
        assertEquals("req_2", bGroup.id) // Most recent request is primary
        assertEquals(listOf("req_2", "req_1"), bGroup.requestIds)

        val cGroup = grouped[1]
        assertEquals("CCCC3333", cGroup.senderPublicId)
        assertEquals(1, cGroup.count) // Single request count is 1 (badge only displayed when count >= 2)
        assertEquals("req_3", cGroup.id)
    }

    @Test
    fun rejectChatRequests_updates_status_to_rejected() = runBlocking {
        val req1 = LocalChatRequestEntity(
            id = "req_1",
            senderIdentityId = userBIdentity.id,
            senderPublicId = userBIdentity.publicId,
            receiverIdentityId = userAIdentity.id,
            receiverPublicId = userAIdentity.publicId,
            status = "pending",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val req2 = LocalChatRequestEntity(
            id = "req_2",
            senderIdentityId = userBIdentity.id,
            senderPublicId = userBIdentity.publicId,
            receiverIdentityId = userAIdentity.id,
            receiverPublicId = userAIdentity.publicId,
            status = "pending",
            createdAt = 2000L,
            updatedAt = 2000L
        )
        fakeChatRequestDao.insertRequests(listOf(req1, req2))

        val result = repository.rejectChatRequests(listOf("req_1", "req_2"), alsoBlockSender = false)
        assertTrue(result.isSuccess)

        assertEquals("rejected", fakeChatRequestDao.getRequestById("req_1")?.status)
        assertEquals("rejected", fakeChatRequestDao.getRequestById("req_2")?.status)
    }

    @Test
    fun rejectChatRequests_with_alsoBlockSender_cancels_all_requests_from_that_identity() = runBlocking {
        val req1 = LocalChatRequestEntity(
            id = "req_1",
            senderIdentityId = userBIdentity.id,
            senderPublicId = userBIdentity.publicId,
            receiverIdentityId = userAIdentity.id,
            receiverPublicId = userAIdentity.publicId,
            status = "pending",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val req2 = LocalChatRequestEntity(
            id = "req_2",
            senderIdentityId = userBIdentity.id,
            senderPublicId = userBIdentity.publicId,
            receiverIdentityId = userAIdentity.id,
            receiverPublicId = userAIdentity.publicId,
            status = "pending",
            createdAt = 2000L,
            updatedAt = 2000L
        )
        fakeChatRequestDao.insertRequests(listOf(req1, req2))

        // Reject req1 with alsoBlockSender = true
        val result = repository.rejectChatRequests(listOf("req_1"), alsoBlockSender = true)
        assertTrue(result.isSuccess)

        assertEquals("rejected", fakeChatRequestDao.getRequestById("req_1")?.status)
        // Since user B was blocked, any other pending requests for user B are cancelled
        assertEquals("cancelled", fakeChatRequestDao.getRequestById("req_2")?.status)
    }

    // --- Fakes ---

    private class FakeIdentityDao(initial: LocalIdentityEntity?) : IdentityDao {
        private val state = MutableStateFlow(initial)

        fun setActiveIdentity(identity: LocalIdentityEntity?) {
            state.value = identity
        }

        override fun getActiveIdentityFlow(): Flow<LocalIdentityEntity?> = state
        override fun getActiveIdentity(): LocalIdentityEntity? = state.value
        override fun insertIdentity(identity: LocalIdentityEntity): Long = 1L
        override fun updateStatus(id: String, status: String): Int = 1
        override fun updateAutoRotation(id: String, enabled: Boolean): Int {
            state.value?.let { current ->
                if (current.id == id) {
                    state.value = current.copy(autoRotationEnabled = enabled)
                }
            }
            return 1
        }

        override fun updateAutoRotationWithExpiration(id: String, enabled: Boolean, expiresAt: Long): Int {
            state.value?.let { current ->
                if (current.id == id) {
                    state.value = current.copy(autoRotationEnabled = enabled, expiresAt = expiresAt)
                }
            }
            return 1
        }
        override fun clearAll(): Int = 1
    }

    private class FakeChatRequestDao : ChatRequestDao {
        private val requests = MutableStateFlow<Map<String, LocalChatRequestEntity>>(emptyMap())

        override fun getPendingRequestsFlow(): Flow<List<LocalChatRequestEntity>> {
            return requests.map { map ->
                map.values.filter { it.status == "pending" }.sortedByDescending { it.createdAt }
            }
        }

        override fun getPendingIncomingRequestsFlow(myIdentityId: String): Flow<List<LocalChatRequestEntity>> {
            return requests.map { map ->
                map.values.filter { it.receiverIdentityId == myIdentityId && it.status == "pending" }
                    .sortedByDescending { it.createdAt }
            }
        }

        override fun getPendingOutgoingRequestsFlow(myIdentityId: String): Flow<List<LocalChatRequestEntity>> {
            return requests.map { map ->
                map.values.filter { it.senderIdentityId == myIdentityId && it.status == "pending" }
                    .sortedByDescending { it.createdAt }
            }
        }

        override fun getRequestById(id: String): LocalChatRequestEntity? = requests.value[id]

        override fun insertRequest(request: LocalChatRequestEntity): Long {
            requests.value = requests.value + (request.id to request)
            return 1L
        }

        override fun insertRequests(requests: List<LocalChatRequestEntity>) {
            this.requests.value = this.requests.value + requests.associateBy { it.id }
        }

        override fun updateStatus(id: String, status: String, updatedAt: Long): Int {
            val existing = requests.value[id] ?: return 0
            requests.value = requests.value + (id to existing.copy(status = status, updatedAt = updatedAt))
            return 1
        }

        override fun markMissingRequestsCancelled(myIdentityId: String, activeRemoteIds: List<String>, status: String, updatedAt: Long): Int {
            var count = 0
            requests.value = requests.value.mapValues { (_, req) ->
                if (req.receiverIdentityId == myIdentityId && req.status == "pending" && req.id !in activeRemoteIds) {
                    count++
                    req.copy(status = status, updatedAt = updatedAt)
                } else {
                    req
                }
            }
            return count
        }

        override fun markAllPendingIncomingRequestsCancelled(myIdentityId: String, status: String, updatedAt: Long): Int {
            var count = 0
            requests.value = requests.value.mapValues { (_, req) ->
                if (req.receiverIdentityId == myIdentityId && req.status == "pending") {
                    count++
                    req.copy(status = status, updatedAt = updatedAt)
                } else {
                    req
                }
            }
            return count
        }

        override fun markAllPendingRequestsCancelledForIdentity(identityId: String, status: String, updatedAt: Long): Int {
            var count = 0
            requests.value = requests.value.mapValues { (_, req) ->
                if ((req.senderIdentityId == identityId || req.receiverIdentityId == identityId) && req.status == "pending") {
                    count++
                    req.copy(status = status, updatedAt = updatedAt)
                } else {
                    req
                }
            }
            return count
        }

        override fun deleteRequest(id: String): Int {
            requests.value = requests.value - id
            return 1
        }

        override fun clearAll(): Int {
            requests.value = emptyMap()
            return 1
        }
    }

    private class FakeConversationDao : ConversationDao {
        private val conversations = MutableStateFlow<Map<String, LocalConversationEntity>>(emptyMap())

        override fun getActiveConversationsFlow(): Flow<List<com.lactose.textme.data.local.dao.LocalConversationWithLastMessage>> {
            return conversations.map { map ->
                map.values.filter { it.status == "active" }
                    .sortedWith(compareByDescending<LocalConversationEntity> { it.isPinned }.thenByDescending { it.createdAt })
                    .map { com.lactose.textme.data.local.dao.LocalConversationWithLastMessage(it, null) }
            }
        }

        override fun getConversationById(id: String): LocalConversationEntity? = conversations.value[id]

        override fun getConversationByIdFlow(id: String): Flow<LocalConversationEntity?> {
            return conversations.map { it[id] }
        }

        override fun updatePinned(id: String, isPinned: Boolean): Int {
            val existing = conversations.value[id] ?: return 0
            conversations.value = conversations.value + (id to existing.copy(isPinned = isPinned))
            return 1
        }

        override fun markConversationsTerminatedByIdentity(identityId: String, reason: String, terminatedBy: String): Int {
            var count = 0
            conversations.value = conversations.value.mapValues { (_, entity) ->
                if ((entity.participantAId == identityId || entity.participantBId == identityId) && entity.status == "active") {
                    count++
                    entity.copy(status = "expired", terminatedReason = reason, terminatedBy = terminatedBy)
                } else {
                    entity
                }
            }
            return count
        }

        override fun markConversationTerminated(conversationId: String, reason: String, terminatedBy: String?): Int {
            val existing = conversations.value[conversationId] ?: return 0
            conversations.value = conversations.value + (conversationId to existing.copy(
                status = "expired",
                terminatedReason = reason,
                terminatedBy = terminatedBy
            ))
            return 1
        }

        override fun insertConversation(conversation: LocalConversationEntity): Long {
            conversations.value = conversations.value + (conversation.id to conversation)
            return 1L
        }

        override fun insertConversations(conversations: List<LocalConversationEntity>) {
            this.conversations.value = this.conversations.value + conversations.associateBy { it.id }
        }

        override fun getAllConversations(): List<LocalConversationEntity> = conversations.value.values.toList()

        override fun getDeletedConversationIds(): List<String> {
            return conversations.value.values.filter { it.status == "deleted" }.map { it.id }
        }

        override fun markConversationDeleted(id: String): Int {
            val existing = conversations.value[id] ?: return 0
            conversations.value = conversations.value + (id to existing.copy(status = "deleted"))
            return 1
        }

        override fun deleteConversation(id: String): Int {
            conversations.value = conversations.value - id
            return 1
        }

        override fun clearAll(): Int {
            conversations.value = emptyMap()
            return 1
        }
    }
}
