package com.example.data.api

import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.util.TokenManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SessionBoundaryTest {
    private val server = MockWebServer()
    private val tokens = TokenManager()
    private val cookies = object : CookieJar {
        @Volatile var saved: List<Cookie> = emptyList()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { saved = cookies }
        override fun loadForRequest(url: HttpUrl) = saved.filter { it.matches(url) }
    }
    private lateinit var api: ChettinadApiService
    @Before fun setup() {
        server.start(); tokens.saveToken("old-token")
        tokens.saveUser(User("old-account", "Synthetic old", Role.DOCTOR, accountId = "old-account"))
        var service: ChettinadApiService? = null
        val client = OkHttpClient.Builder().cookieJar(cookies).addInterceptor(AuthInterceptor(tokens))
            .addNetworkInterceptor(SessionBoundaryInterceptor(tokens, cookies)).authenticator(TokenAuthenticator(tokens) { service!! }).build()
        api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build())).build().create(ChettinadApiService::class.java)
        service = api
    }
    @After fun close() { server.shutdown() }
    private fun signInAgain() {
        tokens.clear(); tokens.saveToken("new-account-token")
        tokens.saveUser(User("new-account", "Synthetic new", Role.DOCTOR, accountId = "new-account"))
    }
    @Test fun aLateProtectedResponseCannotRetryAsAnotherAccount() = runBlocking {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                started.countDown(); release.await(5, TimeUnit.SECONDS)
                return MockResponse().setResponseCode(401)
            }
        }
        val result = async(Dispatchers.IO) { runCatching { api.getSession() } }
        assertTrue(started.await(5, TimeUnit.SECONDS)); signInAgain(); release.countDown()
        assertTrue(result.await().isFailure)
        assertEquals(1, server.requestCount); assertEquals("new-account-token", tokens.getToken())
    }
    @Test fun aLateRefreshCannotReplaceNewCredentialsOrCookies() = runBlocking {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/auth/refresh") return MockResponse().setResponseCode(401)
                started.countDown(); release.await(5, TimeUnit.SECONDS)
                return MockResponse().setResponseCode(200).addHeader("Set-Cookie", "cc_refresh_token=old-rotated; Path=/")
                    .setBody("""{"access_token":"old-rotated","role":"DOCTOR","account_type":"STAFF","userId":"old-account"}""")
            }
        }
        val result = async(Dispatchers.IO) { runCatching { api.getSession() } }
        assertTrue(started.await(5, TimeUnit.SECONDS)); signInAgain()
        cookies.saved = listOf(Cookie.Builder().name("cc_refresh_token").value("new-secret").hostOnlyDomain(server.hostName).path("/").build())
        release.countDown(); assertTrue(result.await().isFailure)
        assertEquals("new-account-token", tokens.getToken()); assertEquals("new-secret", cookies.saved.single().value)
    }
    @Test fun aTemporaryRefreshFailureKeepsTheRecoverableSession() = runBlocking {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(if (request.path == "/auth/refresh") 503 else 401)
        }
        assertTrue(runCatching { api.getSession() }.isFailure)
        assertEquals("old-token", tokens.getToken()); assertEquals("old-account", tokens.getUser()?.id)
    }
    @Test fun refreshCannotChangeTheAccountEvenWhenTheRoleMatches() = runBlocking {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if (request.path == "/auth/refresh") MockResponse().setResponseCode(200)
                .setBody("""{"access_token":"wrong-account","role":"DOCTOR","account_type":"STAFF","userId":"someone-else"}""")
                else MockResponse().setResponseCode(401)
        }
        assertTrue(runCatching { api.getSession() }.isFailure); assertNull(tokens.getToken()); assertNull(tokens.getUser())
    }
}
