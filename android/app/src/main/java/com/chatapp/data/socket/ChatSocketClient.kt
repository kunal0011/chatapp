package com.chatapp.data.socket

import com.chatapp.BuildConfig
import com.chatapp.data.dto.ApiMessage
import com.chatapp.data.dto.SocketMessageEnvelope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.socket.client.IO
import io.socket.client.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class SocketReadEvent(
    @SerializedName("conversationId") val conversationId: String,
    @SerializedName("readerId") val readerId: String
)

data class SocketTypingEvent(
    @SerializedName("conversationId") val conversationId: String,
    @SerializedName("userId") val userId: String,
    val isTyping: Boolean
)

data class MessageAckEvent(
    @SerializedName("clientTempId") val clientTempId: String,
    @SerializedName("messageId") val messageId: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("status") val status: String
)

@Singleton
class ChatSocketClient @Inject constructor(
    private val gson: Gson
) {
    private val mutex = Mutex()
    private var socket: Socket? = null
    private val messageEvents = MutableSharedFlow<ApiMessage>(extraBufferCapacity = 64)
    private val ackEvents = MutableSharedFlow<MessageAckEvent>(extraBufferCapacity = 32)
    private val readEvents = MutableSharedFlow<SocketReadEvent>(extraBufferCapacity = 32)
    private val typingEvents = MutableSharedFlow<SocketTypingEvent>(extraBufferCapacity = 32)
    private val connectionState = MutableStateFlow(false)

    suspend fun connect(accessToken: String) {
        mutex.withLock {
            if (socket?.connected() == true) return

            val options = IO.Options().apply {
                auth = mapOf("token" to accessToken)
                reconnection = true
                reconnectionAttempts = 10
                timeout = 10000
            }

            val created = IO.socket(BuildConfig.SOCKET_URL, options)
            created.on(Socket.EVENT_CONNECT) { connectionState.value = true }
            created.on(Socket.EVENT_DISCONNECT) { connectionState.value = false }
            
            created.on("message:new") { args ->
                if (args.isNotEmpty()) {
                    parseIncomingMessage(args[0])?.let { messageEvents.tryEmit(it) }
                }
            }
            
            created.on("message:ack") { args ->
                if (args.isNotEmpty()) {
                    runCatching {
                        val event = gson.fromJson(args[0].toString(), MessageAckEvent::class.java)
                        ackEvents.tryEmit(event)
                    }
                }
            }

            created.on("conversation:read") { args ->
                if (args.isNotEmpty()) {
                    runCatching {
                        val event = gson.fromJson(args[0].toString(), SocketReadEvent::class.java)
                        readEvents.tryEmit(event)
                    }
                }
            }

            created.on("typing:start") { args ->
                if (args.isNotEmpty()) {
                    runCatching {
                        val event = gson.fromJson(args[0].toString(), SocketTypingEvent::class.java).copy(isTyping = true)
                        typingEvents.tryEmit(event)
                    }
                }
            }

            created.on("typing:stop") { args ->
                if (args.isNotEmpty()) {
                    runCatching {
                        val event = gson.fromJson(args[0].toString(), SocketTypingEvent::class.java).copy(isTyping = false)
                        typingEvents.tryEmit(event)
                    }
                }
            }

            created.on("message:update") { args ->
                if (args.isNotEmpty()) {
                    parseIncomingMessage(args[0])?.let { messageEvents.tryEmit(it) }
                }
            }

            created.connect()
            socket = created
        }
    }

    suspend fun disconnect() {
        mutex.withLock {
            socket?.disconnect()
            socket?.off()
            socket = null
            connectionState.value = false
        }
    }

    suspend fun joinConversation(conversationId: String) {
        mutex.withLock { socket?.emit("conversation:join", conversationId) }
    }

    suspend fun markAsRead(conversationId: String) {
        mutex.withLock {
            val payload = JSONObject().apply { put("conversationId", conversationId) }
            socket?.emit("message:read", payload)
        }
    }

    suspend fun sendTypingStatus(conversationId: String, isTyping: Boolean) {
        mutex.withLock {
            val event = if (isTyping) "typing:start" else "typing:stop"
            val payload = JSONObject().apply { put("conversationId", conversationId) }
            socket?.emit(event, payload)
        }
    }

    suspend fun sendMessage(conversationId: String, content: String, clientTempId: String, parentId: String? = null) {
        mutex.withLock {
            val payload = JSONObject().apply {
                put("conversationId", conversationId)
                put("content", content)
                put("clientTempId", clientTempId)
                if (parentId != null) put("parentId", parentId)
            }
            socket?.emit("message:send", payload)
        }
    }

    suspend fun editMessage(messageId: String, content: String) {
        mutex.withLock {
            val payload = JSONObject().apply {
                put("messageId", messageId)
                put("content", content)
            }
            socket?.emit("message:edit", payload)
        }
    }

    suspend fun unsendMessage(messageId: String) {
        mutex.withLock {
            val payload = JSONObject().apply { put("messageId", messageId) }
            socket?.emit("message:unsend", payload)
        }
    }

    suspend fun sendReaction(messageId: String, emoji: String) {
        mutex.withLock {
            val payload = JSONObject().apply {
                put("messageId", messageId)
                put("emoji", emoji)
            }
            socket?.emit("message:reaction", payload)
        }
    }

    fun observeMessages(): Flow<ApiMessage> = messageEvents.asSharedFlow()
    fun observeAcks(): Flow<MessageAckEvent> = ackEvents.asSharedFlow()
    fun observeReadEvents(): Flow<SocketReadEvent> = readEvents.asSharedFlow()
    fun observeTypingEvents(): Flow<SocketTypingEvent> = typingEvents.asSharedFlow()
    fun observeConnection(): StateFlow<Boolean> = connectionState

    private fun parseIncomingMessage(payload: Any): ApiMessage? {
        return runCatching {
            val json = if (payload is JSONObject) payload.toString() else gson.toJson(payload)
            gson.fromJson(json, SocketMessageEnvelope::class.java).message
        }.getOrNull()
    }
}
