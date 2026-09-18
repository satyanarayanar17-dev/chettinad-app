package com.example.data.api

import com.example.util.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.atomic.AtomicBoolean

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val apiServiceProvider: () -> ChettinadApiService
) : Authenticator {

    private val isRefreshing = AtomicBoolean(false)

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loops if refresh itself returns 401
        if (response.request.url.encodedPath.contains("auth/refresh")) {
            tokenManager.clear()
            return null
        }

        // Avoid multiple simultaneous refresh requests
        synchronized(this) {
            val currentToken = tokenManager.getToken()

            // If the token has already been refreshed by another thread, just retry
            if (response.request.header("Authorization") != "Bearer $currentToken") {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${tokenManager.getToken()}")
                    .build()
            }

            try {
                // Perform synchronous refresh call
                val refreshResponse = apiServiceProvider().refreshSync().execute()

                if (refreshResponse.isSuccessful) {
                    val newToken = refreshResponse.body()?.access_token
                    if (newToken != null) {
                        tokenManager.saveToken(newToken)
                        return response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors during refresh, let it fail
            }
            
            // Refresh failed or returned 401
            tokenManager.clear()
            return null
        }
    }
}
