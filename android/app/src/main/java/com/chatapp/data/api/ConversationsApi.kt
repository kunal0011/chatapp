package com.chatapp.data.api

import com.chatapp.data.dto.AddMembersRequest
import com.chatapp.data.dto.ConversationResponseEnvelope
import com.chatapp.data.dto.ConversationsResponseEnvelope
import com.chatapp.data.dto.DirectConversationRequest
import com.chatapp.data.dto.GroupConversationRequest
import com.chatapp.data.dto.MembersResponse
import com.chatapp.data.dto.MessageInfoResponse
import com.chatapp.data.dto.MessagesResponse
import com.chatapp.data.dto.UnifiedSearchResponse
import com.chatapp.data.dto.UpdateGroupRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ConversationsApi {
    @GET("conversations")
    suspend fun listConversations(): ConversationsResponseEnvelope

    @POST("conversations/direct")
    suspend fun createDirectConversation(@Body request: DirectConversationRequest): ConversationResponseEnvelope

    @POST("conversations/group")
    suspend fun createGroup(@Body request: GroupConversationRequest): ConversationResponseEnvelope

    @POST("conversations/{id}/members")
    suspend fun addMembers(@Path("id") id: String, @Body request: AddMembersRequest): Unit

    @DELETE("conversations/{id}/members/{userId}")
    suspend fun removeMember(@Path("id") id: String, @Path("userId") userId: String): Unit

    @PATCH("conversations/{id}/members/{userId}/role")
    suspend fun updateMemberRole(@Path("id") id: String, @Path("userId") userId: String, @Body body: Map<String, String>): Unit

    @PATCH("conversations/{id}")
    suspend fun updateGroup(@Path("id") id: String, @Body request: UpdateGroupRequest): ConversationResponseEnvelope

    @GET("conversations/{id}/members")
    suspend fun getMembers(@Path("id") id: String): MembersResponse

    @GET("messages/{conversationId}/history")
    suspend fun getMessages(
        @Path("conversationId") conversationId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 30
    ): MessagesResponse

    @POST("messages/conversations/{conversationId}/mute")
    suspend fun muteConversation(@Path("conversationId") conversationId: String): Unit

    @POST("messages/conversations/{conversationId}/unmute")
    suspend fun unmuteConversation(@Path("conversationId") conversationId: String): Unit

        @DELETE("messages/{messageId}")
        suspend fun deleteMessage(@Path("messageId") messageId: String): Unit
    
        @GET("messages/{id}/info")
        suspend fun getMessageInfo(@Path("id") id: String): MessageInfoResponse
        
        @GET("messages/search")
    
    suspend fun searchMessages(@Query("q") query: String): UnifiedSearchResponse
}
