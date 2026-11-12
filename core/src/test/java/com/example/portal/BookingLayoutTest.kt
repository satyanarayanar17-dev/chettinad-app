package com.example.portal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.api.*
import com.example.di.AppContainer
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.ui.theme.AppTheme
import com.example.util.TokenManager
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
class BookingLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val api = mockk<PortalApi>(relaxed = true)
    private fun launch(role: Role, scale: Float = 1f) {
        System.setProperty("roborazzi.test.record", "true"); java.io.File("build/qa").mkdirs()
        RuntimeEnvironment.setFontScale(scale)
        coEvery { api.directory() } returns Directory()
        coEvery { api.appointments(any(), any()) } returns emptyList()
        coEvery { api.queue() } returns emptyList()
        coEvery { api.patients(any(), any()) } returns emptyList()
        coEvery { api.record(any()) } returns ClinicalBundle(PatientProfile("p", "MRN-1", "Synthetic patient", "9000000001", "1990-01-01", "Female"))
        val container = mockk<AppContainer>(); every { container.portalApi } returns api; every { container.tokenManager } returns TokenManager()
        compose.setContent { AppTheme(darkTheme = false) {
            if (role == Role.PATIENT) PatientPortal(container, User("p","Synthetic patient",role)) {}
            else StaffPortal(container, User("doctor-a","Synthetic staff",role)) {}
        } }; compose.waitForIdle()
    }
    @Test fun patientAndVisitScreensHaveNoBookingControls() {
        launch(Role.PATIENT,1.3f)
        compose.onNodeWithText("Book an appointment").assertDoesNotExist()
        compose.onNodeWithText("You").performClick()
        compose.onNodeWithText("Appointments").performScrollTo().performClick()
        compose.onNodeWithText("Request appointment").assertDoesNotExist()
        coVerify(exactly=0) { api.book(any()) }
    }
    @Test fun staffVisitsHaveNoBookingControls() {
        launch(Role.ADMIN)
        compose.onNodeWithContentDescription("Open navigation").performClick(); compose.onNodeWithText("Appointments").performClick()
        compose.onNodeWithText("Book an appointment").assertDoesNotExist()
        coVerify(exactly=0) { api.book(any()) }
    }
    @Test fun doctorCanReachPatientRegistrationWithoutAvailability() {
        launch(Role.DOCTOR)
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Doctor schedules").assertDoesNotExist()
        compose.onNode(hasText("Patients") and hasAnyAncestor(hasTestTag("staff-menu"))).performClick()
        compose.onNodeWithText("Register new patient").performScrollTo().performClick()
        compose.onNodeWithText("Enable patient mobile sign-in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Register patient").assertIsNotEnabled()
        compose.onRoot().captureRoboImage("build/qa/doctor-patient-registration.png")
    }
    @Test fun staffMenuRemainsReachableWithLargerText() {
        launch(Role.ADMIN,1.3f)
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Reception").assertIsDisplayed(); compose.onNodeWithText("Administration").assertIsDisplayed()
        compose.onNodeWithTag("staff-menu").captureRoboImage("build/qa/staff-menu-large-text.png")
        compose.onNodeWithText("Profile").performScrollTo().performClick(); compose.onNodeWithText("My profile").assertIsDisplayed()
    }
}
