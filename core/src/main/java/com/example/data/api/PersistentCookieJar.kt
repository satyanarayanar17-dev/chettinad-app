package com.example.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {
    private val preferences = EncryptedSharedPreferences.create(context, "chettinad_cookies_v2",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val edit = preferences.edit()
        for (cookie in cookies) {
            val key = "${cookie.domain}|${cookie.path}|${cookie.name}"
            if (cookie.expiresAt <= System.currentTimeMillis()) edit.remove(key)
            else edit.putString(key, "${url.scheme}://${url.host}:${url.port}\n$cookie")
        }
        edit.apply()
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = preferences.all.values.mapNotNull { value ->
        val encoded = value as? String ?: return@mapNotNull null
        // Parse against the request only when the stored origin matches exactly.
        if (encoded.substringBefore('\n') != "${url.scheme}://${url.host}:${url.port}") return@mapNotNull null
        Cookie.parse(url, encoded.substringAfter('\n'))?.takeIf {
            it.expiresAt > System.currentTimeMillis() && it.matches(url)
        }
    }
    @Synchronized fun clear() { preferences.edit().clear().apply() }
}
