package com.chatapp.data.network

import com.chatapp.data.store.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val session = runBlocking { sessionStore.getSessionOrNull() }
        val request = chain.request()
        
        return if (session != null) {
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(request)
        }
    }
}
