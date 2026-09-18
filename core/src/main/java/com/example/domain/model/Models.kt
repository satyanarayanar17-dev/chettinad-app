package com.example.domain.model

enum class Role {
    DOCTOR, NURSE, PATIENT, ADMIN
}

data class User(
    val id: String,
    val name: String,
    val role: Role,
    val department: String? = null,
    val isActive: Boolean = true
)

data class Patient(
    val id: String,
    val mrn: String,
    val name: String,
    val age: Int,
    val gender: String,
    val allergies: List<String> = emptyList()
)

data class Triage(
    val id: String,
    val patientId: String,
    val nurseId: String,
    val recordedAt: Long,
    val chiefComplaint: String,
    val symptoms: String,
    val temperatureCelsius: Float?,
    val pulseBpm: Int?,
    val bloodPressureSystolic: Int?,
    val bloodPressureDiastolic: Int?,
    val spO2: Int?,
    val respiratoryRate: Int?,
    val weightKg: Float?,
    val heightCm: Float?,
    val painScore: Int?,
    val priority: TriagePriority,
    val notes: String
)

enum class TriagePriority {
    ROUTINE, URGENT, EMERGENCY
}

data class ClinicalNote(
    val id: String,
    val patientId: String,
    val doctorId: String,
    val createdAt: Long,
    val chiefComplaint: String,
    val historyOfPresentIllness: String,
    val examination: String,
    val assessment: String,
    val diagnosis: String,
    val plan: String,
    val followUpInstructions: String,
    val isDraft: Boolean
)

data class Medication(
    val id: String,
    val name: String,
    val defaultDosage: String
)

data class PrescriptionItem(
    val medicationId: String,
    val medicationName: String,
    val dose: String,
    val frequency: String,
    val durationDays: Int,
    val route: String,
    val instructions: String
)

data class Prescription(
    val id: String,
    val patientId: String,
    val doctorId: String,
    val department: String,
    val issuedAt: Long,
    val items: List<PrescriptionItem>
)

data class PatientDossierResponse(
    val patient: Patient,
    val recentTriage: Triage?,
    val pastNotes: List<ClinicalNote>,
    val pastPrescriptions: List<Prescription>
)

data class AuditEvent(
    val id: String,
    val userId: String,
    val action: String,
    val resourceId: String,
    val timestamp: Long
)
