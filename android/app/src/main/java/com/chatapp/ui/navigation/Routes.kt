package com.chatapp.ui.navigation

object Routes {
    const val AUTH = "auth"
    const val CONTACTS = "contacts"
    const val DIRECTORY = "directory"
    const val SETTINGS = "settings"
    const val CREATE_GROUP = "create_group"
    const val GROUP_INFO = "group_info/{conversationId}"
    const val ADD_MEMBERS = "add_members/{conversationId}"
    const val MESSAGE_INFO = "message_info/{messageId}"
    const val CONTACT_INFO = "contact_info/{userId}"
    const val CHAT = "chat"

    fun groupInfoDestination(conversationId: String): String {
        return "group_info/$conversationId"
    }

    fun addMembersDestination(conversationId: String): String {
        return "add_members/$conversationId"
    }

    fun messageInfoDestination(messageId: String): String {
        return "message_info/$messageId"
    }

    fun contactInfoDestination(userId: String): String {
        return "contact_info/$userId"
    }
    const val CHAT_ROUTE = "chat/{conversationId}/{contactName}"

    fun chatDestination(conversationId: String, contactName: String): String {
        return "$CHAT/$conversationId/$contactName"
    }
}
