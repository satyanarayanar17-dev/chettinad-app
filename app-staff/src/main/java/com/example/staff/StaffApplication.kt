package com.example.staff

import android.app.Application
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer

class StaffApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
