package com.chatapp.core.di

import android.app.Application
import androidx.room.Room
import com.chatapp.data.local.ChatDatabase
import com.chatapp.data.local.dao.ConversationDao
import com.chatapp.data.local.dao.MemberDao
import com.chatapp.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(app: Application): ChatDatabase {
        return Room.databaseBuilder(
            app,
            ChatDatabase::class.java,
            "chatapp.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideConversationDao(db: ChatDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: ChatDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideMemberDao(db: ChatDatabase): MemberDao = db.memberDao()
}
