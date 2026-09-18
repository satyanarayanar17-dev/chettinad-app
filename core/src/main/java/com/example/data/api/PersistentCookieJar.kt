package com.example.data.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "chettinad_cookies",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val editor = sharedPreferences.edit()
        for (cookie in cookies) {
            // Simplistic storage, fine for cc_opd_refresh
            editor.putString(cookie.name, cookie.value)
        }
        editor.apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = mutableListOf<Cookie>()
        for ((name, value) in sharedPreferences.all) {
            if (value is String) {
                // In production, we'd properly serialize/deserialize cookies, but this is a simplified version
                val cookie = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host) // Assume same domain
                    .path("/")
                    .build()
                cookies.add(cookie)
            }
        }
        return cookies
    }
    
    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
