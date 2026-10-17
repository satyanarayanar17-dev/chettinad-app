package com.example.portal

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        val occurrence = CareOccurrence("task-1", "task-1", "Fasting glucose", "MEASUREMENT", "RECORDED", "Measure before breakfast", "2026-10-15", "08:00", "2026-10-15T02:30:00Z", 0, "mg/dL")
        coEvery { api.today(any()) } returns listOf(
            occurrence,
            occurrence.copy(id = "task-2", task_id = "task-2", title = "Morning medicine", task_type = "MEDICATION", status = "TAKEN"),
            occurrence.copy(id = "task-3", task_id = "task-3", title = "Daily activity", task_type = "ACTIVITY", status = "COMPLETED"),
            occurrence.copy(id = "task-4", task_id = "task-4", status = "SKIPPED"),
            occurrence.copy(id = "task-5", task_id = "task-5", status = "DUE"),
            occurrence.copy(id = "task-6", task_id = "task-6", status = "MISSED")
        )
        val container = mockk<AppContainer>()
        every { container.portalApi } returns api
        every { container.tokenManager } returns TokenManager()
        compose.setContent { AppTheme(darkTheme = false) { PatientPortal(container, User("patient-1", "Anitha", Role.PATIENT)) {} } }
        compose.waitForIdle()
        compose.onNodeWithText("Hello, Anitha").assertIsDisplayed()
        compose.onNodeWithText("3 of 6 tasks completed").assertIsDisplayed()
        java.io.File("build/qa").mkdirs()
        compose.onRoot().captureRoboImage("build/qa/patient-home.png")
    }

    @Test fun staffQueueVisual() {
        System.setProperty("roborazzi.test.record", "true")
        val api = mockk<PortalApi>(relaxed = true)
        coEvery { api.appointments(any(), any()) } returns emptyList()
        coEvery { api.directory() } returns Directory()
        coEvery { api.labs(any(), any()) } returns emptyList()
        coEvery { api.queue() } returns listOf(
            QueuePatient("enc-1", "pat-1", "Anitha", "MRN-001", "WAITING_DOCTOR", 3, "OBST-001", wait_minutes = 8.0),
            QueuePatient("enc-2", "pat-2", "Meena", "MRN-002", "CONSULTATION", 4, "OBST-002", wait_minutes = 2.0))
        val container = mockk<AppContainer>()
        every { container.portalApi } returns api
        every { container.tokenManager } returns TokenManager()
        compose.setContent { AppTheme(darkTheme = false) { StaffPortal(container, User("doctor-1", "Dr. Priya", Role.DOCTOR, "Obstetrics & Gynaecology")) {} } }
        compose.waitForIdle()
        compose.onNodeWithText("Live queue").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/qa/staff-queue.png")
    }
    @Test fun measurementEntryLivesInCarePlanAndTrendsIsHistoryOnly() {
        val api = mockk<PortalApi>(relaxed = true)
        coEvery { api.appointments(any(), any()) } returns emptyList()
        coEvery { api.directory() } returns Directory()
        coEvery { api.carePlan(any()) } returns CarePlanBundle()
        coEvery { api.today(any()) } returns emptyList()
        coEvery { api.record(any()) } returns ClinicalBundle(PatientProfile("patient-1", "MRN-1", "Synthetic", "+919100000001", "1990-01-01", "Female"))
        val container = mockk<AppContainer>()
        every { container.portalApi } returns api
        every { container.tokenManager } returns TokenManager()
        compose.setContent { AppTheme { PatientPortal(container, User("patient-1", "Synthetic", Role.PATIENT)) {} } }
        compose.waitForIdle()
        compose.onNodeWithText("Log a measurement or activity").performScrollTo().performClick()
        compose.onNodeWithText("Log measurement or activity").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add your record").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/qa/patient-care-plan-entry.png")
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Trends").performClick()
        compose.onNodeWithText("Health trends").assertIsDisplayed()
        compose.onNodeWithText("Log measurement or activity").assertDoesNotExist()
        compose.onNodeWithText("Add your record").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/qa/patient-trends-history.png")
    }

}
