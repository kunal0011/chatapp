package com.chatapp.data.dto

import com.chatapp.domain.model.User
import com.google.gson.annotations.SerializedName

data class ContactsResponse(
    @SerializedName("contacts") val contacts: List<ApiUser>
)

data class SyncContactsRequest(
    @SerializedName("phones") val phones: List<String>
)

data class UserResponse(
    @SerializedName("user") val user: ApiUser
)

data class ApiPreKeyBundle(
    @SerializedName("bundle") val bundle: BundleData
)

data class BundleData(
    @SerializedName("userId") val userId: String,
    @SerializedName("registrationId") val registrationId: Int,
    @SerializedName("identityKey") val identityKey: String,
    @SerializedName("signedPreKey") val signedPreKey: ApiSignedPreKey,
    @SerializedName("oneTimePreKey") val oneTimePreKey: ApiOneTimePreKey?
)

data class ApiSignedPreKey(
    @SerializedName("keyId") val keyId: Int,
    @SerializedName("publicKey") val publicKey: String,
    @SerializedName("signature") val signature: String
)

data class ApiOneTimePreKey(
    @SerializedName("keyId") val keyId: Int,
    @SerializedName("publicKey") val publicKey: String
)

data class KeyUploadRequest(
    @SerializedName("registrationId") val registrationId: Int,
    @SerializedName("identityKey") val identityKey: String,
    @SerializedName("signedPreKey") val signedPreKey: ApiSignedPreKey,
    @SerializedName("oneTimePreKeys") val oneTimePreKeys: List<ApiOneTimePreKey>
)
