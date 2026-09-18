package com.example.data.api

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface ChettinadApiService {
    // --- AUTHENTICATION ---
    @POST("auth/login/staff")
    suspend fun loginStaff(@Body credentials: Map<String, String>): LoginResponse

    @POST("auth/opd/otp/request")
    suspend fun requestOtp(@Body payload: Map<String, String>): OtpRequestResponse

    @POST("auth/opd/otp/verify")
    suspend fun verifyOtp(@Body payload: Map<String, Any>): LoginResponse

    @POST("auth/refresh")
    fun refreshSync(): Call<RefreshResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("opd/session")
    suspend fun getSession(): SessionResponse

    // --- QUEUE ---
    @GET("opd/queue")
    suspend fun getQueue(): List<QueueEntry>

    // --- TRIAGE ---
    @POST("opd/encounters/{id}/start-triage")
    suspend fun startTriage(@Path("id") encounterId: String, @Body version: VersionRequest): Response<Unit>

    @POST("opd/encounters/{id}/triage")
    suspend fun submitTriage(@Path("id") encounterId: String, @Body triageWrite: TriageWrite): Response<Unit>

    // --- CONSULTATION ---
    @PUT("opd/encounters/{id}/consultation")
    suspend fun saveConsultationDraft(@Path("id") encounterId: String, @Body write: ConsultationWrite): NoteVersion

    @POST("opd/encounters/{id}/complete")
    suspend fun completeConsultation(@Path("id") encounterId: String, @Body write: ConsultationWrite): NoteVersion

    // --- PATIENTS ---
    @GET("opd/patients/{id}/record")
    suspend fun getPatientRecord(@Path("id") patientId: String): PatientRecord

    // --- CATALOGUES ---
    @GET("opd/catalogues")
    suspend fun getCatalogues(): Catalogues

    // --- STAFF (ADMIN) ---
    @GET("opd/staff")
    suspend fun getStaff(): List<Staff>

    @POST("opd/staff")
    suspend fun createStaff(@Body staff: StaffInput): Response<Unit>

    @PATCH("opd/staff/{id}")
    suspend fun updateStaffStatus(@Path("id") staffId: String, @Body active: Map<String, Boolean>): Response<Unit>
}

// Response Models based on v2 actual API
data class LoginResponse(
    val access_token: String,
    val role: String,
    val account_type: String,
    val userId: String,
    val name: String?,
    val must_change_password: Boolean?,
    val token_type: String
)

data class OtpRequestResponse(
    val sent: Boolean,
    val expires_in: Int,
    val development_code: String?
)

data class RefreshResponse(
    val access_token: String,
    val role: String,
    val account_type: String,
    val must_change_password: Boolean?,
    val token_type: String
)

data class SessionResponse(
    val id: String,
    val name: String,
    val role: String,
    val department: String?,
    val patient_id: String?,
    val must_change_password: Any?
)

data class VersionRequest(val __v: Int)

data class QueueEntry(
    val encounter_id: String,
    val appointment_id: String,
    val department_id: String,
    val token: String?,
    val status: String,
    val __v: Int,
    val patient_id: String,
    val patient_name: String,
    val mrn: String,
    val dob: String,
    val gender: String,
    val doctor_id: String?,
    val doctor_name: String?,
    val department_name: String?,
    val room: String?,
    val scheduled_at: String?,
    val chief_complaint: String?,
    val patients_ahead: Int?,
    val estimated_wait: Double?,
    val wait_minutes: Double?
)

data class TriageData(
    val temperature: Double,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int,
    val spo2: Double,
    val weight: Double,
    val height: Double,
    val glucose: Double?,
    val complaint: String,
    val allergies: String?,
    val pain: Int,
    val notes: String?,
    val priority: Int
)

data class TriageWrite(
    val __v: Int,
    val data: TriageData,
    val reason: String? = null
)

data class ConsultationData(
    val complaint: String? = null,
    val history: String? = null,
    val previous_history: String? = null,
    val examination: String? = null,
    val assessment: String? = null,
    val diagnosis_ids: List<String> = emptyList(),
    val treatment: String? = null,
    val advice: String? = null,
    val medications: List<Medication> = emptyList()
)

data class Medication(
    val drug_id: String,
    val dose: String,
    val frequency: String,
    val duration: String,
    val instructions: String? = null,
    val name: String? = null,
    val strength: String? = null,
    val form: String? = null,
    val route: String? = null
)

data class ConsultationWrite(
    val __v: Int,
    val data: ConsultationData
)

data class NoteVersion(
    val id: String,
    val __v: Int
)

data class PatientRecord(
    val patient: PatientApi,
    val encounters: List<EncounterApi>,
    val triage: List<Any>, // Simplification
    val notes: List<Any>, // Simplification
    val prescriptions: List<Any>, // Simplification
    val labs: List<Any>,
    val journey: List<Any>,
    val versions: List<Any>
)

data class PatientApi(
    val id: String,
    val mrn: String,
    val __v: Int,
    val name: String,
    val phone: String,
    val dob: String,
    val gender: String
)

data class EncounterApi(
    val id: String,
    val patient_id: String,
    val assigned_doctor_id: String?,
    val phase: String?,
    val __v: Int,
    val queue_status: String?
)

data class Catalogues(
    val drugs: List<DrugApi>,
    val diagnoses: List<DiagnosisApi>,
    val tests: List<Any>
)

data class DrugApi(
    val id: String,
    val name: String,
    val strength: String,
    val form: String,
    val route: String
)

data class DiagnosisApi(
    val id: String,
    val name: String
)

data class Staff(
    val id: String,
    val name: String,
    val role: String,
    val department: String?,
    val is_active: Int,
    val must_change_password: Int
)

data class StaffInput(
    val id: String,
    val name: String,
    val role: String,
    val department: String,
    val password: Any
)

data class ApiErrorResponse(
    val error: ApiErrorDetail,
    val meta: ApiErrorMeta
)

data class ApiErrorDetail(
    val code: String,
    val message: String,
    val details: Any?
)

data class ApiErrorMeta(
    val correlation_id: String?
)
