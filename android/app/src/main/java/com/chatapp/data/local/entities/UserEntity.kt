package com.chatapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chatapp.domain.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val phone: String,
    val displayName: String
)

fun UserEntity.toDomain() = User(id, phone, displayName)
fun User.toEntity() = UserEntity(id, phone, displayName)
