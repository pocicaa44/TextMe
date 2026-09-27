package com.lactose.textme

import com.lactose.textme.domain.model.Identity
import com.lactose.textme.domain.model.IdentityStatus
import com.lactose.textme.domain.repository.IdentityRepository
import com.lactose.textme.presentation.startup.StartupErrorType
import com.lactose.textme.presentation.startup.StartupUiState
import com.lactose.textme.presentation.startup.StartupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException

class StartupViewModelTest {

    private class FakeIdentityRepository(
        var restoreSessionResult: Result<Identity?> = Result.success(null),
        var createIdentityResult: Result<Identity> = Result.failure(IllegalStateException("Not set"))
    ) : IdentityRepository {
        override fun getActiveIdentityFlow(): Flow<Identity?> = flowOf(null)
        override suspend fun getActiveIdentity(): Identity? = null
        override suspend fun restoreSession(): Result<Identity?> = restoreSessionResult
        override suspend fun createIdentity(): Result<Identity> = createIdentityResult
        override suspend fun rotateIdentity(): Result<Identity> = createIdentityResult
        override suspend fun updateAutoRotation(autoRotationEnabled: Boolean): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun startup_with_existing_valid_session_transitions_to_Authenticated() {
        val canonicalIdentity = Identity(
            id = "id_1",
            userId = "auth_uuid_1",
            publicId = "A7K29P4X",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            status = IdentityStatus.ACTIVE
        )
        val fakeRepo = FakeIdentityRepository(restoreSessionResult = Result.success(canonicalIdentity))
        val viewModel = StartupViewModel(fakeRepo, Dispatchers.Unconfined)

        val state = viewModel.uiState.value
        assertTrue("Expected Authenticated state but got $state", state is StartupUiState.Authenticated)
        assertEquals("A7K29P4X", (state as StartupUiState.Authenticated).identity.publicId)
        assertEquals("auth_uuid_1", state.identity.userId)
    }

    @Test
    fun startup_with_first_launch_transitions_to_WelcomeNeeded() {
        val fakeRepo = FakeIdentityRepository(restoreSessionResult = Result.success(null))
        val viewModel = StartupViewModel(fakeRepo, Dispatchers.Unconfined)

        val state = viewModel.uiState.value
        assertTrue("Expected WelcomeNeeded state but got $state", state is StartupUiState.WelcomeNeeded)
    }

    @Test
    fun startup_with_offline_network_failure_transitions_to_ConnectionError_with_retry() {
        val fakeRepo = FakeIdentityRepository(
            restoreSessionResult = Result.failure(UnknownHostException("Unable to resolve host"))
        )
        val viewModel = StartupViewModel(fakeRepo, Dispatchers.Unconfined)

        val state = viewModel.uiState.value
        assertTrue("Expected Error state but got $state", state is StartupUiState.Error)
        val error = state as StartupUiState.Error
        assertEquals(StartupErrorType.CONNECTION, error.errorType)
        assertTrue(error.canRetry)
    }

    @Test
    fun startup_with_permission_error_42501_transitions_to_ProfileError() {
        val fakeRepo = FakeIdentityRepository(
            restoreSessionResult = Result.failure(
                IllegalStateException("Database permission denied (42501) for schema public")
            )
        )
        val viewModel = StartupViewModel(fakeRepo, Dispatchers.Unconfined)

        val state = viewModel.uiState.value
        assertTrue("Expected Error state but got $state", state is StartupUiState.Error)
        val error = state as StartupUiState.Error
        assertEquals(StartupErrorType.PROFILE, error.errorType)
        assertTrue(error.message.contains("42501") || error.message.contains("permission denied"))
    }

    @Test
    fun creating_anonymous_user_succeeds_and_transitions_to_Authenticated() {
        val createdIdentity = Identity(
            id = "id_new",
            userId = "auth_uuid_new",
            publicId = "K8M4X2KP",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            status = IdentityStatus.ACTIVE
        )
        val fakeRepo = FakeIdentityRepository(
            restoreSessionResult = Result.success(null),
            createIdentityResult = Result.success(createdIdentity)
        )
        val viewModel = StartupViewModel(fakeRepo, Dispatchers.Unconfined)

        assertEquals(StartupUiState.WelcomeNeeded, viewModel.uiState.value)

        viewModel.createAnonymousUser()

        val finalState = viewModel.uiState.value
        assertTrue("Expected Authenticated state but got $finalState", finalState is StartupUiState.Authenticated)
        assertEquals("K8M4X2KP", (finalState as StartupUiState.Authenticated).identity.publicId)
    }
}
