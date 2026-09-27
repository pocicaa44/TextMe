package com.lactose.textme.presentation.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lactose.textme.core.network.SupabaseNetworkClient
import com.lactose.textme.domain.model.Message
import com.lactose.textme.domain.repository.ConversationRepository
import com.lactose.textme.domain.repository.IdentityRepository
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class IdentityChangeAlert {
    NONE,
    SELF_CHANGED,
    OTHER_CHANGED
}

data class ConversationUiState(
    val conversationId: String = "",
    val participantPublicId: String = "",
    val currentIdentityId: String = "",
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val isUnavailable: Boolean = false,
    val identityChangeAlert: IdentityChangeAlert = IdentityChangeAlert.NONE,
    val errorMessage: String? = null
)

class ConversationViewModel(
    private val conversationRepository: ConversationRepository,
    private val identityRepository: IdentityRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var realtimeConvChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun initConversation(conversationId: String, publicId: String) {
        _uiState.update {
            it.copy(
                conversationId = conversationId,
                participantPublicId = publicId
            )
        }

        viewModelScope.launch(dispatcher) {
            val identity = identityRepository.getActiveIdentity()
            if (identity != null) {
                _uiState.update { it.copy(currentIdentityId = identity.id) }
            }

            conversationRepository.getConversationFlow(conversationId).collect { conv ->
                if (conv == null) return@collect
                val activeIdentity = identityRepository.getActiveIdentity()
                if (activeIdentity != null) {
                    val isParticipant = activeIdentity.id == conv.participantAId || activeIdentity.id == conv.participantBId
                    if (!isParticipant) {
                        _uiState.update { it.copy(identityChangeAlert = IdentityChangeAlert.SELF_CHANGED) }
                    } else if (conv.terminatedReason == "identity_rotated") {
                        val isSelf = conv.terminatedBy == activeIdentity.id
                        _uiState.update {
                            it.copy(
                                identityChangeAlert = if (isSelf) IdentityChangeAlert.SELF_CHANGED else IdentityChangeAlert.OTHER_CHANGED
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch(dispatcher) {
            conversationRepository.getMessagesFlow(conversationId).collect { list ->
                _uiState.update { it.copy(messages = list) }
            }
        }

        // 1. Immediate sync & mark as read
        conversationRepository.setActiveOpenConversation(conversationId)
        viewModelScope.launch(dispatcher) {
            conversationRepository.markConversationAsRead(conversationId)
            conversationRepository.syncMessages(conversationId)
            conversationRepository.syncConversationStatus(conversationId)
        }

        // 2. Realtime channel subscription for instant incoming messages and status (< 50ms)
        viewModelScope.launch(dispatcher) {
            try {
                if (SupabaseNetworkClient.isConfigured()) {
                    try {
                        realtimeConvChannel?.let { SupabaseNetworkClient.realtime.removeChannel(it) }
                    } catch (_: Exception) {}

                    val channel = SupabaseNetworkClient.realtime.channel("conv_room_$conversationId")
                    realtimeConvChannel = channel

                    val convChangeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "conversations"
                        filter("id", FilterOperator.EQ, conversationId)
                    }
                    val msgChangeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "messages"
                        filter("conversation_id", FilterOperator.EQ, conversationId)
                    }
                    channel.subscribe()

                    launch {
                        convChangeFlow.collect {
                            conversationRepository.syncConversationStatus(conversationId)
                        }
                    }
                    launch {
                        msgChangeFlow.collect {
                            conversationRepository.syncMessages(conversationId)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Background fallback polling sync while active
        viewModelScope.launch(dispatcher) {
            while (isActive) {
                delay(3000)
                conversationRepository.syncMessages(conversationId)
                conversationRepository.syncConversationStatus(conversationId)
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        if (_uiState.value.identityChangeAlert != IdentityChangeAlert.NONE) return
        val text = _uiState.value.inputText.trim()
        val convId = _uiState.value.conversationId
        if (text.isEmpty() || convId.isEmpty()) return

        // Clear input text immediately for snappy typing UX
        _uiState.update { it.copy(inputText = "", isSending = true) }

        viewModelScope.launch(dispatcher) {
            val result = conversationRepository.sendMessage(convId, text)
            _uiState.update { it.copy(isSending = false) }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.message ?: "Failed to send message"
                    )
                }
            }
        }
    }

    fun deleteAndExit(onNavHome: () -> Unit) {
        val convId = _uiState.value.conversationId
        viewModelScope.launch(dispatcher) {
            conversationRepository.deleteConversation(convId)
            onNavHome()
        }
    }

    override fun onCleared() {
        super.onCleared()
        conversationRepository.setActiveOpenConversation(null)
        viewModelScope.launch(dispatcher) {
            try {
                realtimeConvChannel?.let { SupabaseNetworkClient.realtime.removeChannel(it) }
            } catch (_: Exception) {}
        }
    }
}
