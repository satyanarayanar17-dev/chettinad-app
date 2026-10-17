package com.example.portal

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.api.PersistentCookieJar
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.util.TokenManager
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Exercises the real Android Keystore; JVM mocks cannot establish at-rest encryption. */
@RunWith(AndroidJUnit4::class)
class NativeStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun encryptedSessionSurvivesRecreationAndLogoutClearsAccess() {
        val first = TokenManager(context)
        first.saveToken("synthetic-jwt"); first.saveUser(User("patient-1", "Synthetic", Role.PATIENT))
        first.saveDraft("doctor-a", "encounter-a", "synthetic draft")
        val restored = TokenManager(context)
        assertEquals("synthetic-jwt", restored.getToken()); assertEquals("patient-1", restored.getUser()?.id)
        assertNull(restored.getDraft("doctor-b", "encounter-a"))
        restored.clear(); assertNull(TokenManager(context).getToken()); assertNull(TokenManager(context).getUser())
    }
    @Test fun refreshCookieRetainsOriginSecureExpiryAndPath() {
        val jar = PersistentCookieJar(context); jar.clear()
        val url = "https://hospital.example/api/v1/auth/login/staff".toHttpUrl()
        jar.saveFromResponse(url, listOf(Cookie.parse(url,"cc_refresh=synthetic; Path=/api/v1/auth; Secure; HttpOnly; Max-Age=3600")!!))
        assertEquals(1, PersistentCookieJar(context).loadForRequest("https://hospital.example/api/v1/auth/refresh".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("https://other.example/api/v1/auth/refresh".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://hospital.example/api/v1/auth/refresh".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://hospital.example/api/v1/opd/queue".toHttpUrl()).isEmpty())
        jar.clear()
    }
}
