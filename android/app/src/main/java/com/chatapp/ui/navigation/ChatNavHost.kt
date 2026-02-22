package com.chatapp.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chatapp.ui.screens.auth.AuthScreen
import com.chatapp.ui.screens.chat.AddMembersScreen
import com.chatapp.ui.screens.chat.ChatScreen
import com.chatapp.ui.screens.chat.ConversationsScreen
import com.chatapp.ui.screens.chat.CreateGroupScreen
import com.chatapp.ui.screens.chat.GroupInfoScreen
import com.chatapp.ui.screens.contacts.ContactInfoScreen
import com.chatapp.ui.screens.contacts.DirectoryScreen
import com.chatapp.ui.screens.settings.SettingsScreen
import com.chatapp.ui.viewmodel.AppViewModel

@Composable
fun ChatNavHost() {
    val appViewModel: AppViewModel = hiltViewModel()
    val state by appViewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()

    LaunchedEffect(state.authenticated) {
        val targetRoute = if (state.authenticated) Routes.CONTACTS else Routes.AUTH
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (state.authenticated) Routes.CONTACTS else Routes.AUTH
    ) {
        composable(Routes.AUTH) {
            AuthScreen()
        }

        composable(Routes.CONTACTS) {
            ConversationsScreen(
                onNavigateToChat = { conversationId, contactName ->
                    val encodedName = Uri.encode(contactName)
                    navController.navigate(Routes.chatDestination(conversationId, encodedName))
                },
                onNavigateToDirectory = {
                    navController.navigate(Routes.DIRECTORY)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToCreateGroup = {
                    navController.navigate(Routes.CREATE_GROUP)
                },
                onLogout = appViewModel::logout
            )
        }

        composable(Routes.CREATE_GROUP) {
            CreateGroupScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { conversationId, contactName ->
                    val encodedName = Uri.encode(contactName)
                    navController.navigate(Routes.chatDestination(conversationId, encodedName)) {
                        popUpTo(Routes.CREATE_GROUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DIRECTORY) {
            DirectoryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { conversationId, contactName ->
                    val encodedName = Uri.encode(contactName)
                    navController.navigate(Routes.chatDestination(conversationId, encodedName)) {
                        popUpTo(Routes.DIRECTORY) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = appViewModel::logout
            )
        }

        composable(
            route = Routes.CHAT_ROUTE,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("contactName") { type = NavType.StringType }
            )
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onNavigateToGroupInfo = { conversationId ->
                    navController.navigate(Routes.groupInfoDestination(conversationId))
                },
                onNavigateToContactInfo = { userId ->
                    navController.navigate(Routes.contactInfoDestination(userId))
                }
            )
        }

        composable(
            route = Routes.GROUP_INFO,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) {
            GroupInfoScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAddMembers = { conversationId ->
                    navController.navigate(Routes.addMembersDestination(conversationId))
                },
                onExitGroup = {
                    navController.popBackStack(Routes.CONTACTS, inclusive = false)
                }
            )
        }

        composable(
            route = Routes.ADD_MEMBERS,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) {
            AddMembersScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CONTACT_INFO,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType }
            )
        ) {
            ContactInfoScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
