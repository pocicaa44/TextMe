package com.lactose.textme.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lactose.textme.core.network.SupabaseNetworkClient
import com.lactose.textme.domain.model.Conversation
import com.lactose.textme.domain.model.Identity
import com.lactose.textme.domain.repository.ChatRequestRepository
import com.lactose.textme.domain.repository.ConversationRepository
import com.lactose.textme.domain.repository.IdentityRepository
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class HomeUiState(
    val identity: Identity? = null,
    val formattedCountdown: String = "--h --m",
    val searchPublicIdInput: String = "",
    val searchError: String? = null,
    val isSendingRequest: Boolean = false,
    val requestSuccessMessage: String? = null,
    val pendingRequestCount: Int = 0,
    val activeConversations: List<Conversation> = emptyList()
)

class HomeViewModel(
    private val identityRepository: IdentityRepository,
    private val chatRequestRepository: ChatRequestRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var realtimeRequestsChannel: RealtimeChannel? = null
    private var realtimeConvsChannel: RealtimeChannel? = null
    private var realtimeMessagesChannel: RealtimeChannel? = null

    init {
        loadData()
        startCountdownTimer()
        startRealtimeListener()
        startBackgroundSync()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load active identity from repository (single source of truth)
            var currentIdentity = identityRepository.getActiveIdentity()
            if (currentIdentity != null && currentIdentity.isExpired && currentIdentity.autoRotationEnabled) {
                val rotateResult = identityRepository.rotateIdentity()
                currentIdentity = rotateResult.getOrNull() ?: currentIdentity
            }
            _uiState.update { it.copy(identity = currentIdentity) }

            // Observe identity
            identityRepository.getActiveIdentityFlow().collect { activeIdentity ->
                _uiState.update {
                    it.copy(
                        identity = activeIdentity,
                        formattedCountdown = if (activeIdentity?.autoRotationEnabled == true) it.formattedCountdown else "Disabled"
                    )
                }
            }
        }

        viewModelScope.launch {
            chatRequestRepository.getPendingRequestsFlow().collect { requests ->
                _uiState.update { it.copy(pendingRequestCount = requests.size) }
            }
        }

        viewModelScope.launch {
            conversationRepository.getActiveConversationsFlow().collect { conversations ->
                _uiState.update { it.copy(activeConversations = conversations) }
            }
        }

        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            chatRequestRepository.syncIncomingChatRequests()
        }
        viewModelScope.launch {
            conversationRepository.syncActiveConversations()
        }
    }

    private fun startRealtimeListener() {
        viewModelScope.launch {
            identityRepository.getActiveIdentityFlow().collectLatest { identity ->
                // Clean up previous channels if identity changed
                try {
                    realtimeRequestsChannel?.let { SupabaseNetworkClient.realtime.removeChannel(it) }
                    realtimeConvsChannel?.let { SupabaseNetworkClient.realtime.removeChannel(it) }
                    realtimeMessagesChannel?.let { SupabaseNetworkClient.realtime.removeChannel(it) }
                } catch (_: Exception) {}

                if (identity != null && SupabaseNetworkClient.isConfigured()) {
                    // 1. Instant inbox request badge notification via Realtime WebSocket (<100ms)
                    try {
                        val requestsChannel = SupabaseNetworkClient.realtime.channel("home_requests_${identity.id}")
                        realtimeRequestsChannel = requestsChannel
                        val reqFlow = requestsChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
                            table = "chat_requests"
                            filter("receiver_identity_id", FilterOperator.EQ, identity.id)
                        }
                        requestsChannel.subscribe()
                        launch {
                            reqFlow.collect {
                                chatRequestRepository.syncIncomingChatRequests()
                            }
                        }
                    } catch (_: Exception) {}

                    // 2. Instant active conversation changes via Realtime WebSocket
                    try {
                        val convsChannel = SupabaseNetworkClient.realtime.channel("home_convs_${identity.id}")
                        realtimeConvsChannel = convsChannel
                        val convFlow = convsChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
                            table = "conversations"
                        }
                        convsChannel.subscribe()
                        launch {
                            convFlow.collect {
                                conversationRepository.syncActiveConversations()
                            }
                        }
                    } catch (_: Exception) {}

                    // 3. Instant message changes for homescreen timestamp sync (< 50ms)
                    try {
                        val messagesChannel = SupabaseNetworkClient.realtime.channel("home_messages_${identity.id}")
                        realtimeMessagesChannel = messagesChannel
                        val msgFlow = messagesChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
                            table = "messages"
                        }
                        messagesChannel.subscribe()
                        launch {
                            msgFlow.collect {
                                conversationRepository.syncAllActiveConversationsMessages()
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun startBackgroundSync() {
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                chatRequestRepository.syncIncomingChatRequests()
                conversationRepository.syncActiveConversations()
                conversationRepository.syncAllActiveConversationsMessages()
            }
        }
    }

    private fun startCountdownTimer() {
        viewModelScope.launch {
            while (true) {
                val activeIdentity = _uiState.value.identity
                if (activeIdentity != null) {
                    if (!activeIdentity.autoRotationEnabled) {
                        _uiState.update { it.copy(formattedCountdown = "Disabled") }
                    } else {
                        val remainingMs = activeIdentity.remainingTimeMillis
                        val hours = remainingMs / (1000 * 60 * 60)
                        val minutes = (remainingMs / (1000 * 60)) % 60
                        val seconds = (remainingMs / 1000) % 60

                        val formatted = String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
                        _uiState.update { it.copy(formattedCountdown = formatted) }
                    }
                }
                delay(1000)
            }
        }
    }

    fun onSearchInputChanged(input: String) {
        val uppercaseInput = input.uppercase().take(8)
        _uiState.update {
            it.copy(
                searchPublicIdInput = uppercaseInput,
                searchError = null,
                requestSuccessMessage = null
            )
        }
    }

    fun sendChatRequest() {
        val targetId = _uiState.value.searchPublicIdInput.trim()
        val currentPublicId = _uiState.value.identity?.publicId ?: ""

        if (targetId.equals(currentPublicId, ignoreCase = true)) {
            _uiState.update { it.copy(searchError = "You cannot message yourself.") }
            return
        }

        if (targetId.length != 8) {
            _uiState.update { it.copy(searchError = "Public ID must be exactly 8 characters.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingRequest = true, searchError = null) }
            val result = chatRequestRepository.sendChatRequest(targetId)
            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isSendingRequest = false,
                            searchPublicIdInput = "",
                            requestSuccessMessage = "Request sent to $targetId!"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isSendingRequest = false,
                            searchError = error.message ?: "Failed to send request."
                        )
                    }
                }
            )
        }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(requestSuccessMessage = null) }
    }

    fun togglePinConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.togglePinConversation(conversationId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                realtimeRequestsChannel?.let { SupabaseNetworkClient.realtime.removeChannel(it) }
                realtimeConvsChannel?.let { SupabaseNetworkClient.realtime.removeChannel(it) }
                realtimeMessagesChannel?.let { SupabaseNetworkClient.realtime.removeChannel(it) }
            } catch (_: Exception) {}
        }
    }
}
