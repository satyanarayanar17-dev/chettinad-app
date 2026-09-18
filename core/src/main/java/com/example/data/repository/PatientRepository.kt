package com.example.data.repository

import com.example.data.api.ChettinadApiService
import com.example.domain.model.Patient
import com.example.domain.model.PatientDossierResponse

class PatientRepository(
    private val apiService: ChettinadApiService
) {
    suspend fun getMyRecords(patientId: String): Result<PatientDossierResponse> {
        return try {
            val record = apiService.getPatientRecord(patientId)
            val p = record.patient
            val mappedPatient = Patient(
                id = p.id,
                mrn = p.mrn,
                name = p.name,
                age = try {
                    val parts = p.dob.split("-")
                    val year = parts[0].toInt()
                    2024 - year
                } catch (e: Exception) { 30 },
                gender = p.gender,
                allergies = emptyList()
            )
            
            val mappedDossier = PatientDossierResponse(
                patient = mappedPatient,
                recentTriage = null,
                pastNotes = emptyList(),
                pastPrescriptions = emptyList()
            )
            
            Result.success(mappedDossier)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
