package com.example.data.api

import com.example.domain.model.Role
import com.example.util.SessionRequestContext
import com.example.util.TokenManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

class TokenAuthenticator(private val tokenManager: TokenManager, private val apiServiceProvider: () -> ChettinadApiService) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.contains("auth/opd/otp/") || path.contains("auth/login/") || path.endsWith("auth/change-password")) return null
        val context = response.request.tag(SessionRequestContext::class.java) ?: tokenManager.requestContext()
        if (!tokenManager.isCurrent(context.generation) || response.request.header("Authorization") == null) return null
        var attempts = 0; var prior: Response? = response
        while (prior != null) { attempts++; prior = prior.priorResponse }
        if (attempts > 1 || path.contains("auth/refresh")) { tokenManager.clearIfCurrent(context.generation); return null }
        synchronized(this) {
            val current = tokenManager.requestContext()
            if (current.generation != context.generation || current.accessToken == null) return null
            if (response.request.header("Authorization") != "Bearer ${current.accessToken}")
                return response.request.newBuilder().header("Authorization", "Bearer ${current.accessToken}").build()
            // A transient failure must not erase an otherwise recoverable signed-in session.
            val refreshed = apiServiceProvider().refreshSync().execute()
            if (!tokenManager.isCurrent(context.generation)) return null
            if (refreshed.code() in listOf(400, 401, 403)) { tokenManager.clearIfCurrent(context.generation); return null }
            if (!refreshed.isSuccessful) throw IOException("Unable to refresh the hospital session.")
            val body = refreshed.body() ?: throw IOException("Empty session refresh response.")
            val user = tokenManager.getUser()
            val account = user?.accountId ?: user?.id?.takeIf { user.role != Role.PATIENT }
            if (user != null && (!body.role.equals(user.role.name, true) ||
                    !body.account_type.equals(if (user.role == Role.PATIENT) "patient" else "staff", true) ||
                    (account != null && body.userId != account))) {
                tokenManager.clearIfCurrent(context.generation); return null
            }
            if (body.access_token.isBlank() || !tokenManager.replaceToken(current, body.access_token)) return null
            return response.request.newBuilder().header("Authorization", "Bearer ${body.access_token}").build()
        }
    }
}
