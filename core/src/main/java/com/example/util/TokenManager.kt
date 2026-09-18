package com.example.util

import com.example.domain.model.Role
import com.example.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenManager {
    private var accessToken: String? = null
    private var currentUser: User? = null
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun saveToken(token: String) {
        accessToken = token
    }

    fun getToken(): String? {
        return accessToken
    }

    fun saveUser(user: User) {
        currentUser = user
        _isLoggedIn.value = true
    }

    fun getUser(): User? {
        return currentUser
    }

    fun clear() {
        accessToken = null
        currentUser = null
        _isLoggedIn.value = false
    }
}
