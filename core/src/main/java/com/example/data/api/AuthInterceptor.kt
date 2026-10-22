package com.example.data.api

import com.example.util.TokenManager
import com.example.util.SessionRequestContext
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val context = tokenManager.requestContext()
        val requestBuilder = chain.request().newBuilder().tag(SessionRequestContext::class.java, context)
        
        // Add bearer token if available
        if (context.accessToken != null) requestBuilder.header("Authorization", "Bearer ${context.accessToken}")
        else requestBuilder.removeHeader("Authorization")
        
        // Add correlation ID for backend logging
        requestBuilder.addHeader("X-Correlation-ID", UUID.randomUUID().toString())
        
        return chain.proceed(requestBuilder.build())
    }
}
