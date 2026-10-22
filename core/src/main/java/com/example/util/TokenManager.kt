package com.example.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.domain.model.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Each application has its own Keystore-backed preferences and Linux sandbox. */
class TokenManager(context: Context? = null) {
    private val preferences = context?.let {
        EncryptedSharedPreferences.create(it, "chettinad_session", MasterKey.Builder(it)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(User::class.java)
    @Volatile private var accessToken: String? = preferences?.getString("token", null)
    @Volatile private var generation = 0L
    private val _user = MutableStateFlow(runCatching { preferences?.getString("user", null)?.let(adapter::fromJson) }.getOrNull())
    val sessionUser: StateFlow<User?> = _user.asStateFlow()
    private val _isLoggedIn = MutableStateFlow(_user.value != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    @Synchronized fun saveToken(token: String) { generation++; writeToken(token) }
    private fun writeToken(token: String) { accessToken = token; preferences?.edit()?.putString("token", token)?.apply() }
    fun getToken(): String? = accessToken
    @Synchronized fun requestContext() = SessionRequestContext(generation, accessToken)
    @Synchronized fun isCurrent(epoch: Long): Boolean = generation == epoch
    @Synchronized fun ifCurrent(epoch: Long, action: () -> Unit): Boolean {
        if (generation != epoch) return false
        action(); return true
    }
    @Synchronized fun beginSession(): Long { clear(); return generation }
    @Synchronized fun saveTokenForSession(epoch: Long, token: String): Boolean {
        if (generation != epoch) return false
        writeToken(token); return true
    }
    @Synchronized fun replaceToken(context: SessionRequestContext, token: String): Boolean {
        if (generation != context.generation || accessToken != context.accessToken) return false
        writeToken(token); return true
    }
    @Synchronized fun saveUser(user: User) {
        preferences?.edit()?.putString("user", adapter.toJson(user))?.apply()
        _user.value = user; _isLoggedIn.value = true
    }
    fun getUser(): User? = _user.value
    @Synchronized fun saveUserForSession(epoch: Long, user: User): Boolean {
        if (generation != epoch) return false
        saveUser(user); return true
    }
    @Synchronized fun clearIfCurrent(epoch: Long): Boolean {
        if (generation != epoch) return false
        clear(); return true
    }
    fun saveDraft(account: String, encounter: String, json: String) { preferences?.edit()?.putString("draft:$account:$encounter", json)?.apply() }
    fun getDraft(account: String, encounter: String): String? = preferences?.getString("draft:$account:$encounter", null)
    fun removeDraft(account: String, encounter: String) { preferences?.edit()?.remove("draft:$account:$encounter")?.apply() }
    @Synchronized fun clear() {
        generation++
        accessToken = null
        // Retain account-scoped encrypted clinical drafts for explicit recovery after sign-in.
        preferences?.edit()?.remove("token")?.remove("user")?.apply()
        _user.value = null; _isLoggedIn.value = false
    }
}

/** A request stays bound to the explicit sign-in that created it, across token rotations. */
data class SessionRequestContext(val generation: Long, val accessToken: String?)
