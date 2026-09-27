package com.lactose.textme.domain.repository

import com.lactose.textme.domain.model.ChatRequest
import kotlinx.coroutines.flow.Flow

interface ChatRequestRepository {
    fun getPendingRequestsFlow(): Flow<List<ChatRequest>>
    suspend fun syncIncomingChatRequests(): Result<List<ChatRequest>>
    suspend fun searchPublicId(publicId: String): Result<Boolean>
    suspend fun sendChatRequest(receiverPublicId: String): Result<ChatRequest>
    suspend fun acceptChatRequest(requestId: String): Result<String> // returns conversationId
    suspend fun rejectChatRequest(requestId: String, alsoBlockSender: Boolean = false): Result<Unit>
    suspend fun rejectChatRequests(requestIds: List<String>, alsoBlockSender: Boolean = false): Result<Unit>
}

