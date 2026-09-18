package com.example.staff.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.domain.model.PatientDossierResponse
import com.example.data.repository.DoctorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientDossierUiState(
    val isLoading: Boolean = false,
    val dossier: PatientDossierResponse? = null,
    val error: String? = null
)

class PatientDossierViewModel(
    private val repository: DoctorRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PatientDossierUiState())
    val uiState: StateFlow<PatientDossierUiState> = _uiState.asStateFlow()

    fun loadDossier(patientId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getPatientDossier(patientId)
                .onSuccess { record ->
                    _uiState.update { it.copy(isLoading = false, dossier = record) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load record") }
                }
        }
    }

    companion object {
        fun provideFactory(repository: DoctorRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PatientDossierViewModel(repository) as T
                }
            }
    }
}
