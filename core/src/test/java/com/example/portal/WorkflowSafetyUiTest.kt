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
}
