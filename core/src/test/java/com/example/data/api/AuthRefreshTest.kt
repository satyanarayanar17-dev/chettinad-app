package com.example.data.api

import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.util.TokenManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.atomic.AtomicInteger

class AuthRefreshTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ChettinadApiService
    private lateinit var portalApi: PortalApi
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenManager = TokenManager()
        tokenManager.saveToken("expired-token")
        
        val authInterceptor = AuthInterceptor(tokenManager)
        
        var serviceRef: ChettinadApiService? = null
        val tokenAuthenticator = TokenAuthenticator(tokenManager) { serviceRef!! }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        apiService = retrofit.create(ChettinadApiService::class.java)
        portalApi = retrofit.create(PortalApi::class.java)
        serviceRef = apiService
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testConcurrencyRefresh_OnlyOneRefreshRequestMade() = runBlocking {
        var refreshCount = 0
        
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path == "/auth/refresh") {
                    refreshCount++
                    MockResponse().setResponseCode(200).setBody("""
                        {
                            "access_token": "new-jwt-token",
                            "role": "doctor",
                            "account_type": "staff",
                            "must_change_password": false,
                            "token_type": "bearer"
                        }
                    """.trimIndent())
                } else {
                    val authHeader = request.getHeader("Authorization")
                    if (authHeader == "Bearer new-jwt-token") {
                        MockResponse().setResponseCode(200).setBody("""
                            {
                                "id": "enc-1",
                                "name": "User Session",
                                "role": "DOCTOR",
                                "must_change_password": false
                            }
                        """.trimIndent())
                    } else {
                        MockResponse().setResponseCode(401)
                    }
                }
            }
        }

        val jobs = (1..5).map {
            async(Dispatchers.IO) {
                apiService.getSession()
            }
        }
        
        val results = jobs.awaitAll()
        
        assertEquals(5, results.size)
        assertEquals("new-jwt-token", tokenManager.getToken())
        assertEquals("Exactly one refresh request should have been made", 1, refreshCount)
    }

    @Test
    fun invalidOtpIsNotRefreshedOrReplayed() = runBlocking {
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(401)
        }
        try {
            apiService.verifyOtp(mapOf("phone" to "+919876543210", "code" to "123456"))
            fail("Invalid OTP must fail")
        } catch (error: retrofit2.HttpException) {
            assertEquals(401, error.code())
        }
        assertEquals(1, mockWebServer.requestCount)
        assertEquals("/auth/opd/otp/verify", mockWebServer.takeRequest().path)
    }

    @Test fun incorrectCurrentPasswordIsNotReplayedAndKeepsTheSession() = runBlocking {
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(401)
        }
        try {
            portalApi.changePassword(mapOf("currentPassword" to "Incorrect", "newPassword" to "SyntheticNew2026!"))
            fail("Incorrect current password must fail")
        } catch (error: retrofit2.HttpException) { assertEquals(401, error.code()) }
        assertEquals(1, mockWebServer.requestCount)
        assertEquals("/auth/change-password", mockWebServer.takeRequest().path)
        assertEquals("expired-token", tokenManager.getToken())
    }

    @Test
    fun testRefreshReturns401_SessionTerminates() = runBlocking {
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(401)
            }
        }
        
        try {
            apiService.getSession()
            fail("Should throw HttpException")
        } catch (e: retrofit2.HttpException) {
            assertEquals(401, e.code())
        }
        
        assertNull(tokenManager.getToken())
    }
}
