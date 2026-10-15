package com.example.portal

import com.example.data.api.*
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.util.TokenManager
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PortalStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<PortalApi>(relaxed = true)
    private val tokens = mockk<TokenManager>(relaxed = true)
    private lateinit var vm: PortalViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { api.appointments(any(), any()) } returns emptyList()
        coEvery { api.labs(any(), any()) } returns emptyList()
        coEvery { api.directory() } returns Directory()
        vm = PortalViewModel(api, User("doctor-1", "Synthetic doctor", Role.DOCTOR), tokens)
    }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun bundle(id: String) = ClinicalBundle(PatientProfile(id, "MRN-$id", "Synthetic $id", "+919100000001", "1990-01-01", "Female"))
    @Test fun `switching patients cancels an older response`() = runTest(dispatcher) {
        advanceUntilIdle()
        coEvery { api.record("a") } coAnswers { delay(1000); bundle("a") }
        coEvery { api.record("b") } returns bundle("b")
        vm.loadPatient("a"); runCurrent()
        vm.loadPatient("b"); advanceUntilIdle()
        assertEquals("b", vm.state.value.patientId)
        assertEquals("b", vm.state.value.record?.patient?.id)
    }
    @Test fun `double tap executes a write only once while in flight`() = runTest(dispatcher) {
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>(); var calls = 0
        vm.action("Saved") { calls++; gate.await() }
        vm.action("Saved again") { calls++; gate.await() }
        runCurrent()
        assertEquals(1, calls); assertTrue(vm.state.value.busy)
        gate.complete(Unit); advanceUntilIdle()
        assertFalse(vm.state.value.busy)
    }
    @Test fun `slots are only offered for the searched doctor and date`() = runTest(dispatcher) {
        advanceUntilIdle()
        val slot = Slot("2026-10-16T05:00:00Z", "2026-10-16T05:15:00Z")
        coEvery { api.slots("doctor-a", "2026-10-16") } returns listOf(slot)
        vm.slots("doctor-a", "2026-10-16"); advanceUntilIdle()
        val state = vm.state.value
        assertEquals(listOf(slot), state.availableSlots("doctor-a", "2026-10-16"))
        assertTrue(state.availableSlots("doctor-b", "2026-10-16").isEmpty())
        assertTrue(state.availableSlots("doctor-a", "2026-10-17").isEmpty())
    }
    @Test fun `a slower slot search cannot replace a newer doctor's availability`() = runTest(dispatcher) {
        advanceUntilIdle()
        val old = Slot("2026-10-16T05:00:00Z", "2026-10-16T05:15:00Z")
        val current = Slot("2026-10-17T06:00:00Z", "2026-10-17T06:15:00Z")
        coEvery { api.slots("doctor-a", "2026-10-16") } coAnswers { delay(1000); listOf(old) }
        coEvery { api.slots("doctor-b", "2026-10-17") } returns listOf(current)
        vm.slots("doctor-a", "2026-10-16"); runCurrent()
        vm.slots("doctor-b", "2026-10-17"); advanceUntilIdle()
        val state = vm.state.value
        assertEquals(listOf(current), state.availableSlots("doctor-b", "2026-10-17"))
        assertTrue(state.availableSlots("doctor-a", "2026-10-16").isEmpty())
    }
    @Test fun `signing sends the reviewed patient version as well as the note version`() = runTest(dispatcher) {
        advanceUntilIdle()
        val record = bundle("patient-a").let { it.copy(patient = it.patient.copy(__v = 7)) }
        coEvery { api.record("patient-a") } returns record
        coEvery { api.complete(any(), any()) } returns NoteVersion("note-a", 3)
        vm.loadPatient("patient-a", QueuePatient("encounter-a", "patient-a", "Synthetic", "MRN-a", "CONSULTATION", 4))
        advanceUntilIdle(); vm.saveNote(true); advanceUntilIdle()
        coVerify(exactly = 1) { api.complete("encounter-a", match { it.patient__v == 7 && it.__v == 0 && it.data.previous_history == "" && it.data.treatment == "" }) }
    }
    @Test fun `triage carries explicit allergy review and the reviewed patient version`() = runTest(dispatcher) {
        advanceUntilIdle()
        coEvery { api.record("patient-a") } returns bundle("patient-a")
        vm.loadPatient("patient-a", QueuePatient("encounter-a", "patient-a", "Synthetic", "MRN-a", "TRIAGE", 4))
        advanceUntilIdle()
        vm.transition("triage", mapOf("allergies" to "Synthetic allergy", "allergy_review_status" to "RECORDED"), 7, true, "Confirmed with patient")
        advanceUntilIdle()
        coVerify(exactly = 1) { api.encounterAction("encounter-a", "triage", match {
            it["__v"] == 4 && it["patient__v"] == 7 && it["allergy_review_confirmed"] == true && it["allergy_change_reason"] == "Confirmed with patient"
        }) }
    }
    @Test fun `an old patient search cannot replace the newer identity results`() = runTest(dispatcher) {
        advanceUntilIdle()
        coEvery { api.patients("old", any()) } coAnswers { delay(1000); listOf(bundle("old").patient) }
        coEvery { api.patients("new", any()) } returns listOf(bundle("new").patient)
        vm.searchPatients("old"); runCurrent()
        vm.searchPatients("new"); advanceUntilIdle()
        assertEquals("new", vm.state.value.patients.single().id)
    }
}
