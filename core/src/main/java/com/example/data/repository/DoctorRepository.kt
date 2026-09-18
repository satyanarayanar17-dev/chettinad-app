package com.example.data.repository

import com.example.data.api.ChettinadApiService
import com.example.data.api.ConsultationData
import com.example.data.api.ConsultationWrite
import com.example.data.api.QueueEntry
import com.example.data.api.PatientRecord
import com.example.data.api.Catalogues
import com.example.data.api.NoteVersion
import com.example.data.db.DraftNoteDao
import com.example.domain.model.Patient
import com.example.domain.model.PatientDossierResponse
import com.example.domain.model.ClinicalNote
import com.example.domain.model.Prescription
import com.example.domain.model.Triage
import com.example.domain.model.TriagePriority
import java.util.UUID

open class DoctorRepository(
    private val apiService: ChettinadApiService,
    private val draftDao: DraftNoteDao
) {
    open suspend fun getQueue(department: String): Result<List<Patient>> {
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

    open suspend fun getPatientDossier(patientId: String): Result<PatientDossierResponse> {
        return try {
            val record = apiService.getPatientRecord(patientId)
            
            // Map Patient
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
            
            // Map Dossier
            val mappedDossier = PatientDossierResponse(
                patient = mappedPatient,
                recentTriage = null, // Simplified for now since structure is complex
                pastNotes = emptyList(), // Simplified
                pastPrescriptions = emptyList() // Simplified
            )
            
            Result.success(mappedDossier)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun saveConsultationDraft(encounterId: String, write: ConsultationWrite): Result<NoteVersion> {
        return try {
            val response = apiService.saveConsultationDraft(encounterId, write)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    open suspend fun completeConsultation(encounterId: String, write: ConsultationWrite): Result<NoteVersion> {
        return try {
            val response = apiService.completeConsultation(encounterId, write)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun saveDraft(draft: com.example.data.db.DraftNoteEntity) {
        draftDao.saveDraft(draft)
    }

    open fun observeDrafts(patientId: String): kotlinx.coroutines.flow.Flow<List<com.example.data.db.DraftNoteEntity>> {
        return draftDao.getDraftsForPatient(patientId)
    }
    
    open suspend fun getCatalogues(): Result<Catalogues> {
        return try {
            Result.success(apiService.getCatalogues())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
