package com.lactose.textme

import com.lactose.textme.domain.model.Identity
import com.lactose.textme.domain.model.IdentityStatus
import com.lactose.textme.domain.repository.IdentityRepository
import com.lactose.textme.presentation.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class SettingsViewModelTest {

    private class FakeIdentityRepository(
        initialIdentity: Identity? = null
    ) : IdentityRepository {
        val identityFlow = MutableStateFlow(initialIdentity)
        var lastUpdatedAutoRotation: Boolean? = null
        var updateAutoRotationCallCount = 0

        override fun getActiveIdentityFlow(): Flow<Identity?> = identityFlow
        override suspend fun getActiveIdentity(): Identity? = identityFlow.value
        override suspend fun restoreSession(): Result<Identity?> = Result.success(identityFlow.value)
        override suspend fun createIdentity(): Result<Identity> = Result.failure(UnsupportedOperationException())
        override suspend fun rotateIdentity(): Result<Identity> = Result.failure(UnsupportedOperationException())

        override suspend fun updateAutoRotation(autoRotationEnabled: Boolean): Result<Unit> {
            updateAutoRotationCallCount++
            lastUpdatedAutoRotation = autoRotationEnabled
            val current = identityFlow.value
            if (current != null) {
                identityFlow.value = current.copy(autoRotationEnabled = autoRotationEnabled)
            }
            return Result.success(Unit)
        }
    }

    private fun createTestIdentity(autoRotation: Boolean = false): Identity {
        return Identity(
            id = "id_1",
            userId = "user_1",
            publicId = "A7K29P4X",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            status = IdentityStatus.ACTIVE,
            autoRotationEnabled = autoRotation
        )
    }

    @Test
    fun initial_state_reflects_active_identity_with_no_unsaved_changes() {
        val identity = createTestIdentity(autoRotation = false)
        val fakeRepo = FakeIdentityRepository(identity)
        val viewModel = SettingsViewModel(fakeRepo, Dispatchers.Unconfined)

        val state = viewModel.uiState.value
        assertFalse("Auto rotation switch should be off by default", state.isAutoRotationEnabled)
        assertFalse("Saved auto rotation state should be off", state.savedAutoRotationEnabled)
        assertFalse("Should have no unsaved changes initially", state.hasUnsavedChanges)
        assertEquals("Countdown should be Disabled when auto-rotation is off", "Disabled", state.formattedCountdown)
    }

    @Test
    fun toggleAutoRotation_sets_hasUnsavedChanges_true_without_saving() {
        val identity = createTestIdentity(autoRotation = false)
        val fakeRepo = FakeIdentityRepository(identity)
        val viewModel = SettingsViewModel(fakeRepo, Dispatchers.Unconfined)

        // User flips the switch to ON
        viewModel.toggleAutoRotation(true)

        val state = viewModel.uiState.value
        assertTrue("Switch state should be toggled to true", state.isAutoRotationEnabled)
        assertFalse("Saved state must NOT change until save button is pressed", state.savedAutoRotationEnabled)
        assertTrue("hasUnsavedChanges must be true to enable Save Changes button", state.hasUnsavedChanges)
        assertEquals("Repository updateAutoRotation must NOT be called by toggle alone", 0, fakeRepo.updateAutoRotationCallCount)
    }

    @Test
    fun toggleAutoRotation_reverted_resets_hasUnsavedChanges_to_false() {
        val identity = createTestIdentity(autoRotation = false)
        val fakeRepo = FakeIdentityRepository(identity)
        val viewModel = SettingsViewModel(fakeRepo, Dispatchers.Unconfined)

        // Toggle ON then back OFF
        viewModel.toggleAutoRotation(true)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.toggleAutoRotation(false)
        assertFalse("Reverting switch back to saved value clears unsaved changes", viewModel.uiState.value.hasUnsavedChanges)
        assertEquals(0, fakeRepo.updateAutoRotationCallCount)
    }

    @Test
    fun saveChanges_persists_to_repository_and_clears_unsaved_changes() {
        val identity = createTestIdentity(autoRotation = false)
        val fakeRepo = FakeIdentityRepository(identity)
        val viewModel = SettingsViewModel(fakeRepo, Dispatchers.Unconfined)

        // Toggle ON
        viewModel.toggleAutoRotation(true)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Click Save Changes button
        viewModel.saveChanges()

        val state = viewModel.uiState.value
        assertEquals("Repository updateAutoRotation must be called once", 1, fakeRepo.updateAutoRotationCallCount)
        assertEquals(true, fakeRepo.lastUpdatedAutoRotation)
        assertTrue("Saved state must now be true", state.savedAutoRotationEnabled)
        assertFalse("Unsaved changes must be false after saving", state.hasUnsavedChanges)
        assertNotNull("Save message must be present", state.saveMessage)
        assertTrue(state.saveMessage!!.contains("ENABLED"))
    }

    @Test
    fun disabling_autoRotation_and_saving_persists_false_to_repository() {
        val identity = createTestIdentity(autoRotation = true)
        val fakeRepo = FakeIdentityRepository(identity)
        val viewModel = SettingsViewModel(fakeRepo, Dispatchers.Unconfined)

        // Toggle OFF
        viewModel.toggleAutoRotation(false)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Save
        viewModel.saveChanges()

        val state = viewModel.uiState.value
        assertEquals(1, fakeRepo.updateAutoRotationCallCount)
        assertEquals(false, fakeRepo.lastUpdatedAutoRotation)
        assertFalse(state.savedAutoRotationEnabled)
        assertFalse(state.hasUnsavedChanges)
        assertTrue(state.saveMessage!!.contains("DISABLED"))
    }
}
