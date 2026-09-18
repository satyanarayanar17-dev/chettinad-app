package com.example.staff.ui.auth.staff.ui.doctor

import com.example.data.api.Catalogues
import com.example.data.api.ChettinadApiService
import com.example.data.api.ConsultationWrite
import com.example.data.api.NoteVersion
import com.example.data.api.PatientRecord
import com.example.data.api.QueueEntry
import com.example.data.repository.DoctorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeDoctorRepositoryConflict : DoctorRepository(
    apiService = object : ChettinadApiService {
        // Implement only what's needed for the test, or throw
        override suspend fun loginStaff(credentials: Map<String, String>) = throw NotImplementedError()
        override suspend fun requestOtp(payload: Map<String, String>) = throw NotImplementedError()
        override suspend fun verifyOtp(payload: Map<String, Any>) = throw NotImplementedError()
        override fun refreshSync() = throw NotImplementedError()
        override suspend fun logout() = throw NotImplementedError()
        override suspend fun getSession() = throw NotImplementedError()
        override suspend fun getQueue() = throw NotImplementedError()
        override suspend fun getPatientRecord(patientId: String) = throw NotImplementedError()
        override suspend fun startTriage(encounterId: String, version: com.example.data.api.VersionRequest) = throw NotImplementedError()
        override suspend fun submitTriage(encounterId: String, triageWrite: com.example.data.api.TriageWrite) = throw NotImplementedError()
        
        override suspend fun saveConsultationDraft(encounterId: String, write: ConsultationWrite): NoteVersion {
            throw Exception("409 STALE_STATE")
        }
        
        override suspend fun completeConsultation(encounterId: String, write: ConsultationWrite): NoteVersion {
            throw Exception("409 STALE_STATE")
        }
        
        override suspend fun getCatalogues() = throw NotImplementedError()
        override suspend fun getStaff() = throw NotImplementedError()
        override suspend fun createStaff(staff: com.example.data.api.StaffInput) = throw NotImplementedError()
        override suspend fun updateStaffStatus(staffId: String, active: Map<String, Boolean>) = throw NotImplementedError()
    },
    draftDao = object : com.example.data.db.DraftNoteDao {
        override fun getDraftsForPatient(patientId: String) = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.db.DraftNoteEntity>())
        override suspend fun saveDraft(draft: com.example.data.db.DraftNoteEntity) {}
        override suspend fun deleteDraft(id: String) {}
    }
)

@OptIn(ExperimentalCoroutinesApi::class)
class ClinicalNoteConflictTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeDoctorRepositoryConflict
    private lateinit var viewModel: com.example.staff.ui.doctor.ClinicalNoteViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDoctorRepositoryConflict()
        viewModel = com.example.staff.ui.doctor.ClinicalNoteViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveNote_withConflict_setsConflictState() = runTest(testDispatcher) {
        viewModel.submitNote("p1", "d1", "cc", "hx", "ex", "as", "dx", "pl", "fu")
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertFalse(state.saveSuccess)
        assertTrue("State should indicate a version conflict", state.hasConflict)
        assertNull("Error string should be null when it's a known conflict state", state.error)
    }
}
