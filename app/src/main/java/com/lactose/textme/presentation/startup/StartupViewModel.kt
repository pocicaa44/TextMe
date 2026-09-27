package com.lactose.textme.presentation.startup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lactose.textme.core.network.SupabaseNetworkClient
import com.lactose.textme.domain.model.Identity
import com.lactose.textme.domain.repository.IdentityRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class StartupErrorType {
    CONFIGURATION,
    CONNECTION,
    AUTHENTICATION,
    PROFILE
}

sealed interface StartupUiState {
    data class Initializing(val message: String = "Initializing TextMe...") : StartupUiState
    data class CreatingAccount(val message: String = "Generating your anonymous Public ID...") : StartupUiState
    data class Authenticated(val identity: Identity) : StartupUiState
    object WelcomeNeeded : StartupUiState
    data class Error(
        val errorType: StartupErrorType,
        val title: String,
        val message: String,
        val canRetry: Boolean = true
    ) : StartupUiState
}

class StartupViewModel(
    private val identityRepository: IdentityRepository,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _uiState = MutableStateFlow<StartupUiState>(StartupUiState.Initializing())
    val uiState: StateFlow<StartupUiState> = _uiState.asStateFlow()

    init {
        initStartup()
    }

    fun initStartup() {
        viewModelScope.launch(coroutineDispatcher) {
            _uiState.update { StartupUiState.Initializing("Connecting to Supabase...") }

            // 1. Verify Configuration
            if (!SupabaseNetworkClient.isConfigured()) {
                Log.e(TAG, "[Startup] Supabase configuration is missing or blank.")
                _uiState.update {
                    StartupUiState.Error(
                        errorType = StartupErrorType.CONFIGURATION,
                        title = "Configuration Required",
                        message = "Supabase URL or Anon Key is missing. Please check local.properties and rebuild.",
                        canRetry = false
                    )
                }
                return@launch
            }

            // 2. Restore Auth Session & Profile
            Log.d(TAG, "[Startup] Restoring authentication session...")
            val result = identityRepository.restoreSession()
            result.fold(
                onSuccess = { identity ->
                    if (identity != null) {
                        Log.i(TAG, "[Startup] Session restored successfully: Public ID=${identity.publicId}")
                        _uiState.update { StartupUiState.Authenticated(identity) }
                    } else {
                        Log.i(TAG, "[Startup] No existing session found. Proceeding to Welcome.")
                        _uiState.update { StartupUiState.WelcomeNeeded }
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "[Startup] Session restoration failed: ${error.message}", error)
                    val errorState = classifyError(error)
                    _uiState.update { errorState }
                }
            )
        }
    }

    fun createAnonymousUser() {
        viewModelScope.launch(coroutineDispatcher) {
            _uiState.update { StartupUiState.CreatingAccount() }
            Log.d(TAG, "[Startup] Creating anonymous user and identity...")

            val result = identityRepository.createIdentity()
            result.fold(
                onSuccess = { identity ->
                    Log.i(TAG, "[Startup] Created identity successfully: Public ID=${identity.publicId}")
                    _uiState.update { StartupUiState.Authenticated(identity) }
                },
                onFailure = { error ->
                    Log.e(TAG, "[Startup] Account creation failed: ${error.message}", error)
                    val errorState = classifyError(error)
                    _uiState.update { errorState }
                }
            )
        }
    }

    fun retry() {
        initStartup()
    }

    private fun classifyError(throwable: Throwable): StartupUiState.Error {
        val message = throwable.message ?: ""
        return when {
            message.contains("42501") || message.contains("permission denied for schema public") -> {
                StartupUiState.Error(
                    errorType = StartupErrorType.PROFILE,
                    title = "Database Permission Denied",
                    message = "PostgreSQL returned 42501: permission denied for schema public. Please execute the GRANT statements in supabase/schema.sql in your Supabase SQL Editor."
                )
            }
            throwable is UnknownHostException || throwable is ConnectException || throwable is SocketTimeoutException ||
                    message.contains("Unable to resolve host") || message.contains("Failed to connect") -> {
                StartupUiState.Error(
                    errorType = StartupErrorType.CONNECTION,
                    title = "Connection Failed",
                    message = "Unable to connect to Supabase. Please check your internet connection and try again."
                )
            }
            message.contains("configuration", ignoreCase = true) -> {
                StartupUiState.Error(
                    errorType = StartupErrorType.CONFIGURATION,
                    title = "Configuration Error",
                    message = message
                )
            }
            else -> {
                StartupUiState.Error(
                    errorType = StartupErrorType.AUTHENTICATION,
                    title = "Initialization Error",
                    message = throwable.localizedMessage ?: "An error occurred while communicating with Supabase."
                )
            }
        }
    }

    companion object {
        private const val TAG = "StartupVM"
    }
}
