package com.lactose.textme

import com.lactose.textme.domain.model.Conversation
import com.lactose.textme.domain.model.ConversationStatus
import com.lactose.textme.domain.model.Identity
import com.lactose.textme.domain.model.IdentityStatus
import com.lactose.textme.domain.model.Message
import com.lactose.textme.domain.repository.ConversationRepository
import com.lactose.textme.domain.repository.IdentityRepository
import com.lactose.textme.presentation.conversation.ConversationViewModel
import com.lactose.textme.presentation.conversation.IdentityChangeAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.*
import org.junit.Test

class ConversationViewModelTest {

    private class FakeConversationRepository : ConversationRepository {
        val conversationsFlow = MutableStateFlow<Map<String, Conversation>>(emptyMap())
        val messagesFlow = MutableStateFlow<List<Message>>(emptyList())
        var deletedConversationId: String? = null
        var sentMessages = mutableListOf<String>()

        override fun getActiveConversationsFlow(): Flow<List<Conversation>> {
            return MutableStateFlow(conversationsFlow.value.values.toList())
        }

        override fun getConversationFlow(conversationId: String): Flow<Conversation?> {
            return MutableStateFlow(conversationsFlow.value[conversationId])
        }

        override suspend fun getConversationById(conversationId: String): Conversation? {
            return conversationsFlow.value[conversationId]
        }

        override suspend fun syncConversationStatus(conversationId: String): Result<Conversation?> {
            return Result.success(conversationsFlow.value[conversationId])
        }

        override suspend fun syncActiveConversations(): Result<List<Conversation>> {
            return Result.success(conversationsFlow.value.values.toList())
        }

        override fun getMessagesFlow(conversationId: String): Flow<List<Message>> {
            return messagesFlow.asStateFlow()
        }

        override suspend fun syncMessages(conversationId: String): Result<List<Message>> {
            return Result.success(messagesFlow.value)
        }

        override suspend fun syncAllActiveConversationsMessages(): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun sendMessage(conversationId: String, text: String): Result<Message> {
            sentMessages.add(text)
            val msg = Message(
                id = "msg_1",
                conversationId = conversationId,
                senderIdentityId = "user_me",
                ciphertext = text,
                nonce = "nonce",
                createdAt = System.currentTimeMillis(),
                text = text
            )
            return Result.success(msg)
        }

        override suspend fun deleteConversation(conversationId: String): Result<Unit> {
            deletedConversationId = conversationId
            conversationsFlow.value = conversationsFlow.value - conversationId
            return Result.success(Unit)
        }

        override suspend fun togglePinConversation(conversationId: String): Result<Boolean> {
            val conv = conversationsFlow.value[conversationId] ?: return Result.failure(IllegalArgumentException("Not found"))
            val newPinned = !conv.isPinned
            conversationsFlow.value = conversationsFlow.value + (conversationId to conv.copy(isPinned = newPinned))
            return Result.success(newPinned)
        }

        var activeOpenConversationId: String? = null
        var markedReadConversationId: String? = null

        override fun setActiveOpenConversation(conversationId: String?) {
            activeOpenConversationId = conversationId
        }

        override suspend fun markConversationAsRead(conversationId: String): Result<Unit> {
            markedReadConversationId = conversationId
            return Result.success(Unit)
        }
    }

