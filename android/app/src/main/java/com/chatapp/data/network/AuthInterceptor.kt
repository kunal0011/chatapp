package com.chatapp.data.network

import android.util.Log
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
        val request = chain.request()
        val path = request.url.encodedPath

        // Skip adding token for auth endpoints
        if (path.contains("/auth/login") ||
            path.contains("/auth/register") ||
            path.contains("/auth/refresh") ||
            path.contains("/auth/otp")) {
            Log.d("AuthInterceptor", "Auth endpoint detected ($path), skipping token injection.")
            return chain.proceed(request)
        }

        val session = runBlocking { sessionStore.getSessionOrNull() }

        val response = if (session != null) {
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(request)
        }

        // If we get a 401 and we are on an auth path (like refresh failed)
        // or if the authenticator already cleared the session, ensure we propagate that
        if (response.code == 401) {
            Log.e("AuthInterceptor", "401 Unauthorized received for path: $path")
            if (path.contains("/auth/refresh") || runBlocking { sessionStore.getSessionOrNull() } == null) {
                Log.e("AuthInterceptor", "Refresh failed or session null. Force clearing state.")
                runBlocking { sessionStore.clearSession() }
            }
        }

        return response
    }
}
