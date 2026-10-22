package com.example.portal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.api.*
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.ui.theme.AppTheme
import io.mockk.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkflowSafetyUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun followUpOnlyShowsTheSelectedDoctorsCurrentSearch() {
        val vm = mockk<PortalViewModel>(relaxed = true)
        every { vm.user } returns User("doctor-1", "Synthetic doctor", Role.DOCTOR)
        val slot = Slot("2026-10-16T05:00:00Z", "2026-10-16T05:15:00Z")
        val state = mutableStateOf(PortalState(slots = listOf(slot), slotsDoctorId = "doctor-other", slotsDate = todayIndia()))
        compose.setContent { AppTheme { Column { FollowUpEditor(vm, state.value) } } }
        compose.onNodeWithText("Schedule follow-up with this doctor").performClick()
        compose.onNodeWithText(displayDate(slot.scheduled_at)).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(slotsDoctorId = "doctor-1") }
        compose.onNodeWithText(displayDate(slot.scheduled_at)).assertExists()
        compose.runOnIdle { state.value = state.value.copy(slotsDate = "2000-01-01") }
        compose.onNodeWithText(displayDate(slot.scheduled_at)).assertDoesNotExist()
    }
    @Test fun triageStartsWithTheExistingAllergyRecordAndRequiresReview() {
        val vm = mockk<PortalViewModel>(relaxed = true)
        val profile = PatientProfile("patient-1", "MRN-1", "Synthetic", "+919100000001", "1990-01-01", "Female", allergies = "Synthetic allergy", allergy_review_status = "RECORDED")
        val state = PortalState(record = ClinicalBundle(profile), encounter = QueuePatient("encounter-1", "patient-1", "Synthetic", "MRN-1", "TRIAGE", 4))
        compose.setContent { AppTheme { Column(Modifier.verticalScroll(rememberScrollState())) { TriagePage(vm, state) } } }
        compose.onNode(hasText("Allergies") and hasSetTextAction()).assertTextContains("Synthetic allergy")
        compose.onNodeWithText("I reviewed the allergy record with the patient").assertExists()
        coVerify(exactly = 0) { vm.transition(any(), any(), any(), any(), any()) }
    }
    @Test fun careTaskFormKeepsItsReviewedVersionWhenAnotherAppChangesThePlan() {
        val vm = mockk<PortalViewModel>(relaxed = true)
        val plan = CarePlan("plan-1", "ACTIVE", 7, todayIndia())
        val state = mutableStateOf(PortalState(patientId = "patient-1", care = CarePlanBundle(plan)))
        compose.setContent { AppTheme { Column(Modifier.verticalScroll(rememberScrollState())) { TaskEditor(vm, state.value) } } }
        compose.onNode(hasText("Title") and hasSetTextAction()).performTextInput("Synthetic task")
        compose.onNode(hasText("Instruction") and hasSetTextAction()).performTextInput("Synthetic instruction")
        compose.onNodeWithText("Add task").performScrollTo().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(care = CarePlanBundle(plan.copy(__v = 8))) }
        compose.onNodeWithText("Add task").assertIsNotEnabled()
        compose.onNodeWithText("The care plan changed in another app. Close this editor and review the updated plan before making changes.").assertExists()
    }

    @Test fun pastPatientRecordsStayReachableAndChangingTheSearchHidesTheOldIdentity() {
        val vm = mockk<PortalViewModel>(relaxed = true)
        every { vm.user } returns User("doctor-1", "Synthetic doctor", Role.DOCTOR)
        val appointment = Appointment(id = "past-visit", __v = 4, status = "COMPLETED", scheduled_at = "2026-10-15T04:30:00Z", patient_id = "past-patient", patient_name = "Synthetic past patient", mrn = "MRN-PAST", encounter_id = "past-encounter")
        var opened: String? = null
        compose.setContent { AppTheme { Column { PatientRecordsSearch(vm, PortalState(appointments = listOf(appointment))) { opened = it.id } } } }
        compose.onNodeWithText("Open records").assertDoesNotExist()
        compose.onNode(hasText("Patient name or MRN") and hasSetTextAction()).performTextInput("MRN-PAST")
        compose.onNodeWithText("Search records").performClick()
        verify(exactly = 0) { vm.searchPatients(any()) }
        compose.onNodeWithText("Open records").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("past-patient", opened) }
        compose.onNode(hasText("Patient name or MRN") and hasSetTextAction()).performTextReplacement("Different patient")
        compose.onNodeWithText("Open records").assertDoesNotExist()
    }

}
