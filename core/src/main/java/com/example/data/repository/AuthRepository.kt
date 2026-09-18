package com.example.data.repository

import com.example.data.api.ChettinadApiService
import com.example.data.api.PersistentCookieJar
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.util.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class AuthRepository(
    private val apiService: ChettinadApiService,
    private val tokenManager: TokenManager,
    private val cookieJar: PersistentCookieJar
) {
    private val _currentUser = MutableStateFlow<User?>(tokenManager.getUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    open suspend fun login(credentials: Map<String, String>): Result<User> {
        return try {
            val response = apiService.loginStaff(credentials)
            tokenManager.saveToken(response.access_token)
            
            val role = Role.valueOf(response.role.uppercase())
            val user = User(
                id = response.userId,
                name = response.name ?: response.userId,
                role = role,
                department = null, // Will fetch from /opd/session if needed
                isActive = true
            )
            tokenManager.saveUser(user)
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    open suspend fun logout(): Result<Unit> {
        return try {
            apiService.logout()
            clearLocalSession()
            Result.success(Unit)
        } catch (e: Exception) {
            clearLocalSession()
            Result.failure(e)
        }
    }

    private fun clearLocalSession() {
        tokenManager.clear()
        cookieJar.clear()
        _currentUser.value = null
    }

    open fun getStoredUser(): User? {
        return tokenManager.getUser()
    }
}
