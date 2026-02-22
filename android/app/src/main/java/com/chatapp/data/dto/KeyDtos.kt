package com.chatapp.data.dto

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// ---------------------------------------------------------------
// Key Bundle DTOs
// ---------------------------------------------------------------

data class SignedPreKeyDto(
    @SerializedName("keyId") val keyId: Int,
    @SerializedName("publicKey") val publicKey: String,
    @SerializedName("signature") val signature: String
)

data class OneTimePreKeyDto(
    @SerializedName("keyId") val keyId: Int,
    @SerializedName("publicKey") val publicKey: String
)

data class KeyBundleRequest(
    @SerializedName("identityKey") val identityKey: String,
    @SerializedName("signedPreKey") val signedPreKey: SignedPreKeyDto,
    @SerializedName("oneTimePreKeys") val oneTimePreKeys: List<OneTimePreKeyDto>
)

data class OneTimePreKeysRequest(
    @SerializedName("oneTimePreKeys") val oneTimePreKeys: List<OneTimePreKeyDto>
)

data class KeyBundleResponse(
    @SerializedName("userId") val userId: String,
    @SerializedName("identityKey") val identityKey: String,
    @SerializedName("signedPreKey") val signedPreKey: SignedPreKeyDto,
    @SerializedName("oneTimePreKey") val oneTimePreKey: OneTimePreKeyDto?
)

data class OPKCountResponse(
    @SerializedName("oneTimePreKeyCount") val oneTimePreKeyCount: Int
)
