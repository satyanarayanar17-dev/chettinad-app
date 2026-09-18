package com.example.data.repository

import com.example.data.api.ChettinadApiService
import com.example.data.api.TriageWrite
import com.example.data.api.VersionRequest
import com.example.domain.model.Patient
import retrofit2.Response

class NurseRepository(
    private val apiService: ChettinadApiService
) {
    suspend fun getQueue(): Result<List<Patient>> {
        return try {
            val queue = apiService.getQueue()
            val mapped = queue.map { entry ->
                Patient(
                    id = entry.encounter_id, // Map encounter_id to id so UI can pass it for next steps
                    mrn = entry.mrn,
                    name = entry.patient_name,
                    age = try {
                        val parts = entry.dob.split("-")
                        val year = parts[0].toInt()
                        2024 - year
                    } catch (e: Exception) { 30 },
                    gender = entry.gender,
                    allergies = emptyList()
                )
            }
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startTriage(encounterId: String, version: Int): Result<Unit> {
        return try {
            apiService.startTriage(encounterId, VersionRequest(version))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitTriage(encounterId: String, triageWrite: TriageWrite): Result<Unit> {
        return try {
            apiService.submitTriage(encounterId, triageWrite)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
