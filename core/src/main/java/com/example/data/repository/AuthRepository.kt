package com.example.data.repository

import com.example.data.api.SessionChangedException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.example.data.api.OtpRequestResponse
import com.example.data.api.ChettinadApiService
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.data.api.PersistentCookieJar
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.util.TokenManager
import kotlinx.coroutines.flow.StateFlow

open class AuthRepository(
    private val apiService: ChettinadApiService,
    private val tokenManager: TokenManager,
    private val cookieJar: PersistentCookieJar
) {
    private val sessionMutex = Mutex()
    val currentUser: StateFlow<User?> = tokenManager.sessionUser

    open suspend fun login(credentials: Map<String, String>): Result<User> = sessionMutex.withLock {
        val epoch = tokenManager.beginSession()
        cookieJar.clear()
        try {
            val response = apiService.loginStaff(credentials)
            if (!tokenManager.saveTokenForSession(epoch, response.access_token)) throw SessionChangedException()
            
            val role = Role.valueOf(response.role.uppercase())
            require(role != Role.PATIENT && response.account_type.equals("staff", true)) { "Staff role required" }
            val user = User(
                id = response.userId,
                name = response.name ?: response.userId,
                role = role,
                department = null, // Will fetch from /opd/session if needed
                isActive = true, accountId = response.userId
            )
            val session = apiService.getSession()
            require(session.id == response.userId && session.role.equals(role.name, ignoreCase = true))
            if (!tokenManager.saveUserForSession(epoch, user.copy(name = session.name, department = session.department, mustChangePassword = session.must_change_password == true || session.must_change_password == 1 || session.must_change_password == 1.0))) throw SessionChangedException()
            Result.success(tokenManager.getUser() ?: user)
        } catch (e: CancellationException) {
            clearLocalSession(epoch); throw e
        } catch (e: Exception) {
            clearLocalSession(epoch); Result.failure(e)
        }
    }
    
    open suspend fun requestPatientOtp(phone: String): Result<OtpRequestResponse> = try {
        Result.success(apiService.requestOtp(mapOf("phone" to phone)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(patientAuthError(e))
    }

    open suspend fun verifyPatientOtp(
        phone: String,
        code: String,
        challengeId: String? = null,
        profile: Map<String, String>? = null
    ): Result<User> = sessionMutex.withLock {
        val epoch = tokenManager.beginSession()
        cookieJar.clear()
        try {
            val payload = mutableMapOf<String, Any>("phone" to phone, "code" to code)
            challengeId?.let { payload["challenge_id"] = it }
            profile?.let { payload["profile"] = it }
            val response = apiService.verifyOtp(payload)
            require(response.role.equals("patient", ignoreCase = true) && response.account_type.equals("patient", true)) { "Unexpected patient role" }
            if (!tokenManager.saveTokenForSession(epoch, response.access_token)) throw SessionChangedException()
            // Account IDs and patient IDs differ. Only the session supplies record ownership.
            val session = apiService.getSession()
            require(session.id == response.userId && session.role.equals("PATIENT", ignoreCase = true)) { "Unexpected session role" }
            val patientId = requireNotNull(session.patient_id?.takeIf { it.isNotBlank() })
            val user = User(id = patientId, name = session.name, role = Role.PATIENT, isActive = true, accountId = response.userId)
            if (!tokenManager.saveUserForSession(epoch, user)) throw SessionChangedException()
            Result.success(user)
        } catch (e: CancellationException) {
            clearLocalSession(epoch)
            throw e
        } catch (e: Exception) {
            clearLocalSession(epoch)
            Result.failure(patientAuthError(e))
        }

    }

    private fun patientAuthError(error: Exception): Exception {
        if (error !is HttpException) return error
        val code = runCatching {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            moshi.adapter(PatientErrorEnvelope::class.java)
                .fromJson(error.response()?.errorBody()?.string().orEmpty())?.error?.code
        }.getOrNull()
        return PatientAuthException(code ?: "HTTP_${error.code()}", error)
    }

    open suspend fun logout(): Result<Unit> = sessionMutex.withLock {
        // Publish sign-out immediately; serialize new sign-in behind server/cookie cleanup.
        tokenManager.clear()
        withContext(NonCancellable) {
            try {
                apiService.logout()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            } finally { cookieJar.clear() }
        }
    }

    private fun clearLocalSession(epoch: Long) {
        if (tokenManager.clearIfCurrent(epoch)) cookieJar.clear()
    }

    open fun getStoredUser(): User? {
        return tokenManager.getUser()
    }
}

internal data class PatientErrorEnvelope(val error: PatientErrorCode)
internal data class PatientErrorCode(val code: String)
class PatientAuthException(val code: String, cause: Throwable? = null) : Exception(code, cause)
