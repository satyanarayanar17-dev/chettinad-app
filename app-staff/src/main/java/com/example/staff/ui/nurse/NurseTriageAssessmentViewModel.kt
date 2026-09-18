package com.example.staff.ui.nurse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.TriageData
import com.example.data.api.TriageWrite
import com.example.data.repository.NurseRepository
import com.example.domain.model.TriagePriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NurseTriageUiState(
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val error: String? = null
)

class NurseTriageAssessmentViewModel(
    private val nurseRepository: NurseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NurseTriageUiState())
    val uiState: StateFlow<NurseTriageUiState> = _uiState.asStateFlow()

    fun submitTriage(
        encounterId: String,
        nurseId: String,
        chiefComplaint: String,
        symptoms: String,
        temp: String,
        pulse: String,
        bpSystolic: String,
        bpDiastolic: String,
        spo2: String,
        respRate: String,
        weight: String,
        height: String,
        painScore: String,
        priority: TriagePriority,
        notes: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            
            try {
                val priorityInt = when (priority) {
                    TriagePriority.EMERGENCY -> 1
                    TriagePriority.URGENT -> 2
                    TriagePriority.ROUTINE -> 3
                }

                val triageData = TriageData(
                    temperature = temp.toDoubleOrNull() ?: 0.0,
                    systolic = bpSystolic.toIntOrNull() ?: 0,
                    diastolic = bpDiastolic.toIntOrNull() ?: 0,
                    pulse = pulse.toIntOrNull() ?: 0,
                    spo2 = spo2.toDoubleOrNull() ?: 0.0,
                    weight = weight.toDoubleOrNull() ?: 0.0,
                    height = height.toDoubleOrNull() ?: 0.0,
                    glucose = null,
                    complaint = chiefComplaint,
                    allergies = null,
                    pain = painScore.toIntOrNull() ?: 0,
                    notes = notes,
                    priority = priorityInt
                )
                
                val write = TriageWrite(__v = 0, data = triageData, reason = null)
                val result = nurseRepository.submitTriage(encounterId, write)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, submitSuccess = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        error = result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, error = e.message)
            }
        }
    }

    fun resetState() {
        _uiState.value = NurseTriageUiState()
    }

    companion object {
        fun provideFactory(nurseRepository: NurseRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NurseTriageAssessmentViewModel(nurseRepository) as T
                }
            }
    }
}
