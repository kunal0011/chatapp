package com.chatapp.data.network

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONObject
import retrofit2.HttpException

object NetworkErrorMapper {
    fun toUserMessage(throwable: Throwable, fallback: String): String {
        if (throwable is IllegalStateException && !throwable.message.isNullOrBlank()) {
            return throwable.message!!
        }

        if (throwable is HttpException) {
            if (throwable.code() == 401) {
                return "Session expired. Please login again."
            }
            val parsed = parseHttpError(throwable)
            if (!parsed.isNullOrBlank()) {
                return parsed
            }
        }

        return when (throwable) {
            is UnknownHostException -> "Cannot reach server. Check host/IP and internet connection."
            is ConnectException -> "Connection refused. Ensure backend is running and reachable."
            is SocketTimeoutException -> "Connection timed out. Please retry."
            else -> throwable.message ?: fallback
        }
    }

    private fun parseHttpError(exception: HttpException): String? {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull() ?: return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val error = json.optString("error")
        if (error.isNotBlank() && error != "null") {
            if (error == "Validation failed") {
                val details = json.optJSONObject("details")
                if (details != null) {
                    val keys = details.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val messages = details.optJSONArray(key)
                        if (messages != null && messages.length() > 0) {
                            val first = messages.optString(0)
                            if (first.isNotBlank()) {
                                return "$key: $first"
                            }
                        }
                    }
                }
            }
            return error
        }

        val message = json.optString("message")
        return message.takeIf { it.isNotBlank() && it != "null" }
    }
}
