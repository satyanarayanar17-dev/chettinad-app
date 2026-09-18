package com.example.patient.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.domain.model.PatientDossierResponse
import com.example.data.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatientRecordsUiState(
    val isLoading: Boolean = false,
    val dossier: PatientDossierResponse? = null,
    val error: String? = null
)

class PatientRecordsViewModel(
    private val repository: PatientRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PatientRecordsUiState())
    val uiState: StateFlow<PatientRecordsUiState> = _uiState.asStateFlow()

    fun loadRecords(patientId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getMyRecords(patientId)
                .onSuccess { dossier ->
                    _uiState.update { it.copy(isLoading = false, dossier = dossier) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load records") }
                }
        }
    }

    companion object {
        fun provideFactory(repository: PatientRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PatientRecordsViewModel(repository) as T
                }
            }
    }
}
