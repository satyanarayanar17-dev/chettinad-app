package com.example.staff.ui.nurse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.domain.model.Patient
import com.example.data.repository.NurseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NurseQueueUiState(
    val isLoading: Boolean = false,
    val queue: List<Patient> = emptyList(),
    val error: String? = null
)

class NurseTriageQueueViewModel(
    private val repository: NurseRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NurseQueueUiState())
    val uiState: StateFlow<NurseQueueUiState> = _uiState.asStateFlow()

    fun loadQueue() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.getQueue()
                .onSuccess { queue ->
                    _uiState.update { it.copy(isLoading = false, queue = queue) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load queue") }
                }
        }
    }

    companion object {
        fun provideFactory(repository: NurseRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NurseTriageQueueViewModel(repository) as T
                }
            }
    }
}
