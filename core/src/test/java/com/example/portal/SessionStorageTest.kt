package com.example.portal

import androidx.test.core.app.ApplicationProvider
import com.example.data.api.PersistentCookieJar
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.util.TokenManager
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionStorageTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun `encrypted session survives recreation and logout clears access`() {
        val first = TokenManager(context)
        first.saveToken("synthetic-jwt"); first.saveUser(User("patient-1", "Synthetic", Role.PATIENT))
        first.saveDraft("doctor-a", "encounter-a", "synthetic draft")
        val restored = TokenManager(context)
        assertEquals("synthetic-jwt", restored.getToken())
        assertEquals("patient-1", restored.getUser()?.id)
        assertNull(restored.getDraft("doctor-b", "encounter-a"))
        restored.clear()
        assertNull(TokenManager(context).getToken()); assertNull(TokenManager(context).getUser())
    }
    @Test fun `refresh cookies keep origin secure expiry and path restrictions`() {
        val jar = PersistentCookieJar(context); jar.clear()
        val api = "https://hospital.example/api/v1/auth/login/staff".toHttpUrl()
        jar.saveFromResponse(api, listOf(Cookie.parse(api, "cc_refresh=synthetic; Path=/api/v1/auth; Secure; HttpOnly; Max-Age=3600")!!))
        assertEquals(1, PersistentCookieJar(context).loadForRequest("https://hospital.example/api/v1/auth/refresh".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("https://other.example/api/v1/auth/refresh".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://hospital.example/api/v1/auth/refresh".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://hospital.example/api/v1/opd/queue".toHttpUrl()).isEmpty())
        jar.clear()
    }
}
