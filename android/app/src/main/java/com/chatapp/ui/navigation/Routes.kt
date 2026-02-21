package com.chatapp.ui.navigation

object Routes {
    const val AUTH = "auth"
    const val CONTACTS = "contacts"
    const val DIRECTORY = "directory"
    const val SETTINGS = "settings"
    const val CHAT = "chat"
    const val CHAT_ROUTE = "chat/{conversationId}/{contactName}"

    fun chatDestination(conversationId: String, contactName: String): String {
        return "$CHAT/$conversationId/$contactName"
    }
}
