package com.example.patient

import android.app.Application
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer

class PatientApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
