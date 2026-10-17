package com.example.portal

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Looper
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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi")
class BookingLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val api = mockk<PortalApi>(relaxed = true)
    private val patient = PatientProfile("patient-a", "MRN-DEMO-001", "Anitha Raman", "9000000001", "1990-01-01", "Female")
    private val doctor = Doctor("doctor-a", "Dr. Meenakshi Ramanujam", "Obstetrics & Gynaecology")
    private val otherDoctor = Doctor("doctor-b", "Dr. Priya Subramanian", "Obstetrics & Gynaecology")
    private val time = Slot(todayIndia() + "T05:00:00Z", todayIndia() + "T05:15:00Z", "Room 2")

    private fun launch(staff: Boolean = true, scale: Float = 1f) {
        System.setProperty("roborazzi.test.record", "true")
        java.io.File("build/qa").mkdirs()
        RuntimeEnvironment.setFontScale(scale)
        coEvery { api.directory() } returns Directory(doctors = listOf(doctor, otherDoctor))
        coEvery { api.appointments(any(), any()) } returns emptyList()
        coEvery { api.queue() } returns emptyList()
        coEvery { api.patients("Anitha", any()) } returns listOf(patient)
        coEvery { api.slots(doctor.id, todayIndia()) } returns listOf(time)
        coEvery { api.record(patient.id) } returns ClinicalBundle(patient)
        val container = mockk<AppContainer>()
        every { container.portalApi } returns api
        every { container.tokenManager } returns TokenManager()
        compose.setContent {
            AppTheme(darkTheme = false) {
                if (staff) StaffPortal(container, User("admin-a", "Demo Administrator", Role.ADMIN)) {}
                else PatientPortal(container, User(patient.id, patient.name, Role.PATIENT)) {}
            }
        }
        compose.waitForIdle()
    }
    private fun openStaffBooking() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Appointments").performClick()
        compose.onNodeWithText("Book an appointment").performClick()
        compose.waitForIdle()
    }
    private fun selectPatient() {
        compose.onNodeWithText("Patient name or MRN").performTextInput("Anitha")
        compose.onNodeWithText("Find patient").performClick()
        compose.onNodeWithText(patient.name).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("booking-screen").captureRoboImage("build/qa/staff-booking-patient.png")
        compose.onNodeWithText("Continue").performClick()
    }
    private fun selectVisit() {
        compose.onNodeWithText("Doctor").performClick()
        compose.onNodeWithText(doctor.name).performClick()
        compose.onNodeWithText("Find available times").performScrollTo().performClick()
        compose.onNodeWithText("Available time").performScrollTo().performClick()
        compose.onNodeWithText("10:30 am", ignoreCase = true).performClick()
        compose.waitForIdle()
    }
    @Test fun staffBookingReviewsTheSelectedIdentityAndSendsOneRequest() {
        launch(); openStaffBooking()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Doctor").assertDoesNotExist()
        selectPatient()
        compose.onNodeWithContentDescription("Choose visit date").performScrollTo().performClick()
        val calendar = ShadowDialog.getLatestDialog() as DatePickerDialog
        calendar.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        selectVisit()
        compose.onNodeWithTag("booking-screen").captureRoboImage("build/qa/staff-booking-visit.png")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText(patient.name).assertIsDisplayed()
        compose.onNodeWithText(patient.mrn).assertIsDisplayed()
        compose.onNodeWithText(doctor.name).assertIsDisplayed()
        compose.onNodeWithText("Request appointment").assertIsNotEnabled()
        compose.onNodeWithText("Reason for visit").performScrollTo().performTextInput("Routine review")
        compose.waitForIdle()
        compose.onNodeWithTag("booking-screen").captureRoboImage("build/qa/staff-booking-review.png")
        compose.onNodeWithText("Request appointment").assertIsDisplayed().performClick()
        compose.waitForIdle()
        coVerify(exactly = 1) { api.book(match { it["patient_id"] == patient.id && it["doctor_id"] == doctor.id && it["scheduled_at"] == time.scheduled_at && it["reason"] == "Routine review" }) }
        compose.onNodeWithTag("booking-screen").assertDoesNotExist()
    }
    @Test fun changingDoctorClearsThePreviouslySelectedTime() {
        launch(); openStaffBooking(); selectPatient(); selectVisit()
        compose.onNodeWithText("Continue").assertIsEnabled()
        compose.onNodeWithText(doctor.name).performScrollTo().performClick()
        compose.onNodeWithText(otherDoctor.name).performClick()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Available time").assertDoesNotExist()
        coVerify(exactly = 0) { api.book(any()) }
    }
    @Test fun changingPatientDoesNotReuseAnotherPatientsReason() {
        val other = patient.copy(id = "patient-b", name = "Meena Devi", mrn = "MRN-DEMO-002")
        coEvery { api.patients("Meena", any()) } returns listOf(other)
        launch(); openStaffBooking(); selectPatient(); selectVisit()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Reason for visit").performScrollTo().performTextInput("Private first-patient reason")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Patient name or MRN").performTextClearance()
        compose.onNodeWithText("Patient name or MRN").performTextInput("Meena")
        compose.onNodeWithText("Find patient").performClick()
        compose.onNodeWithText(other.name).performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText(other.name).assertIsDisplayed()
        compose.onNodeWithText(other.mrn).assertIsDisplayed()
        compose.onNodeWithText("Private first-patient reason").assertDoesNotExist()
        compose.onNodeWithText("Request appointment").assertIsNotEnabled()
        coVerify(exactly = 0) { api.book(any()) }
    }
    @Test fun staffMenuHasLeftAlignedGroupsAndCanReachProfileWithLargerText() {
        launch(scale = 1.3f)
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Reception").assertIsDisplayed()
        compose.onNodeWithText("Administration").assertIsDisplayed()
        compose.onNodeWithTag("staff-menu").captureRoboImage("build/qa/staff-menu-large-text.png")
        compose.onNodeWithText("Profile").performScrollTo().performClick()
        compose.onNodeWithText("My profile").assertIsDisplayed()
    }
    @Test fun patientBookingStartsAtVisitAndDoesNotSendAStaffPatientOverride() {
        launch(staff = false, scale = 1.3f)
        compose.onNodeWithText("Book an appointment").performScrollTo().performClick()
        compose.onNodeWithText("Book an appointment").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("booking-screen").captureRoboImage("build/qa/patient-booking-large-text.png")
        compose.onNodeWithText("Step 1 of 2").assertIsDisplayed()
        compose.onNodeWithText("Patient name or MRN").assertDoesNotExist()
        selectVisit()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Reason for visit").performScrollTo().performTextInput("Follow-up")
        compose.onNodeWithText("Request appointment").assertIsDisplayed().performClick()
        compose.waitForIdle()
        coVerify(exactly = 1) { api.book(match { "patient_id" !in it && it["doctor_id"] == doctor.id && it["scheduled_at"] == time.scheduled_at }) }
    }
}
