package com.lactose.textme.presentation.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lactose.textme.core.database.AppDatabase
import com.lactose.textme.data.repository.ChatRequestRepositoryImpl
import com.lactose.textme.data.repository.ConversationRepositoryImpl
import com.lactose.textme.data.repository.IdentityRepositoryImpl
import com.lactose.textme.presentation.conversation.ConversationScreen
import com.lactose.textme.presentation.conversation.ConversationViewModel
import com.lactose.textme.presentation.home.HomeScreen
import com.lactose.textme.presentation.home.HomeViewModel
import com.lactose.textme.presentation.inbox.InboxScreen
import com.lactose.textme.presentation.inbox.InboxViewModel
import com.lactose.textme.presentation.settings.SettingsScreen
import com.lactose.textme.presentation.settings.SettingsViewModel
import com.lactose.textme.presentation.startup.StartupScreen
import com.lactose.textme.presentation.startup.StartupUiState
import com.lactose.textme.presentation.startup.StartupViewModel
import com.lactose.textme.presentation.welcome.WelcomeScreen

@Composable
fun TextMeNavHost(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current.applicationContext

    // Dependencies
    val db = remember { AppDatabase.getInstance(context) }
    val identityRepository = remember { IdentityRepositoryImpl(db.identityDao(), db.conversationDao(), db.chatRequestDao()) }
    val chatRequestRepository = remember {
        ChatRequestRepositoryImpl(db.chatRequestDao(), db.conversationDao(), db.identityDao())
    }
    val conversationRepository = remember {
        ConversationRepositoryImpl(db.conversationDao(), db.messageDao(), db.identityDao())
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Startup.route
    ) {
        composable(Screen.Startup.route) {
            val startupViewModel: StartupViewModel = viewModel(
                factory = SimpleViewModelFactory {
                    StartupViewModel(identityRepository)
                }
            )
            val uiState by startupViewModel.uiState.collectAsState()

            LaunchedEffect(uiState) {
                when (uiState) {
                    is StartupUiState.Authenticated -> {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Startup.route) { inclusive = true }
                        }
                    }
                    is StartupUiState.WelcomeNeeded -> {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(Screen.Startup.route) { inclusive = true }
                        }
                    }
                    else -> Unit
                }
            }

            StartupScreen(
                uiState = uiState,
                onRetry = startupViewModel::retry
            )
        }

        composable(Screen.Welcome.route) {
            val startupViewModel: StartupViewModel = viewModel(
                factory = SimpleViewModelFactory {
                    StartupViewModel(identityRepository)
                }
            )
            val uiState by startupViewModel.uiState.collectAsState()

            LaunchedEffect(uiState) {
                if (uiState is StartupUiState.Authenticated) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            }

            WelcomeScreen(
                isCreatingUser = uiState is StartupUiState.CreatingAccount,
                errorMessage = (uiState as? StartupUiState.Error)?.message,
                onStartMessaging = {
                    startupViewModel.createAnonymousUser()
                }
            )
        }

        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = SimpleViewModelFactory {
                    HomeViewModel(identityRepository, chatRequestRepository, conversationRepository)
                }
            )
            val uiState by homeViewModel.uiState.collectAsState()

            HomeScreen(
                uiState = uiState,
                onSearchInputChanged = homeViewModel::onSearchInputChanged,
                onSendRequest = homeViewModel::sendChatRequest,
                onNavigateToInbox = { navController.navigate(Screen.Inbox.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToConversation = { conversationId, publicId ->
                    navController.navigate(Screen.Conversation.createRoute(conversationId, publicId))
                },
                onTogglePinConversation = homeViewModel::togglePinConversation
            )
        }

        composable(Screen.Inbox.route) {
            val inboxViewModel: InboxViewModel = viewModel(
                factory = SimpleViewModelFactory {
                    InboxViewModel(chatRequestRepository)
                }
            )
            val uiState by inboxViewModel.uiState.collectAsState()

            InboxScreen(
                uiState = uiState,
                onAcceptRequest = { item ->
                    inboxViewModel.acceptRequest(item) { conversationId, publicId ->
                        navController.navigate(Screen.Conversation.createRoute(conversationId, publicId))
                    }
                },
                onRejectRequest = { item, alsoBlock ->
                    inboxViewModel.rejectRequest(item, alsoBlock)
                },
                onRefresh = inboxViewModel::refresh,
                onDismissError = inboxViewModel::clearErrorMessage,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Conversation.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("publicId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val publicId = backStackEntry.arguments?.getString("publicId") ?: ""

            val conversationViewModel: ConversationViewModel = viewModel(
                factory = SimpleViewModelFactory {
                    ConversationViewModel(conversationRepository, identityRepository)
                }
            )

            LaunchedEffect(conversationId, publicId) {
                conversationViewModel.initConversation(conversationId, publicId)
            }

            val uiState by conversationViewModel.uiState.collectAsState()

            ConversationScreen(
                uiState = uiState,
                onInputChanged = conversationViewModel::onInputTextChanged,
                onSendMessage = conversationViewModel::sendMessage,
                onDeleteAndExit = {
                    conversationViewModel.deleteAndExit {
                        navController.popBackStack(Screen.Home.route, false)
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SimpleViewModelFactory {
                    SettingsViewModel(identityRepository)
                }
            )
            val uiState by settingsViewModel.uiState.collectAsState()

            SettingsScreen(
                uiState = uiState,
                onToggleAutoRotation = settingsViewModel::toggleAutoRotation,
                onSaveChanges = settingsViewModel::saveChanges,
                onRotateIdentity = settingsViewModel::rotateIdentity,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

// Inline ViewModel Factory helper
class SimpleViewModelFactory<T : ViewModel>(
    private val creator: () -> T
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
        return creator() as VM
    }
}
