package com.chatapp.data.api

import com.chatapp.data.dto.KeyBundleRequest
import com.chatapp.data.dto.KeyBundleResponse
import com.chatapp.data.dto.OPKCountResponse
import com.chatapp.data.dto.OneTimePreKeysRequest
import retrofit2.Response
import retrofit2.http.*

/**
 * REST API interface for E2EE key management.
 *
 * All endpoints communicate with /users prefix (mounted on the usersRouter + keysRouter).
 */
interface KeysApi {

    /**
     * Upload or replace the current user's public key bundle.
     * Called once on first login and whenever the signed prekey is rotated.
     */
    @PUT("users/me/keys")
    suspend fun uploadKeyBundle(@Body bundle: KeyBundleRequest): Response<Unit>

    /**
     * Fetch a recipient's public key bundle.
     * The server atomically removes one OPK from the bundle and returns it.
     */
    @GET("users/{userId}/keys")
    suspend fun getKeyBundle(@Path("userId") userId: String): KeyBundleResponse

    /**
     * Replenish the one-time prekey pool on the server.
     */
    @POST("users/me/keys/one-time")
    suspend fun replenishOneTimePreKeys(@Body request: OneTimePreKeysRequest): Response<Unit>

    /**
     * Check how many OPKs remain on the server. Used to trigger replenishment.
     */
    @GET("users/me/keys/count")
    suspend fun getOPKCount(): OPKCountResponse

    // ---------------------------------------------------------------
    // Group E2EE (SenderKey) Endpoints
    // ---------------------------------------------------------------

    /** 
     * Distribute own SenderKey to specified group members (1:1 encrypted blobs). 
     */
    @POST("groups/{groupId}/sender-keys")
    suspend fun distributeSenderKeys(
        @Path("groupId") groupId: String,
        @Body request: com.chatapp.data.dto.DistributeSenderKeysRequest
    ): Response<Unit>

    /** 
     * Fetch pending SenderKeys that other members have distributed to us in this group. 
     */
    @GET("groups/{groupId}/sender-keys")
    suspend fun fetchSenderKeys(
        @Path("groupId") groupId: String
    ): com.chatapp.data.dto.FetchSenderKeysResponse

    /** 
     * Revoke own SenderKey before rotation (e.g., after a member is removed). 
     */
    @DELETE("groups/{groupId}/sender-keys/mine")
    suspend fun revokeMySenderKey(
        @Path("groupId") groupId: String
    ): Response<Unit>
}
