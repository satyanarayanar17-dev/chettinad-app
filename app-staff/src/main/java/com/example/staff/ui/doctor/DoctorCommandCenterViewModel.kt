package com.example.staff.ui.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.domain.model.Patient
import com.example.data.repository.DoctorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DoctorQueueUiState(
    val isLoading: Boolean = false,
    val queue: List<Patient> = emptyList(),
    val error: String? = null
)

class DoctorCommandCenterViewModel(
    private val repository: DoctorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorQueueUiState())
    val uiState: StateFlow<DoctorQueueUiState> = _uiState.asStateFlow()

    fun loadQueue(department: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getQueue(department)
                .onSuccess { queue ->
                    _uiState.update { it.copy(isLoading = false, queue = queue) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load queue") }
                }
        }
    }

    companion object {
        fun provideFactory(repository: DoctorRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DoctorCommandCenterViewModel(repository) as T
                }
            }
    }
}
