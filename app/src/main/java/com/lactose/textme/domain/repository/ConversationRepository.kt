package com.lactose.textme.domain.repository

import com.lactose.textme.domain.model.Conversation
import com.lactose.textme.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getActiveConversationsFlow(): Flow<List<Conversation>>
    fun getConversationFlow(conversationId: String): Flow<Conversation?>
    suspend fun getConversationById(conversationId: String): Conversation?
    suspend fun syncConversationStatus(conversationId: String): Result<Conversation?>
    suspend fun syncActiveConversations(): Result<List<Conversation>>
    fun getMessagesFlow(conversationId: String): Flow<List<Message>>
    suspend fun syncMessages(conversationId: String): Result<List<Message>>
    suspend fun syncAllActiveConversationsMessages(): Result<Unit>
    suspend fun sendMessage(conversationId: String, text: String): Result<Message>
    suspend fun markConversationAsRead(conversationId: String): Result<Unit>
    fun setActiveOpenConversation(conversationId: String?)
    suspend fun deleteConversation(conversationId: String): Result<Unit>
    suspend fun togglePinConversation(conversationId: String): Result<Boolean>
}
