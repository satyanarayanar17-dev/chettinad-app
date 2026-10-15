package com.example.portal

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.AppTheme
import com.example.data.api.*
import com.example.di.AppContainer
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.util.TokenManager
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class PortalVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun patientHomeVisual() {
        System.setProperty("roborazzi.test.record", "true")
        val api = mockk<PortalApi>(relaxed = true)
        coEvery { api.appointments(any(), any()) } returns listOf(Appointment("apt-1", "2026-10-16T05:00:00Z", "CONFIRMED", 1, doctor_name = "Dr. Priya", department_name = "Obstetrics & Gynaecology", reason = "Antenatal review"))
        coEvery { api.directory() } returns Directory()
        coEvery { api.carePlan(any()) } returns CarePlanBundle()
        coEvery { api.record(any()) } returns ClinicalBundle(PatientProfile("patient-1", "MRN-1", "Anitha", "+919100000001", "1990-01-01", "Female"))
        coEvery { api.today(any()) } returns listOf(CareOccurrence("task-1", "task-1", "Fasting glucose", "MEASUREMENT", "DUE", "Measure before breakfast", "2026-10-15", "08:00", "2026-10-15T02:30:00Z", 0, "mg/dL"))
        val container = mockk<AppContainer>()
        every { container.portalApi } returns api
        every { container.tokenManager } returns TokenManager()
        compose.setContent { AppTheme(darkTheme = false) { PatientPortal(container, User("patient-1", "Anitha", Role.PATIENT)) {} } }
        compose.waitForIdle()
        java.io.File("build/qa").mkdirs()
        compose.onRoot().captureRoboImage("build/qa/patient-home.png")
    }
}
