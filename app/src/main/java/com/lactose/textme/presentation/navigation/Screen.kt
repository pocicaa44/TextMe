package com.lactose.textme.presentation.navigation

sealed class Screen(val route: String) {
    object Startup : Screen("startup")
    object Welcome : Screen("welcome")
    object Home : Screen("home")
    object Inbox : Screen("inbox")
    object Conversation : Screen("conversation/{conversationId}/{publicId}") {
        fun createRoute(conversationId: String, publicId: String): String {
            return "conversation/$conversationId/$publicId"
        }
    }
    object Settings : Screen("settings")
}
