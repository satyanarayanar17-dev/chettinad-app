package com.example.data.repository

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class AuthRepository(
    private val apiService: ChettinadApiService,
    private val tokenManager: TokenManager,
    private val cookieJar: PersistentCookieJar
) {
    val currentUser: StateFlow<User?> = tokenManager.user

    open suspend fun login(credentials: Map<String, String>): Result<User> {
        return try {
            val response = apiService.loginStaff(credentials)
            tokenManager.saveToken(response.access_token)
            
            val role = Role.valueOf(response.role.uppercase())
            require(role != Role.PATIENT) { "Staff role required" }
            val user = User(
                id = response.userId,
                name = response.name ?: response.userId,
                role = role,
                department = null, // Will fetch from /opd/session if needed
                isActive = true
            )
            val session = apiService.getSession()
            require(session.role.equals(role.name, ignoreCase = true))
            tokenManager.saveUser(user.copy(name = session.name, department = session.department, mustChangePassword = session.must_change_password == true || session.must_change_password == 1 || session.must_change_password == 1.0))
            Result.success(tokenManager.getUser() ?: user)
        } catch (e: CancellationException) {
            clearLocalSession(); throw e
        } catch (e: Exception) {
            clearLocalSession(); Result.failure(e)
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
    ): Result<User> = try {
        val payload = mutableMapOf<String, Any>("phone" to phone, "code" to code)
        challengeId?.let { payload["challenge_id"] = it }
        profile?.let { payload["profile"] = it }
        val response = apiService.verifyOtp(payload)
        require(response.role.equals("patient", ignoreCase = true)) { "Unexpected patient role" }
        tokenManager.saveToken(response.access_token)
        // Account IDs and patient IDs differ. Only the session supplies record ownership.
        val session = apiService.getSession()
        require(session.role.equals("PATIENT", ignoreCase = true)) { "Unexpected session role" }
        val patientId = requireNotNull(session.patient_id?.takeIf { it.isNotBlank() })
        val user = User(id = patientId, name = session.name, role = Role.PATIENT, isActive = true)
        tokenManager.saveUser(user)
        Result.success(user)
    } catch (e: CancellationException) {
        clearLocalSession()
        throw e
    } catch (e: Exception) {
        clearLocalSession()
        Result.failure(patientAuthError(e))
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
    }

    open fun getStoredUser(): User? {
        return tokenManager.getUser()
    }
}

internal data class PatientErrorEnvelope(val error: PatientErrorCode)
internal data class PatientErrorCode(val code: String)
class PatientAuthException(val code: String, cause: Throwable? = null) : Exception(code, cause)
