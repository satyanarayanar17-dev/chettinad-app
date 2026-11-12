package com.example.data.repository

import com.example.data.api.*
import com.example.util.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProductionAccessPolicyTest {
    private val api = mockk<ChettinadApiService>()
    private val tokens = TokenManager()
    private val cookies = mockk<PersistentCookieJar>(relaxed = true)
    private val repository = AuthRepository(api, tokens, cookies, requireLivePatientAccess = true)

    @Test fun demoBackendCannotReceivePatientPhoneFromAProductionLogin() = runTest {
        coEvery { api.accessConfiguration() } returns AccessConfiguration(true, true, true)
        val result = repository.requestPatientOtp("+919000000001")
        assertEquals("LIVE_ACCESS_NOT_READY", (result.exceptionOrNull() as PatientAuthException).code)
        coVerify(exactly = 0) { api.requestOtp(any()) }
    }

    @Test fun missingSmsBlocksCredentialsAndLeavesNoSession() = runTest {
        coEvery { api.accessConfiguration() } returns AccessConfiguration(false, false, true)
        assertTrue(repository.login(mapOf("username" to "synthetic-doctor", "password" to "synthetic-password")).isFailure)
        coVerify(exactly = 0) { api.loginStaff(any()) }
        assertNull(tokens.getToken())
        assertNull(tokens.getUser())
    }

    @Test fun aSelfRegistrationBackendCannotVerifyAProductionPatient() = runTest {
        coEvery { api.accessConfiguration() } returns AccessConfiguration(false, true, false)
        assertTrue(repository.verifyPatientOtp("+919000000001", "123456", "synthetic-challenge").isFailure)
        coVerify(exactly = 0) { api.verifyOtp(any()) }
    }

    @Test fun liveDoctorProvisionedSmsAllowsTheChallengeRequest() = runTest {
        coEvery { api.accessConfiguration() } returns AccessConfiguration(false, true, true)
        coEvery { api.requestOtp(any()) } returns OtpRequestResponse(true, 300, challenge_id = "synthetic-challenge")
        assertEquals("synthetic-challenge", repository.requestPatientOtp("+919000000001").getOrThrow().challenge_id)
    }

    @Test fun configurationFailureCannotFallBackToDemoAccess() = runTest {
        coEvery { api.accessConfiguration() } throws java.io.IOException("offline")
        assertTrue(repository.requestPatientOtp("+919000000001").isFailure)
        coVerify(exactly = 0) { api.requestOtp(any()) }
    }
}
