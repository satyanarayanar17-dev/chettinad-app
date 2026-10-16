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
        every { tokens.getDraft(any(), any()) } returns null
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
        assertEquals("new", vm.state.value.patientSearchQuery); assertFalse(vm.state.value.patientSearchLoading)
    }

    private val encounter = QueuePatient("encounter-a", "patient-a", "Synthetic", "MRN-a", "CONSULTATION", 4)
    private fun record(noteVersion: Int = 2, history: String = "Hospital history", patientVersion: Int = 7) =
        bundle("patient-a").let { it.copy(patient = it.patient.copy(__v = patientVersion),
            notes = listOf(ClinicalDocument("note-a", encounter.encounter_id, "DRAFT", noteVersion,
                draft_content = NoteContent(history = history)))) }
    private suspend fun openConsultation() {
        coEvery { api.queue() } returns listOf(encounter)
        coEvery { api.record("patient-a") } returns record()
        vm.loadPatient("patient-a", encounter)
    }
    @Test fun `foreground sync and manual refresh preserve unsaved clinical edits`() = runTest(dispatcher) {
        advanceUntilIdle(); openConsultation(); advanceUntilIdle()
        vm.updateDraft(vm.state.value.draft.copy(history = "Unsaved local history"))
        vm.sync(); advanceUntilIdle(); vm.refresh(); advanceUntilIdle()
        assertEquals("Unsaved local history", vm.state.value.draft.history)
        assertEquals(2, vm.state.value.noteVersion)
        assertTrue(vm.state.value.draftDirty); assertFalse(vm.state.value.draftConflict)
    }
    @Test fun `a remote note change blocks signing and requires explicit draft review`() = runTest(dispatcher) {
        advanceUntilIdle(); openConsultation(); advanceUntilIdle()
        vm.updateDraft(vm.state.value.draft.copy(history = "Unsaved local history"))
        coEvery { api.record("patient-a") } returns record(3, "Updated from web")
        vm.sync(); advanceUntilIdle()
        assertEquals("Unsaved local history", vm.state.value.draft.history)
        assertEquals(2, vm.state.value.noteVersion); assertTrue(vm.state.value.draftConflict)
        vm.saveNote(true); advanceUntilIdle()
        coVerify(exactly = 0) { api.complete(any(), any()) }
        vm.reloadHospitalDraft(); advanceUntilIdle()
        assertEquals("Updated from web", vm.state.value.draft.history)
        assertEquals(3, vm.state.value.noteVersion); assertFalse(vm.state.value.draftDirty)
        assertFalse(vm.state.value.draftConflict)
    }
    @Test fun `patient changes from another client do not advance the reviewed signing version`() = runTest(dispatcher) {
        advanceUntilIdle(); openConsultation(); advanceUntilIdle()
        vm.updateDraft(vm.state.value.draft.copy(advice = "Local advice"))
        coEvery { api.record("patient-a") } returns record(patientVersion = 8)
        vm.sync(); advanceUntilIdle()
        assertEquals(8, vm.state.value.record?.patient?.__v)
        assertEquals(7, vm.state.value.reviewedPatientVersion)
        assertTrue(vm.state.value.draftConflict)
    }
    @Test fun `an untouched draft and appointments adopt changes from another app`() = runTest(dispatcher) {
        advanceUntilIdle(); openConsultation(); advanceUntilIdle()
        coEvery { api.record("patient-a") } returns record(3, "Updated from iOS")
        val appointment = Appointment("appointment-a", "2026-10-16T05:00:00Z", "CONFIRMED", 2)
        coEvery { api.appointments(any(), any()) } returns listOf(appointment)
        vm.sync(); advanceUntilIdle()
        assertEquals("Updated from iOS", vm.state.value.draft.history)
        assertEquals(3, vm.state.value.noteVersion)
        assertEquals(listOf(appointment), vm.state.value.appointments)
    }
    @Test fun `a foreground read cannot replace a newer patient selection`() = runTest(dispatcher) {
        advanceUntilIdle(); openConsultation(); advanceUntilIdle()
        coEvery { api.record("patient-a") } coAnswers { delay(1000); record(3, "Old patient") }
        vm.sync(); runCurrent()
        coEvery { api.record("patient-b") } returns bundle("patient-b")
        vm.loadPatient("patient-b"); advanceUntilIdle()
        assertEquals("patient-b", vm.state.value.record?.patient?.id)
        assertNull(vm.state.value.draft.history)
    }
    @Test fun `writes cancel pending sync and background sync waits while a write runs`() = runTest(dispatcher) {
        advanceUntilIdle(); openConsultation(); advanceUntilIdle()
        coEvery { api.record("patient-a") } coAnswers { delay(1000); record(4, "Stale read") }
        vm.sync(); runCurrent()
        val gate = CompletableDeferred<Unit>()
        vm.action("Saved") { gate.await() }; runCurrent()
        vm.sync(); advanceTimeBy(2000); runCurrent()
        assertEquals("Hospital history", vm.state.value.draft.history)
        gate.complete(Unit); advanceUntilIdle()
        assertFalse(vm.state.value.busy)
    }
    private fun signedRecord(version: Int = 2, history: String = "Signed history") = record(version, history).let { bundle ->
        bundle.copy(encounters = listOf(EncounterRecord(encounter.encounter_id, "patient-a", assigned_doctor_id = "doctor-1", is_discharged = 1)),
            notes = bundle.notes.map { it.copy(status = "FINALIZED", author_id = "doctor-1") })
    }
    @Test fun `amendments retain reviewed versions and use a separate encrypted recovery draft`() = runTest(dispatcher) {
        advanceUntilIdle()
        coEvery { api.record("patient-a") } returns signedRecord()
        vm.loadPatient("patient-a"); advanceUntilIdle()
        vm.beginAmendment(vm.state.value.record!!.notes.single())
        vm.updateDraft(vm.state.value.draft.copy(history = "Clarified signed history"))
        vm.updateAmendmentReason("Reviewed with patient")
        verify { tokens.saveDraft("doctor-1", "amend:encounter-a", any()) }
        coEvery { api.record("patient-a") } returns signedRecord(3, "Amended from web")
        vm.sync(); advanceUntilIdle()
        assertEquals("Clarified signed history", vm.state.value.draft.history)
        assertEquals(2, vm.state.value.noteVersion); assertTrue(vm.state.value.draftConflict)
        vm.saveAmendment(); advanceUntilIdle()
        coVerify(exactly = 0) { api.amend(any(), any()) }
        vm.reloadHospitalDraft(); advanceUntilIdle()
        assertEquals("Amended from web", vm.state.value.draft.history)
        assertEquals("encounter-a", vm.state.value.amendingEncounterId)
        assertEquals(3, vm.state.value.noteVersion); assertFalse(vm.state.value.draftConflict)
        vm.updateDraft(vm.state.value.draft.copy(history = "Reviewed updated history"))
        vm.saveAmendment(); advanceUntilIdle()
        coVerify(exactly = 1) { api.amend("encounter-a", match { it.__v == 3 && it.patient__v == 7 && it.reason == "Reviewed with patient" }) }
    }
    @Test fun `only the assigned doctor can amend a discharged finalized consultation`() = runTest(dispatcher) {
        advanceUntilIdle()
        val record = signedRecord(); val note = record.notes.single()
        assertTrue(canAmend(record, note, vm.user))
        assertFalse(canAmend(record, note, vm.user.copy(id = "other-doctor")))
        assertFalse(canAmend(record, note, vm.user.copy(role = Role.NURSE)))
        assertFalse(canAmend(record.copy(encounters = record.encounters.map { it.copy(is_discharged = 0) }), note, vm.user))
        assertFalse(canAmend(record, note.copy(status = "DRAFT"), vm.user))
    }

    @Test fun `recovery after a web edit keeps the original reviewed versions and blocks signing`() = runTest(dispatcher) {
        advanceUntilIdle(); openConsultation(); advanceUntilIdle()
        val saved = slot<String>()
        every { tokens.saveDraft(any(), any(), capture(saved)) } just Runs
        vm.updateDraft(vm.state.value.draft.copy(history = "Unsent local history"))
        every { tokens.getDraft("doctor-1", "encounter-a") } returns saved.captured
        coEvery { api.record("patient-a") } returns record(3, "Updated on web", 8)
        vm.loadPatient("patient-a", encounter); advanceUntilIdle(); vm.recoverDraft()
        assertEquals("Unsent local history", vm.state.value.draft.history)
        assertEquals(2, vm.state.value.noteVersion); assertEquals(7, vm.state.value.reviewedPatientVersion)
        assertTrue(vm.state.value.draftConflict)
        vm.saveNote(true); advanceUntilIdle()
        coVerify(exactly = 0) { api.complete(any(), any()) }
        vm.reloadHospitalDraft(); advanceUntilIdle()
        assertEquals("Updated on web", vm.state.value.draft.history); assertFalse(vm.state.value.draftConflict)
    }
    @Test fun `current recovery remains editable while legacy unversioned recovery requires review`() = runTest(dispatcher) {
        advanceUntilIdle(); openConsultation(); advanceUntilIdle()
        val saved = slot<String>()
        every { tokens.saveDraft(any(), any(), capture(saved)) } just Runs
        vm.updateDraft(vm.state.value.draft.copy(history = "Current local draft"))
        every { tokens.getDraft("doctor-1", "encounter-a") } returns saved.captured
        vm.loadPatient("patient-a", encounter); advanceUntilIdle(); vm.recoverDraft()
        assertEquals("Current local draft", vm.state.value.draft.history); assertFalse(vm.state.value.draftConflict)
        every { tokens.getDraft("doctor-1", "encounter-a") } returns "{\"history\":\"Legacy local draft\"}"
        vm.loadPatient("patient-a", encounter); advanceUntilIdle(); vm.recoverDraft()
        assertEquals("Legacy local draft", vm.state.value.draft.history); assertTrue(vm.state.value.draftConflict)
    }

}
