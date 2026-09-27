package com.lactose.textme.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lactose.textme.domain.model.ChatRequest
import com.lactose.textme.domain.model.RequestStatus
import com.lactose.textme.domain.repository.ChatRequestRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class InboxRequestItem(
    val primaryRequest: ChatRequest,
    val requestIds: List<String>,
    val count: Int
) {
    val id: String get() = primaryRequest.id
    val senderPublicId: String get() = primaryRequest.senderPublicId
    val senderIdentityId: String get() = primaryRequest.senderIdentityId
    val receiverIdentityId: String get() = primaryRequest.receiverIdentityId
    val receiverPublicId: String get() = primaryRequest.receiverPublicId
    val status: RequestStatus get() = primaryRequest.status
    val createdAt: Long get() = primaryRequest.createdAt
    val updatedAt: Long get() = primaryRequest.updatedAt
}

data class InboxUiState(
    val requests: List<InboxRequestItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class InboxViewModel(
    private val chatRequestRepository: ChatRequestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        observeRequests()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = chatRequestRepository.syncIncomingChatRequests()
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
            )
        }
    }

    private fun observeRequests() {
        viewModelScope.launch {
            chatRequestRepository.getPendingRequestsFlow().collect { list ->
                val grouped = groupRequests(list)
                _uiState.update { it.copy(requests = grouped) }
            }
        }
    }

    fun acceptRequest(item: InboxRequestItem, onAccepted: (String, String) -> Unit) {
        viewModelScope.launch {
            val result = chatRequestRepository.acceptChatRequest(item.id)
            result.fold(
                onSuccess = { conversationId ->
                    val otherIds = item.requestIds.filter { it != item.id }
                    if (otherIds.isNotEmpty()) {
                        chatRequestRepository.rejectChatRequests(otherIds, alsoBlockSender = false)
                    }
                    onAccepted(conversationId, item.senderPublicId)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "Failed to accept request") }
                }
            )
        }
    }

    fun acceptRequest(requestId: String, onAccepted: (String, String) -> Unit) {
        val item = _uiState.value.requests.find { it.id == requestId || requestId in it.requestIds }
        if (item != null) {
            acceptRequest(item, onAccepted)
        } else {
            viewModelScope.launch {
                val result = chatRequestRepository.acceptChatRequest(requestId)
                result.fold(
                    onSuccess = { conversationId -> onAccepted(conversationId, "") },
                    onFailure = { error -> _uiState.update { it.copy(errorMessage = error.message) } }
                )
            }
        }
    }

    fun rejectRequest(item: InboxRequestItem, alsoBlock: Boolean = false) {
        viewModelScope.launch {
            chatRequestRepository.rejectChatRequests(item.requestIds, alsoBlockSender = alsoBlock)
        }
    }

    fun rejectRequest(requestId: String, alsoBlock: Boolean = false) {
        val item = _uiState.value.requests.find { it.id == requestId || requestId in it.requestIds }
        if (item != null) {
            rejectRequest(item, alsoBlock)
        } else {
            viewModelScope.launch {
                chatRequestRepository.rejectChatRequest(requestId, alsoBlock)
            }
        }
    }

    fun dismissRequest(requestId: String) {
        rejectRequest(requestId, alsoBlock = false)
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun groupRequests(requests: List<ChatRequest>): List<InboxRequestItem> {
            return requests
                .groupBy { req ->
                    if (req.senderPublicId.isNotBlank() && req.senderPublicId != "UNKNOWN") {
                        req.senderPublicId
                    } else {
                        req.senderIdentityId
                    }
                }
                .values
                .map { group ->
                    val sorted = group.sortedByDescending { it.createdAt }
                    val primary = sorted.first()
                    InboxRequestItem(
                        primaryRequest = primary,
                        requestIds = sorted.map { it.id },
                        count = sorted.size
                    )
                }
                .sortedByDescending { it.createdAt }
        }
    }
}

