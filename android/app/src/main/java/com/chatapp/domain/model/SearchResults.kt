package com.chatapp.domain.model

data class SearchResults(
    val messages: List<ChatMessage> = emptyList(),
    val contacts: List<User> = emptyList(),
    val groups: List<Conversation> = emptyList()
)
