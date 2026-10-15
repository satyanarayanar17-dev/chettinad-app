package com.example.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

/** Native contract shared by Patient and Staff. All authorization stays on the backend. */
interface PortalApi {
    @GET("opd/profile") suspend fun profile(): PatientProfile
    @GET("opd/directory") suspend fun directory(): Directory
    @GET("opd/slots") suspend fun slots(@Query("doctor_id") doctor: String, @Query("date") date: String): List<Slot>
    @GET("opd/appointments") suspend fun appointments(@Query("limit") limit: Int = 500, @Query("offset") offset: Int = 0): List<Appointment>
    @POST("opd/appointments") suspend fun book(@Body data: Map<String, String>): Appointment
    @POST("opd/appointments/{id}/{action}") suspend fun appointmentAction(@Path("id") id: String, @Path("action") action: String, @Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, Any?>
    @GET("opd/queue") suspend fun queue(): List<QueuePatient>
    @GET("opd/patients/{id}/record") suspend fun record(@Path("id") id: String): ClinicalBundle
    @GET("opd/patients") suspend fun patients(@Query("search") query: String, @Query("limit") limit: Int = 100): List<PatientProfile>
    @POST("opd/patients") suspend fun createPatient(@Body profile: Map<String, String>): PatientProfile
    @PATCH("opd/patients/{id}") suspend fun updateProfile(@Path("id") id: String, @Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, Any?>
    @GET("opd/notifications") suspend fun notifications(@Query("limit") limit: Int = 100, @Query("offset") offset: Int = 0): List<Notice>
    @POST("opd/notifications/read") suspend fun readNotifications(): Map<String, Any?>
    @GET("opd/care-plans/{id}") suspend fun carePlan(@Path("id") patient: String): CarePlanBundle
    @GET("opd/adherence/{id}/today") suspend fun today(@Path("id") patient: String): List<CareOccurrence>
    @GET("opd/adherence/{id}/timeline") suspend fun timeline(@Path("id") patient: String): List<AdherenceEntry>
    @POST("opd/adherence/{id}/record") suspend fun recordAdherence(@Path("id") resource: String, @Body payload: Map<String, @JvmSuppressWildcards Any>): CareOccurrence
    @POST("opd/care-plans/{id}") suspend fun createPlan(@Path("id") patient: String, @Body data: Map<String, String>): Map<String, Any?>
    @POST("opd/care-plans/{id}/tasks") suspend fun createTask(@Path("id") patient: String, @Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, Any?>
    @PATCH("opd/care-plans/tasks/{id}") suspend fun changeTask(@Path("id") task: String, @Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, Any?>
    @POST("opd/care-plans/{id}/review") suspend fun reviewPlan(@Path("id") patient: String, @Body data: Map<String, String>): Map<String, Any?>
    @GET("opd/patient-self-records/{id}") suspend fun selfRecords(@Path("id") patient: String): List<SelfRecord>
    @POST("opd/patient-self-records/{id}") suspend fun addSelfRecord(@Path("id") patient: String, @Body data: Map<String, @JvmSuppressWildcards Any>): SelfRecord
    @GET("opd/patient-documents/{id}") suspend fun documents(@Path("id") patient: String): List<PatientDocument>
    @Multipart @POST("opd/patient-documents/{id}") suspend fun uploadDocument(@Path("id") patient: String, @Part file: MultipartBody.Part, @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>): PatientDocument
    @GET("opd/patient-documents/{patient}/{document}/content") suspend fun documentContent(@Path("patient") patient: String, @Path("document") document: String): ResponseBody
    @PATCH("opd/patient-documents/{patient}/{document}/appointment") suspend fun linkDocument(@Path("patient") patient: String, @Path("document") document: String, @Body data: Map<String, String?>): PatientDocument
    @POST("opd/encounters/{id}/{action}") suspend fun encounterAction(@Path("id") encounter: String, @Path("action") action: String, @Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, Any?>
    @PUT("opd/encounters/{id}/consultation") suspend fun saveNote(@Path("id") encounter: String, @Body data: ConsultationWrite): NoteVersion
    @POST("opd/encounters/{id}/complete") suspend fun complete(@Path("id") encounter: String, @Body data: ConsultationWrite): NoteVersion
    @GET("opd/catalogues") suspend fun catalogues(): PortalCatalogues
    @GET("opd/labs") suspend fun labs(@Query("limit") limit: Int = 500, @Query("offset") offset: Int = 0): List<Lab>
    @POST("opd/labs/{id}/{action}") suspend fun labAction(@Path("id") id: String, @Path("action") action: String, @Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, Any?>
    @GET("opd/staff") suspend fun staff(): List<StaffMember>
    @POST("opd/staff") suspend fun addStaff(@Body data: Map<String, String>): Map<String, Any?>
    @PATCH("opd/staff/{id}") suspend fun staffStatus(@Path("id") id: String, @Body data: Map<String, Boolean>): Map<String, Any?>
    @GET("opd/dashboard") suspend fun dashboard(): Map<String, Any?>
    @GET("opd/audit") suspend fun audit(@Query("limit") limit: Int = 200, @Query("offset") offset: Int = 0): List<AuditRow>
    @POST("opd/schedules") suspend fun schedule(@Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, Any?>
    @GET("auth/opd/sessions") suspend fun sessions(): List<DeviceSession>
    @DELETE("auth/opd/sessions/{id}") suspend fun revokeSession(@Path("id") id: String): Map<String, Any?>
    @POST("auth/change-password") suspend fun changePassword(@Body data: Map<String, String>): Map<String, Any?>
}

data class PatientProfile(
    val id: String, val mrn: String, val name: String, val phone: String, val dob: String,
    val gender: String, val __v: Int = 1, val email: String? = null,
    val address: String? = null, val city: String? = null, val state: String? = null,
    val pin_code: String? = null, val emergency_contact: String? = null,
    val preferred_language: String? = null, val allergies: String? = null,
    val allergy_review_status: String? = null
)
data class Department(val id: String, val name: String, val prefix: String = "")
data class Doctor(val id: String, val name: String, val department: String? = null, val schedules: List<DoctorSchedule> = emptyList())
data class DoctorSchedule(val id: String, val doctor_id: String, val department_id: String, val weekday: Int, val start_time: String, val end_time: String, val slot_minutes: Int, val room: String, val __v: Int = 1, val break_start: String? = null, val break_end: String? = null)
data class Directory(val departments: List<Department> = emptyList(), val doctors: List<Doctor> = emptyList())
data class Slot(val scheduled_at: String, val ends_at: String, val room: String = "", val department_id: String = "")
data class Appointment(val id: String, val scheduled_at: String, val status: String, val __v: Int, val patient_id: String? = null, val patient_name: String? = null, val mrn: String? = null, val doctor_id: String? = null, val doctor_name: String? = null, val department_name: String? = null, val reason: String? = null, val encounter_id: String? = null)
data class QueuePatient(val encounter_id: String, val patient_id: String, val patient_name: String, val mrn: String, val status: String, val __v: Int, val token: String? = null, val doctor_name: String? = null, val doctor_id: String? = null, val patients_ahead: Int? = null, val estimated_wait: Double? = null, val wait_minutes: Double? = null)
data class EncounterRecord(val id: String, val patient_id: String, val phase: String? = null, val __v: Int = 1)
data class TriageRecord(val id: String, val encounter_id: String, val data: Map<String, Any?>? = null, val created_at: String = "")
data class ClinicalDocument(val id: String, val encounter_id: String, val status: String, val __v: Int = 0, val created_at: String = "", val author_name: String? = null, val draft_content: NoteContent? = null)
data class NoteContent(val complaint: String? = null, val history: String? = null, val previous_history: String? = null, val examination: String? = null, val assessment: String? = null, val treatment: String? = null, val advice: String? = null, val diagnoses: List<DiagnosisApi> = emptyList(), val medications: List<Medication> = emptyList(), val follow_up: Map<String, String>? = null)
data class PrescriptionPatientSnapshot(val id: String, val mrn: String, val name: String, val dob: String, val gender: String, val __v: Int)
data class PrescriptionContent(val medications: List<Medication> = emptyList(), val diagnoses: List<DiagnosisApi> = emptyList(), val advice: String? = null, val patient_snapshot: PrescriptionPatientSnapshot? = null)
data class PrescriptionRecord(val id: String, val encounter_id: String, val status: String, val created_at: String, val doctor_name: String? = null, val rx_content: PrescriptionContent? = null)
data class ClinicalBundle(val patient: PatientProfile, val encounters: List<EncounterRecord> = emptyList(), val triage: List<TriageRecord> = emptyList(), val notes: List<ClinicalDocument> = emptyList(), val prescriptions: List<PrescriptionRecord> = emptyList(), val labs: List<Lab> = emptyList())
data class Lab(val id: String, val encounter_id: String, val test_id: String, val status: String, val __v: Int, val name: String? = null, val patient_name: String? = null, val unit: String? = null, val reference_range: String? = null, val result: LabResult? = null)
data class LabResult(val value: String, val unit: String, val reference_range: String, val flag: String, val resulted_at: String? = null)
data class TestCatalogue(val id: String, val name: String, val unit: String? = null, val reference_range: String? = null)
data class PortalCatalogues(val drugs: List<DrugApi> = emptyList(), val diagnoses: List<DiagnosisApi> = emptyList(), val tests: List<TestCatalogue> = emptyList())
data class Notice(val id: String, val code: String, val created_at: String, val read_at: String? = null)
data class CarePlan(val id: String, val status: String, val __v: Int, val start_date: String, val end_date: String? = null)
data class CareTask(val id: String, val title: String, val instruction: String, val task_type: String, val status: String, val revision: Int, val scheduled_time: String, val start_date: String, val end_date: String? = null, val observation_type: String? = null, val timing_relation: String? = null, val target_unit: String? = null, val prescription_item_id: String? = null, val frequency_type: String = "DAILY", val weekdays_mask: Int? = null, val interval_days: Int? = null, val one_time_date: String? = null, val window_before_minutes: Int = 0, val window_after_minutes: Int = 120, val expected_duration_minutes: Int? = null, val reminder_enabled: Any? = true)
data class PrescriptionSource(val id: String, val drug_name: String, val dose: String, val frequency: String, val duration: String, val strength: String? = null, val prescription_version: Int = 1, val current_prescription_version: Int = 1)
data class CarePlanBundle(val plan: CarePlan? = null, val tasks: List<CareTask> = emptyList(), val available_prescription_items: List<PrescriptionSource> = emptyList())
data class CareOccurrence(val id: String, val task_id: String, val title: String, val task_type: String, val status: String, val instruction: String = "", val occurrence_date: String, val due_time: String, val scheduled_for: String, val __v: Int, val target_unit: String? = null, val observation_type: String? = null, val timing_relation: String? = null, val recorded_value: Double? = null, val patient_note: String? = null, val medication_source: Map<String, Any?>? = null)
data class ObservationComponent(val component_code: String, val numeric_value: Double, val unit: String)
data class Observation(val observation_type: String, val observed_at: String, val components: List<ObservationComponent> = emptyList())
data class AdherenceEntry(val id: String, val status: String, val occurrence_date: String, val title: String? = null, val task_type: String? = null, val recorded_at: String, val patient_note: String? = null, val observation: Observation? = null)
data class SelfRecord(val id: String, val record_type: String, val observed_at: String, val numeric_value: Double? = null, val secondary_numeric_value: Double? = null, val unit: String? = null, val timing_context: String? = null, val activity_name: String? = null, val duration_minutes: Int? = null, val patient_note: String? = null)
data class PatientDocument(val id: String, val title: String, val document_type: String, val original_filename: String, val mime_type: String, val clinical_date: String, val size_bytes: Long, val appointment_id: String? = null, val patient_note: String? = null)
data class StaffMember(val id: String, val name: String, val role: String, val department: String? = null, val is_active: Any? = null) { val active: Boolean get() = is_active == true || is_active == 1 || is_active == 1.0 }
data class AuditRow(val id: Any, val action: String? = null, val actor_id: String? = null, val user_id: String? = null, val created_at: String? = null, val timestamp: String? = null)
data class DeviceSession(val id: String, val device_name: String, val created_at: String, val expires_at: String, val current: Boolean = false)
