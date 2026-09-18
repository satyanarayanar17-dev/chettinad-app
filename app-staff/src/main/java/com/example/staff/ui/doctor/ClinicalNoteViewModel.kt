package com.example.staff.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.data.api.ConsultationData
import com.example.data.api.ConsultationWrite
import com.example.data.db.DraftNoteEntity
import com.example.data.repository.DoctorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ClinicalNoteUiState(
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val draftSaved: Boolean = false,
    val hasConflict: Boolean = false,
    val error: String? = null,
    val existingDraft: DraftNoteEntity? = null
)

class ClinicalNoteViewModel(
    private val repository: DoctorRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClinicalNoteUiState())
    val uiState: StateFlow<ClinicalNoteUiState> = _uiState.asStateFlow()

    private var draftJob: Job? = null
    private var observeJob: Job? = null
    
    // In this MVP adaptation, patientId in the route is actually encounterId
    private var encounterId: String = ""
    private var currentServerVersion: Int = 0

    fun loadDrafts(patientId: String) { // patientId here is encounterId
        encounterId = patientId
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeDrafts(patientId).collect { drafts ->
                if (drafts.isNotEmpty()) {
                    val latestDraft = drafts.maxByOrNull { it.lastUpdated }
                    if (latestDraft != null) {
                        _uiState.update { it.copy(existingDraft = latestDraft) }
                    }
                }
            }
        }
    }

    fun autoSaveDraft(
        patientId: String,
        chiefComplaint: String,
        history: String,
        examination: String,
        assessment: String,
        diagnosis: String,
        plan: String
    ) {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(1000)
            val draft = DraftNoteEntity(
                id = encounterId,
                patientId = patientId,
                chiefComplaint = chiefComplaint,
                history = history,
                examination = examination,
                assessment = assessment,
                diagnosis = diagnosis,
                plan = plan,
                lastUpdated = System.currentTimeMillis()
            )
            repository.saveDraft(draft)
            
            val write = ConsultationWrite(
                __v = currentServerVersion,
                data = ConsultationData(
                    complaint = chiefComplaint,
                    history = history,
                    examination = examination,
                    assessment = assessment,
                    treatment = plan
                )
            )
            val result = repository.saveConsultationDraft(encounterId, write)
            result.onSuccess {
                currentServerVersion = it.__v
            }
            
            _uiState.update { it.copy(draftSaved = true, error = null) }
            delay(2000)
            _uiState.update { it.copy(draftSaved = false) }
        }
    }

    fun submitNote(
        patientId: String,
        doctorId: String,
        chiefComplaint: String,
        history: String,
        examination: String,
        assessment: String,
        diagnosis: String,
        plan: String,
        followUp: String
    ) {
        _uiState.update { it.copy(isSaving = true, error = null, saveSuccess = false) }
        
        val write = ConsultationWrite(
            __v = currentServerVersion,
            data = ConsultationData(
                complaint = chiefComplaint,
                history = history,
                examination = examination,
                assessment = assessment,
                treatment = plan
            )
        )

        viewModelScope.launch {
            repository.completeConsultation(encounterId, write)
                .onSuccess {
                    currentServerVersion = it.__v
                    _uiState.update { state -> state.copy(isSaving = false, saveSuccess = true) }
                }
                .onFailure { error ->
                    val isConflict = error.message?.contains("409") == true || error.message?.contains("STALE_STATE") == true
                    _uiState.update { 
                        it.copy(
                            isSaving = false, 
                            error = if (isConflict) null else (error.message ?: "Failed to save to hospital record"),
                            hasConflict = isConflict
                        ) 
                    }
                }
        }
    }

    fun resetState() {
        _uiState.update { ClinicalNoteUiState() }
    }

    companion object {
        fun provideFactory(repository: DoctorRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ClinicalNoteViewModel(repository) as T
                }
            }
    }
}
