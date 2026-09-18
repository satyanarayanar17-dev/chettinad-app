package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ChettinadApiContractTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ChettinadApiService
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        apiService = retrofit.create(ChettinadApiService::class.java)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testLogin_v2() = runBlocking {
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody("""
                {
                    "access_token": "jwt-token-123",
                    "role": "doctor",
                    "account_type": "staff",
                    "userId": "dr_smith",
                    "name": "Dr. Smith",
                    "must_change_password": false,
                    "token_type": "bearer"
                }
            """.trimIndent())
        mockWebServer.enqueue(mockResponse)

        val response = apiService.loginStaff(mapOf("username" to "dr_smith", "password" to "password"))
        assertEquals("jwt-token-123", response.access_token)
        assertEquals("doctor", response.role)
        
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/login/staff", request.path)
    }

    @Test
    fun testClinicalConflict_Returns409() = runBlocking {
        val mockResponse = MockResponse()
            .setResponseCode(409)
            .setBody("""
                {
                  "error": {
                    "code": "STALE_STATE",
                    "message": "Encounter has been updated by another user.",
                    "details": null
                  },
                  "meta": { "correlation_id": "123" }
                }
            """.trimIndent())
        mockWebServer.enqueue(mockResponse)

        val write = ConsultationWrite(__v = 1, data = ConsultationData(complaint = "Fever"))
        try {
            apiService.saveConsultationDraft("enc-1", write)
            fail("Should have thrown HTTP exception")
        } catch (e: retrofit2.HttpException) {
            assertEquals(409, e.code())
        }
        
        val request = mockWebServer.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/opd/encounters/enc-1/consultation", request.path)
    }
}
