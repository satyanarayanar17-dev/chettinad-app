package com.example.data.repository

import com.example.data.api.*
import com.example.util.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PatientAuthRepositoryTest {
    private val api = mockk<ChettinadApiService>()
    private val tokens = TokenManager()
    private val cookies = mockk<PersistentCookieJar>(relaxed = true)
    private fun repository(): AuthRepository {
        return AuthRepository(api, tokens, cookies)
    }

    @Test
    fun `patient identity is session patient id and challenge is forwarded`() = runTest {
        val repository = repository()
        coEvery { api.verifyOtp(any()) } returns LoginResponse("token", "patient", "patient", "account-1", "Patient", false)
        coEvery { api.getSession() } returns SessionResponse("account-1", "Patient", "PATIENT", null, "patient-42", false)
        val user = repository.verifyPatientOtp("+919876543210", "123456", "challenge-1").getOrThrow()
        assertEquals("patient-42", user.id)
        coVerify { api.verifyOtp(match { it["challenge_id"] == "challenge-1" }) }
        assertEquals("patient-42", tokens.getUser()?.id)
    }

    @Test
    fun `profile required remains actionable without publishing a session`() = runTest {
        val repository = repository()
        coEvery { api.verifyOtp(any()) } throws HttpException(Response.error<Any>(422,
            """{"error":{"code":"PROFILE_REQUIRED"}}""".toResponseBody("application/json".toMediaType())))
        val result = repository.verifyPatientOtp("+919876543210", "123456")
        assertEquals("PROFILE_REQUIRED", (result.exceptionOrNull() as PatientAuthException).code)
        assertNull(repository.currentUser.value)
        assertNull(tokens.getUser())
    }

    @Test
    fun `missing patient ownership clears token instead of using account id`() = runTest {
        val repository = repository()
        coEvery { api.verifyOtp(any()) } returns LoginResponse("token", "patient", "patient", "account-1", "Patient", false)
        coEvery { api.getSession() } returns SessionResponse("account-1", "Patient", "PATIENT", null, null, false)
        assertTrue(repository.verifyPatientOtp("+919876543210", "123456").isFailure)
        assertNull(repository.currentUser.value)
        assertNull(tokens.getToken())
        verify { cookies.clear() }
    }
}
