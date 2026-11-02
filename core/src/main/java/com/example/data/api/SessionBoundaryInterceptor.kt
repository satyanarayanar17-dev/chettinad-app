package com.example.data.api

import com.example.util.SessionRequestContext
import com.example.util.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Cookie
import okhttp3.CookieJar
import java.io.IOException

class SessionChangedException : IOException("The sign-in session changed.")

/** Checks the response before OkHttp's cookie jar can accept stale Set-Cookie headers. */
class SessionBoundaryInterceptor(private val tokens: TokenManager, private val cookies: CookieJar) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val context = chain.request().tag(SessionRequestContext::class.java)
        if (context != null && !tokens.isCurrent(context.generation)) throw SessionChangedException()
        val response = chain.proceed(chain.request())
        if (context == null) return response
        // Commit cookies under the same lock that advances the session. Strip them
        // from the response so BridgeInterceptor cannot save them a second time.
        if (!tokens.ifCurrent(context.generation) {
            val received = Cookie.parseAll(response.request.url, response.headers)
            if (received.isNotEmpty()) cookies.saveFromResponse(response.request.url, received)
        }) {
            response.close()
            throw SessionChangedException()
        }
        return response.newBuilder().removeHeader("Set-Cookie").build()
    }
}