    private class FakeIdentityRepository(
        var activeIdentity: Identity?
    ) : IdentityRepository {
        override fun getActiveIdentityFlow(): Flow<Identity?> = MutableStateFlow(activeIdentity)
        override suspend fun getActiveIdentity(): Identity? = activeIdentity
        override suspend fun restoreSession(): Result<Identity?> = Result.success(activeIdentity)
        override suspend fun createIdentity(): Result<Identity> = Result.failure(UnsupportedOperationException())
        override suspend fun rotateIdentity(): Result<Identity> = Result.failure(UnsupportedOperationException())
        override suspend fun updateAutoRotation(autoRotationEnabled: Boolean): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun self_rotated_shows_self_changed_alert() {
        val convId = "conv_1"
        // Current device has identity id_new (user rotated their ID)
        val currentIdentity = Identity(
            id = "id_new",
            userId = "user_1",
            publicId = "NEW8CHAR",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            status = IdentityStatus.ACTIVE
        )
        // Conversation was created with id_old and id_peer
        val conv = Conversation(
            id = convId,
            participantAId = "id_old",
            participantAPublicId = "OLD8CHAR",
            participantBId = "id_peer",
            participantBPublicId = "PEER8CHA",
            createdAt = System.currentTimeMillis() - 10000L,
            expiresAt = System.currentTimeMillis() + 76400000L,
            status = ConversationStatus.ACTIVE
        )

        val fakeConvRepo = FakeConversationRepository()
        fakeConvRepo.conversationsFlow.value = mapOf(convId to conv)
        val fakeIdRepo = FakeIdentityRepository(currentIdentity)

        val viewModel = ConversationViewModel(fakeConvRepo, fakeIdRepo, Dispatchers.Unconfined)
        viewModel.initConversation(convId, "PEER8CHA")

        assertEquals(
            "Must display SELF_CHANGED alert when current identity is not a participant",
            IdentityChangeAlert.SELF_CHANGED,
            viewModel.uiState.value.identityChangeAlert
        )
    }

    @Test
    fun peer_rotated_shows_other_changed_alert() {
        val convId = "conv_1"
        val currentIdentity = Identity(
            id = "id_me",
            userId = "user_1",
            publicId = "MY8CHARS",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            status = IdentityStatus.ACTIVE
        )
        // Conversation terminated because peer rotated
        val conv = Conversation(
            id = convId,
            participantAId = "id_me",
            participantAPublicId = "MY8CHARS",
            participantBId = "id_peer",
            participantBPublicId = "PEER8CHA",
            createdAt = System.currentTimeMillis() - 10000L,
            expiresAt = System.currentTimeMillis() + 76400000L,
            status = ConversationStatus.EXPIRED,
            terminatedReason = "identity_rotated",
            terminatedBy = "id_peer"
        )

        val fakeConvRepo = FakeConversationRepository()
        fakeConvRepo.conversationsFlow.value = mapOf(convId to conv)
        val fakeIdRepo = FakeIdentityRepository(currentIdentity)

        val viewModel = ConversationViewModel(fakeConvRepo, fakeIdRepo, Dispatchers.Unconfined)
        viewModel.initConversation(convId, "PEER8CHA")

        assertEquals(
            "Must display OTHER_CHANGED alert when conversation is terminated by peer",
            IdentityChangeAlert.OTHER_CHANGED,
            viewModel.uiState.value.identityChangeAlert
        )
    }

    @Test
    fun deleteAndExit_deletes_conversation_and_invokes_navigation() {
        val convId = "conv_to_delete"
        val fakeConvRepo = FakeConversationRepository()
        val fakeIdRepo = FakeIdentityRepository(null)

        val viewModel = ConversationViewModel(fakeConvRepo, fakeIdRepo, Dispatchers.Unconfined)
        viewModel.initConversation(convId, "PEER8CHA")

        var navInvoked = false
        viewModel.deleteAndExit {
            navInvoked = true
        }

        assertEquals("Must call repository to delete the conversation", convId, fakeConvRepo.deletedConversationId)
        assertTrue("Must invoke navigation callback after deletion", navInvoked)
    }

    @Test
    fun sendMessage_is_prevented_when_identity_changed() {
        val convId = "conv_blocked"
        val currentIdentity = Identity(
            id = "id_new",
            userId = "user_1",
            publicId = "NEW8CHAR",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            status = IdentityStatus.ACTIVE
        )
        val conv = Conversation(
            id = convId,
            participantAId = "id_old",
            participantAPublicId = "OLD8CHAR",
            participantBId = "id_peer",
            participantBPublicId = "PEER8CHA",
            createdAt = System.currentTimeMillis() - 10000L,
            expiresAt = System.currentTimeMillis() + 76400000L,
            status = ConversationStatus.ACTIVE
        )

        val fakeConvRepo = FakeConversationRepository()
        fakeConvRepo.conversationsFlow.value = mapOf(convId to conv)
        val fakeIdRepo = FakeIdentityRepository(currentIdentity)

        val viewModel = ConversationViewModel(fakeConvRepo, fakeIdRepo, Dispatchers.Unconfined)
        viewModel.initConversation(convId, "PEER8CHA")

        viewModel.onInputTextChanged("Hello after rotation")
        viewModel.sendMessage()

        assertTrue("No message should be sent when identity change alert is active", fakeConvRepo.sentMessages.isEmpty())
    }

    @Test
    fun testInitConversationMarksReadAndSetsActiveConversation() {
        val convId = "conv_123"
        val currentIdentity = Identity(
            id = "id_1",
            userId = "user_1",
            publicId = "MYID1234",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            status = IdentityStatus.ACTIVE
        )
        val conv = Conversation(
            id = convId,
            participantAId = "id_1",
            participantAPublicId = "MYID1234",
            participantBId = "id_2",
            participantBPublicId = "PEER8CHA",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            status = ConversationStatus.ACTIVE,
            unreadCount = 3
        )

        val fakeConvRepo = FakeConversationRepository()
        fakeConvRepo.conversationsFlow.value = mapOf(convId to conv)
        val fakeIdRepo = FakeIdentityRepository(currentIdentity)

        val viewModel = ConversationViewModel(fakeConvRepo, fakeIdRepo, Dispatchers.Unconfined)
        viewModel.initConversation(convId, "PEER8CHA")

        assertEquals("conv_123", fakeConvRepo.activeOpenConversationId)
        assertEquals("conv_123", fakeConvRepo.markedReadConversationId)
    }
}
