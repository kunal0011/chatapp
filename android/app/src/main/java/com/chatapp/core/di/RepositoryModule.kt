package com.chatapp.core.di

import com.chatapp.data.repository.AuthRepositoryImpl
import com.chatapp.data.repository.ChatRepositoryImpl
import com.chatapp.data.repository.ContactsRepositoryImpl
import com.chatapp.domain.repository.AuthRepository
import com.chatapp.domain.repository.ChatRepository
import com.chatapp.domain.repository.ContactsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindContactsRepository(impl: ContactsRepositoryImpl): ContactsRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
