package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.core.BuildConfig
import com.example.data.api.AuthInterceptor
import com.example.data.api.ChettinadApiService
import com.example.data.api.PersistentCookieJar
import com.example.data.api.TokenAuthenticator
import com.example.data.db.ChettinadDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.DoctorRepository
import com.example.data.repository.NurseRepository
import com.example.data.repository.AdminRepository
import com.example.data.repository.PatientRepository
import com.example.util.TokenManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

interface AppContainer {
    val tokenManager: TokenManager
    val cookieJar: PersistentCookieJar
    val chettinadApiService: ChettinadApiService
    val chettinadDatabase: ChettinadDatabase
    val authRepository: AuthRepository
    val doctorRepository: DoctorRepository
    val nurseRepository: NurseRepository
    val adminRepository: AdminRepository
    val patientRepository: PatientRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val tokenManager: TokenManager by lazy {
        TokenManager()
    }
    
    override val cookieJar: PersistentCookieJar by lazy {
        PersistentCookieJar(context)
    }

    private val authInterceptor by lazy {
        AuthInterceptor(tokenManager)
    }
    
    private val tokenAuthenticator by lazy {
        TokenAuthenticator(tokenManager) { chettinadApiService }
    }

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    override val chettinadApiService: ChettinadApiService by lazy {
        retrofit.create(ChettinadApiService::class.java)
    }

    override val chettinadDatabase: ChettinadDatabase by lazy {
        Room.databaseBuilder(
            context,
            ChettinadDatabase::class.java,
            "chettinad_database"
        ).build()
    }

    override val authRepository: AuthRepository by lazy {
        AuthRepository(chettinadApiService, tokenManager, cookieJar)
    }

    override val doctorRepository: DoctorRepository by lazy {
        DoctorRepository(chettinadApiService, chettinadDatabase.draftNoteDao())
    }

    override val nurseRepository: NurseRepository by lazy {
        NurseRepository(chettinadApiService)
    }

    override val adminRepository: AdminRepository by lazy {
        AdminRepository(chettinadApiService)
    }

    override val patientRepository: PatientRepository by lazy {
        PatientRepository(chettinadApiService)
    }
}
