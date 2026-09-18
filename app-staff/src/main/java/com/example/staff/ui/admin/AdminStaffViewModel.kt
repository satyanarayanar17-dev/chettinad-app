package com.example.staff.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.data.api.StaffInput
import com.example.data.repository.AdminRepository
import com.example.domain.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AdminStaffUiState(
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val error: String? = null
)

class AdminStaffViewModel(
    private val repository: AdminRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminStaffUiState())
    val uiState: StateFlow<AdminStaffUiState> = _uiState.asStateFlow()

    fun createStaff(
        name: String,
        role: Role,
        department: String
    ) {
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Name cannot be empty") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null, submitSuccess = false) }

        val staffInput = StaffInput(
            id = name.lowercase().replace(" ", "_").take(60), // Generate simplistic ID based on name for MVP
            name = name,
            role = role.name,
            department = department.takeIf { it.isNotBlank() } ?: "general",
            password = "Password123!" // Initial temp password
        )

        viewModelScope.launch {
            repository.createStaff(staffInput)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, submitSuccess = true) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSubmitting = false, error = error.message ?: "Failed to create staff account") }
                }
        }
    }
    
    fun resetState() {
        _uiState.update { AdminStaffUiState() }
    }

    companion object {
        fun provideFactory(repository: AdminRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdminStaffViewModel(repository) as T
                }
            }
    }
}
