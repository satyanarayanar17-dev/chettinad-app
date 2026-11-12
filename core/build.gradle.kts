import java.net.URI

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.example.core"
  compileSdk = 36

  flavorDimensions.add("environment")
  productFlavors {
    create("demo") {
      dimension = "environment"
      buildConfigField("String", "API_BASE_URL", "\"${project.findProperty("DEMO_API_URL") ?: "https://chettinad-care-api.onrender.com/api/v1/"}\"")
    }
    create("staging") {
      dimension = "environment"
      val stagingUrl = project.findProperty("STAGING_API_URL") as? String ?: System.getenv("STAGING_API_URL")
      if (stagingUrl == null && gradle.startParameter.taskNames.any { it.contains("Staging", ignoreCase = true) }) {
         throw GradleException("STAGING_API_URL is required for staging builds.")
      }
      buildConfigField("String", "API_BASE_URL", "\"${stagingUrl ?: "https://staging.placeholder/"}\"")
    }
    create("production") {
      dimension = "environment"
      val prodUrl = project.findProperty("PRODUCTION_API_URL") as? String ?: System.getenv("PRODUCTION_API_URL")
      if (prodUrl == null && gradle.startParameter.taskNames.any { it.contains("Production", ignoreCase = true) }) {
         throw GradleException("PRODUCTION_API_URL is required for production builds.")
      }
      if (prodUrl != null && (!prodUrl.startsWith("https://") || !prodUrl.endsWith("/api/v1/"))) {
        throw GradleException("PRODUCTION_API_URL must be an HTTPS URL ending in /api/v1/.")
      }
      val producingRelease = gradle.startParameter.taskNames.any {
        it.contains("Production", ignoreCase = true) && it.contains("Release", ignoreCase = true)
      }
      if (producingRelease && providers.gradleProperty("PILOT_BACKEND_APPROVED").orNull != "true") {
        throw GradleException("Production release requires -PPILOT_BACKEND_APPROVED=true after verifying the live API, durable storage and real OTP delivery.")
      }
      val parsed = prodUrl?.let { URI(it) }
      if (parsed != null && (parsed.host.isNullOrBlank() || parsed.userInfo != null || parsed.rawQuery != null || parsed.fragment != null)) {
        throw GradleException("PRODUCTION_API_URL must have a real HTTPS host and no credentials, query or fragment.")
      }
      buildConfigField("String", "API_BASE_URL", "\"${prodUrl ?: "https://production.placeholder/"}\"")
    }
  }

  defaultConfig {
    minSdk = 24
    listOf("PRIVACY_POLICY_URL", "SUPPORT_URL").forEach { setting ->
      val value = providers.gradleProperty(setting).orNull ?: System.getenv(setting) ?: ""
      if (value.isNotBlank()) {
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "$setting must be a public HTTPS URL without credentials." }
      }
      if (gradle.startParameter.taskNames.any { it.contains("Production", true) && it.contains("Release", true) } && value.isBlank()) {
        throw GradleException("$setting must be configured for production releases.")
      }
      buildConfigField("String", setting, "\"$value\"")
    }
    buildConfigField("String", "PUBLISHER_NAME", "\"Chettinad Medical Sciences\"")
    buildConfigField("String", "SUPPORT_EMAIL", "\"satyanarayanr17@gmail.com\"")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation("io.mockk:mockk:1.13.8")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockwebserver)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
