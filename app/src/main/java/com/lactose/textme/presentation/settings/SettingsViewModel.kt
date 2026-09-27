package com.lactose.textme.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lactose.textme.domain.model.Identity
import com.lactose.textme.domain.repository.IdentityRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

data class SettingsUiState(
    val identity: Identity? = null,
    val formattedCountdown: String = "--h --m",
    val isAutoRotationEnabled: Boolean = false,
    val savedAutoRotationEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val isRotating: Boolean = false,
    val rotationMessage: String? = null,
    val saveMessage: String? = null
) {
    val hasUnsavedChanges: Boolean
        get() = isAutoRotationEnabled != savedAutoRotationEnabled && !isSaving
}

class SettingsViewModel(
    private val identityRepository: IdentityRepository,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadIdentity()
        startTimer()
    }

    private fun loadIdentity() {
        viewModelScope.launch(coroutineDispatcher) {
            identityRepository.getActiveIdentityFlow().collect { active ->
                if (active != null) {
                    _uiState.update { current ->
                        current.copy(
                            identity = active,
                            isAutoRotationEnabled = if (current.hasUnsavedChanges) current.isAutoRotationEnabled else active.autoRotationEnabled,
                            savedAutoRotationEnabled = active.autoRotationEnabled,
                            formattedCountdown = if (active.autoRotationEnabled) current.formattedCountdown else "Disabled"
                        )
                    }
                }
            }
        }
    }

    private fun startTimer() {
        viewModelScope.launch(coroutineDispatcher) {
            while (true) {
                val active = _uiState.value.identity
                if (active != null) {
                    if (!active.autoRotationEnabled) {
                        _uiState.update { it.copy(formattedCountdown = "Disabled") }
                    } else {
                        val remainingMs = active.remainingTimeMillis
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

    fun toggleAutoRotation(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isAutoRotationEnabled = enabled,
                saveMessage = null
            )
        }
    }

    fun saveChanges() {
        val currentEnabled = _uiState.value.isAutoRotationEnabled
        viewModelScope.launch(coroutineDispatcher) {
            _uiState.update { it.copy(isSaving = true) }
            val result = identityRepository.updateAutoRotation(currentEnabled)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedAutoRotationEnabled = currentEnabled,
                            saveMessage = "Settings saved successfully! Automatic rotation is now ${if (currentEnabled) "ENABLED" else "DISABLED"}."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveMessage = error.message ?: "Failed to save settings."
                        )
                    }
                }
            )
        }
    }

    fun rotateIdentity() {
        viewModelScope.launch(coroutineDispatcher) {
            _uiState.update { it.copy(isRotating = true) }
            val result = identityRepository.rotateIdentity()
            result.fold(
                onSuccess = { newIdentity ->
                    _uiState.update {
                        it.copy(
                            isRotating = false,
                            identity = newIdentity,
                            rotationMessage = "Identity rotated! New Public ID: ${newIdentity.publicId}"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRotating = false,
                            rotationMessage = error.message ?: "Failed to rotate identity."
                        )
                    }
                }
            )
        }
    }
}
