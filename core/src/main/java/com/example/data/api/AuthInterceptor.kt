package com.example.data.api

import com.example.util.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        
        // Add bearer token if available
        tokenManager.getToken()?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        
        // Add correlation ID for backend logging
        requestBuilder.addHeader("X-Correlation-ID", UUID.randomUUID().toString())
        
        return chain.proceed(requestBuilder.build())
    }
}
