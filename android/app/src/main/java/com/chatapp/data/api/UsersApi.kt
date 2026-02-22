package com.chatapp.data.api

import com.chatapp.data.dto.ApiPreKeyBundle
import com.chatapp.data.dto.ContactsResponse
import com.chatapp.data.dto.KeyUploadRequest
import com.chatapp.data.dto.SyncContactsRequest
import com.chatapp.data.dto.UserResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface UsersApi {
    @GET("users/me")
    suspend fun getCurrentProfile(): UserResponse

    @GET("users/{userId}")
    suspend fun getUserById(@Path("userId") userId: String): UserResponse

    @PATCH("users/me")
    suspend fun updateProfile(@Body request: Map<String, String>): UserResponse

    @POST("keys/upload")
    suspend fun uploadKeys(@Body request: KeyUploadRequest): Unit

    @GET("keys/bundle/{userId}")
    suspend fun fetchPreKeyBundle(@Path("userId") userId: String): ApiPreKeyBundle

    @POST("users/fcm-token")
    suspend fun updatePushToken(@Body request: Map<String, String>): Unit

    @POST("users/{userId}/block")
    suspend fun blockUser(@Path("userId") userId: String): Unit

    @DELETE("users/{userId}/unblock")
    suspend fun unblockUser(@Path("userId") userId: String): Unit

    @GET("users/contacts")
    suspend fun getContacts(): ContactsResponse

    @POST("users/sync")
    suspend fun syncContacts(@Body request: SyncContactsRequest): ContactsResponse

    @POST("users/discover")
    suspend fun discoverContacts(@Body request: SyncContactsRequest): ContactsResponse

    @GET("users/directory")
    suspend fun getGlobalUsers(): ContactsResponse

    @POST("users/add")
    suspend fun addContactByUserId(@Body request: Map<String, String>): ContactsResponse
}
