package com.example.data.repository

import com.example.data.api.ChettinadApiService
import com.example.data.api.StaffInput
import com.example.domain.model.Role
import com.example.domain.model.User

class AdminRepository(
    private val apiService: ChettinadApiService
) {
    suspend fun getStaff(): Result<List<User>> {
        return try {
            val staff = apiService.getStaff()
            val mapped = staff.map {
                User(
                    id = it.id,
                    name = it.name,
                    role = try { Role.valueOf(it.role) } catch(e: Exception) { Role.NURSE },
                    department = it.department,
                    isActive = it.is_active == 1
                )
            }
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createStaff(staffInput: StaffInput): Result<Unit> {
        return try {
            apiService.createStaff(staffInput)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateStaffStatus(staffId: String, isActive: Boolean): Result<Unit> {
        return try {
            apiService.updateStaffStatus(staffId, mapOf("active" to isActive))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
