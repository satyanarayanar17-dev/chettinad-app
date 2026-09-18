package com.example.domain.model

enum class AppEnvironment {
    DEMO,
    STAGING,
    PRODUCTION
}

object EnvironmentConfig {
    val current: AppEnvironment = when (com.example.core.BuildConfig.FLAVOR) {
        "demo" -> AppEnvironment.DEMO
        "staging" -> AppEnvironment.STAGING
        "production" -> AppEnvironment.PRODUCTION
        else -> AppEnvironment.DEMO
    }
}
