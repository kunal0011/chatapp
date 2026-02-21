package com.chatapp.data.api

import com.chatapp.data.dto.*
import retrofit2.http.*

interface ConversationsApi {
    @GET("conversations")
    suspend fun listConversations(): ConversationsResponseEnvelope

    @POST("conversations/direct")
    suspend fun createDirectConversation(@Body request: DirectConversationRequest): ConversationResponseEnvelope

    @GET("conversations/{conversationId}/messages")
    suspend fun getMessages(
        @Path("conversationId") conversationId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 30
    ): MessagesResponse

    @POST("conversations/{conversationId}/mute")
    suspend fun muteConversation(@Path("conversationId") conversationId: String): Unit

    @POST("conversations/{conversationId}/unmute")
    suspend fun unmuteConversation(@Path("conversationId") conversationId: String): Unit

    @DELETE("messages/{messageId}")
    suspend fun deleteMessage(@Path("messageId") messageId: String): Unit
    
    @GET("messages/search")
    suspend fun searchMessages(@Query("q") query: String): MessagesResponse
}
