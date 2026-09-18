package com.example.staff.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.data.api.ConsultationData
import com.example.data.api.ConsultationWrite
import com.example.data.api.DrugApi
import com.example.data.repository.DoctorRepository
import com.example.domain.model.Medication
import com.example.domain.model.PrescriptionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrescriptionUiState(
    val isSearching: Boolean = false,
    val searchResults: List<Medication> = emptyList(),
    val searchError: String? = null,
    val currentItems: List<PrescriptionItem> = emptyList(),
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitError: String? = null
)

class PrescriptionViewModel(
    private val repository: DoctorRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PrescriptionUiState())
    val uiState: StateFlow<PrescriptionUiState> = _uiState.asStateFlow()

    private var cachedDrugs: List<DrugApi> = emptyList()
    private var currentServerVersion = 0

    fun searchMedications(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchError = null) }
            return
        }
        
        _uiState.update { it.copy(isSearching = true, searchError = null) }
        
        viewModelScope.launch {
            if (cachedDrugs.isEmpty()) {
                repository.getCatalogues().onSuccess { catalogues ->
                    cachedDrugs = catalogues.drugs
                    filterDrugs(query)
                }.onFailure { error ->
                    _uiState.update { it.copy(isSearching = false, searchError = error.message) }
                }
            } else {
                filterDrugs(query)
            }
        }
    }
    
    private fun filterDrugs(query: String) {
        val filtered = cachedDrugs.filter { it.name.contains(query, ignoreCase = true) }.take(20)
        val mapped = filtered.map { 
            Medication(
                id = it.id, 
                name = it.name, 
                defaultDosage = "1-0-1"
            ) 
        }
        _uiState.update { it.copy(isSearching = false, searchResults = mapped) }
    }

    fun addItem(item: PrescriptionItem) {
        _uiState.update { it.copy(currentItems = it.currentItems + item) }
    }

    fun removeItem(item: PrescriptionItem) {
        _uiState.update { it.copy(currentItems = it.currentItems - item) }
    }

    fun submitPrescription(patientId: String, doctorId: String, department: String) {
        val encounterId = patientId // Patient ID route arg is used as encounterId in v2 adaptation
        
        if (_uiState.value.currentItems.isEmpty()) {
            _uiState.update { it.copy(submitError = "Cannot submit empty prescription") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, submitError = null, submitSuccess = false) }

        val mappedItems = _uiState.value.currentItems.map {
            com.example.data.api.Medication(
                drug_id = it.medicationId,
                dose = it.dose,
                frequency = it.frequency,
                duration = it.durationDays.toString() + " days",
                instructions = it.instructions
            )
        }

        val write = ConsultationWrite(
            __v = currentServerVersion,
            data = ConsultationData(
                medications = mappedItems
            )
        )

        viewModelScope.launch {
            repository.completeConsultation(encounterId, write)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, submitSuccess = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSubmitting = false, submitError = error.message ?: "Failed to finalize prescription") }
                }
        }
    }
    
    fun resetState() {
        _uiState.update { PrescriptionUiState() }
    }

    companion object {
        fun provideFactory(repository: DoctorRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PrescriptionViewModel(repository) as T
                }
            }
    }
}
