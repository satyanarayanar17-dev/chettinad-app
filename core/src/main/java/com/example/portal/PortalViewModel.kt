package com.example.portal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.*
import com.example.domain.model.User
import com.example.domain.model.Role
import com.example.util.TokenManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException

/** A patient ID never doubles as an encounter or account ID. Mutations are explicit and single flight. */
data class PortalState(
    val loading: Boolean = false, val patientLoading: Boolean = false, val busy: Boolean = false,
    val errors: Map<String, String> = emptyMap(), val message: String? = null,
    val appointments: List<Appointment> = emptyList(), val queue: List<QueuePatient> = emptyList(),
    val directory: Directory = Directory(), val slots: List<Slot> = emptyList(),
    val slotsDoctorId: String? = null, val slotsDate: String? = null,
    val record: ClinicalBundle? = null, val patientId: String? = null, val encounter: QueuePatient? = null,
    val care: CarePlanBundle = CarePlanBundle(), val today: List<CareOccurrence> = emptyList(),
    val timeline: List<AdherenceEntry> = emptyList(), val selfRecords: List<SelfRecord> = emptyList(),
    val documents: List<PatientDocument> = emptyList(), val notices: List<Notice> = emptyList(),
    val staff: List<StaffMember> = emptyList(), val dashboard: Map<String, Any?> = emptyMap(),
    val audit: List<AuditRow> = emptyList(), val sessions: List<DeviceSession> = emptyList(),
    val catalogues: PortalCatalogues = PortalCatalogues(), val labs: List<Lab> = emptyList(),
    val patients: List<PatientProfile> = emptyList(), val noteVersion: Int = 0,
    val draft: ConsultationData = ConsultationData(), val draftAvailable: Boolean = false,
    val draftDirty: Boolean = false, val draftConflict: Boolean = false,
    val reviewedPatientVersion: Int? = null
) {
    fun availableSlots(doctorId: String, date: String): List<Slot> =
        if (slotsDoctorId == doctorId && slotsDate == date) slots else emptyList()
}

class PortalViewModel(val api: PortalApi, val user: User, private val tokens: TokenManager) : ViewModel() {
    private val _state = MutableStateFlow(PortalState())
    val state = _state.asStateFlow()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val draftAdapter = moshi.adapter(ConsultationData::class.java)
    private var patientLoad: Job? = null
    private var slotsLoad: Job? = null
    private var patientSearch: Job? = null
    private var refreshJob: Job? = null
    private var patientRevision = 0
    init { refresh() }

    private suspend fun <T> read(section: String, request: suspend () -> T, apply: (PortalState, T) -> PortalState) {
        try {
            val data = request()
            currentCoroutineContext().ensureActive()
            _state.update { apply(it, data).copy(errors = it.errors - section) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.update { it.copy(errors = it.errors + (section to errorMessage(e))) } }
    }

    fun refresh() = refresh(silent = false)
    fun sync() = refresh(silent = true)
    fun stopSync() { refreshJob?.cancel(); patientLoad?.cancel() }
    private fun refresh(silent: Boolean) {
        if (refreshJob?.isActive == true || patientLoad?.isActive == true || _state.value.busy || _state.value.patientLoading) return
        val revision = patientRevision
        refreshJob = viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = true) }
            try { supervisorScope {
                launch { read("appointments", { allAppointments() }) { s, v -> s.copy(appointments = v) } }
                launch { read("queue", { api.queue() }) { s, v -> s.copy(queue = v,
                    encounter = s.encounter?.let { old -> v.find { it.encounter_id == old.encounter_id } }) } }
                launch { read("directory", { api.directory() }) { s, v -> s.copy(directory = v) } }
                launch { read("notifications", { api.notifications() }) { s, v -> s.copy(notices = v) } }
                launch { read("sessions", { api.sessions() }) { s, v -> s.copy(sessions = v) } }
                if (user.role == Role.ADMIN) {
                    launch { read("staff", { api.staff() }) { s, v -> s.copy(staff = v) } }
                    launch { read("overview", { api.dashboard() }) { s, v -> s.copy(dashboard = v) } }
                    launch { read("audit", { api.audit() }) { s, v -> s.copy(audit = v) } }
                } else if (user.role != Role.PATIENT) {
                    launch { read("labs", { allLabs() }) { s, v -> s.copy(labs = v) } }
                }
            }
                if (revision == patientRevision) {
                    val patient = if (user.role == Role.PATIENT) user.id else _state.value.patientId
                    if (patient != null) {
                        if (_state.value.patientId == null) loadPatient(patient)
                        else refreshPatient(patient)
                    }
                }
            } finally { _state.update { it.copy(loading = false) } }
        }
    }

    private suspend fun allAppointments(): List<Appointment> {
        val rows = mutableListOf<Appointment>(); var offset = 0
        do { val page = api.appointments(500, offset); rows.addAll(page); offset += page.size } while (page.size == 500)
        return rows.distinctBy { it.id }
    }
    private suspend fun allLabs(): List<Lab> {
        val rows = mutableListOf<Lab>(); var offset = 0
        do { val page = api.labs(500, offset); rows.addAll(page); offset += page.size } while (page.size == 500)
        return rows.distinctBy { it.id }
    }

    fun loadPatient(patientId: String, encounter: QueuePatient? = null) {
        if (_state.value.busy) return
        patientRevision++
        patientLoad?.cancel()
        _state.update { it.copy(patientLoading = true, patientId = patientId, encounter = encounter, record = null,
            care = CarePlanBundle(), today = emptyList(), timeline = emptyList(), documents = emptyList(),
            selfRecords = emptyList(), noteVersion = 0, draft = ConsultationData(), draftAvailable = false,
            draftDirty = false, draftConflict = false, reviewedPatientVersion = null,
            errors = it.errors.filterKeys { key -> key !in setOf("record", "care plan", "today", "timeline", "measurements", "documents") }) }
        refreshPatient(patientId)
    }

    private fun mergeRecord(s: PortalState, v: ClinicalBundle): PortalState {
        val note = v.notes.filter { it.encounter_id == s.encounter?.encounter_id }.maxByOrNull { it.__v }
        if (s.draftDirty) return s.copy(record = v, draftConflict = s.draftConflict ||
            s.noteVersion != (note?.__v ?: 0) || s.reviewedPatientVersion != v.patient.__v)
        val draft = note?.draft_content?.let { content -> ConsultationData(
                        complaint = content.complaint, history = content.history, previous_history = content.previous_history,
                        examination = content.examination, assessment = content.assessment,
                        diagnosis_ids = content.diagnoses.map { it.id }, treatment = content.treatment,
                        advice = content.advice, medications = content.medications, follow_up = content.follow_up) } ?: ConsultationData()
        return s.copy(record = v, noteVersion = note?.__v ?: 0, draft = draft,
            reviewedPatientVersion = v.patient.__v, draftConflict = false,
            draftAvailable = s.encounter?.let { tokens.getDraft(user.id, it.encounter_id) != null } ?: false)
    }

    private fun refreshPatient(patientId: String) {
        patientLoad?.cancel()
        patientLoad = viewModelScope.launch {
            try { supervisorScope {
                launch { read("record", { api.record(patientId) }, ::mergeRecord) }
                launch { read("care plan", { api.carePlan(patientId) }) { s,v -> s.copy(care = v) } }
                launch { read("today", { api.today(patientId) }) { s,v -> s.copy(today = v) } }
                launch { read("timeline", { api.timeline(patientId) }) { s,v -> s.copy(timeline = v) } }
                launch { read("measurements", { api.selfRecords(patientId) }) { s,v -> s.copy(selfRecords = v) } }
                launch { read("documents", { api.documents(patientId) }) { s,v -> s.copy(documents = v) } }
                if (user.role == Role.DOCTOR) launch { read("catalogues", { api.catalogues() }) { s,v -> s.copy(catalogues = v) } }
            } } finally { _state.update { it.copy(patientLoading = false) } }
        }
    }

    fun reloadHospitalDraft() {
        if (_state.value.busy) return
        val state = _state.value
        state.encounter?.let { tokens.removeDraft(user.id, it.encounter_id) }
        state.patientId?.let { loadPatient(it, state.encounter) }
    }

    fun slots(doctor: String, date: String) {
        slotsLoad?.cancel(); _state.update { it.copy(slots = emptyList(), slotsDoctorId = doctor, slotsDate = date) }
        slotsLoad = viewModelScope.launch { read("slots", { api.slots(doctor, date) }) { s,v -> s.copy(slots = v) } }
    }
    fun reloadStaff(staff: List<StaffMember>) { _state.update { it.copy(staff = staff) } }
    fun reloadDirectory(directory: Directory) { _state.update { it.copy(directory = directory) } }
    fun reloadDocuments(documents: List<PatientDocument>) { _state.update { it.copy(documents = documents) } }
    fun reloadNotices(notices: List<Notice>) { _state.update { it.copy(notices = notices) } }
    fun reloadSessions(sessions: List<DeviceSession>) { _state.update { it.copy(sessions = sessions) } }
    fun clearMessage() { _state.update { it.copy(message = null, errors = it.errors - "action") } }
    fun action(success: String, operation: suspend () -> Unit) {
        if (_state.value.busy) return
        refreshJob?.cancel(); patientLoad?.cancel()
        _state.update { it.copy(busy = true, loading = false, patientLoading = false, message = null, errors = it.errors - "action") }
        viewModelScope.launch {
            try { operation(); _state.update { it.copy(message = success) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(errors = it.errors + ("action" to errorMessage(e))) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    suspend fun reloadAppointments() { val a = allAppointments(); _state.update { it.copy(appointments = a) } }
    suspend fun reloadQueue() { val q = api.queue(); _state.update { it.copy(queue = q, encounter = it.encounter?.let { old -> q.find { row -> row.encounter_id == old.encounter_id } }) } }
    suspend fun reloadPatient() {
        val id = _state.value.patientId ?: return
        val record = api.record(id)
        _state.update { mergeRecord(it, record) }
    }
    suspend fun reloadCare() {
        val id = _state.value.patientId ?: return
        val care = api.carePlan(id); val today = api.today(id); val timeline = api.timeline(id)
        _state.update { it.copy(care = care, today = today, timeline = timeline) }
    }
    fun book(doctor: String, time: String, reason: String, patient: String? = null) = action("Appointment request sent") {
        val body = mutableMapOf("doctor_id" to doctor, "scheduled_at" to time, "reason" to reason)
        patient?.let { body["patient_id"] = it }
        api.book(body); reloadAppointments()
    }
    fun appointment(appointment: Appointment, verb: String, time: String? = null) = action("Appointment updated") {
        val body = mutableMapOf<String, Any>("__v" to appointment.__v)
        time?.let { body["scheduled_at"] = it }
        if (verb == "check-in") body["identity_verified"] = true
        api.appointmentAction(appointment.id, verb, body); reloadAppointments(); reloadQueue()
    }
    fun adherence(task: CareOccurrence, status: String, value: Double?, note: String) = action("Care plan updated") {
        val body = mutableMapOf<String, Any>("__v" to task.__v, "date" to task.occurrence_date, "status" to status)
        if (value != null) { body["value"] = value; task.target_unit?.let { body["unit"] = it } }
        if (note.isNotBlank()) body["note"] = note
        try { api.recordAdherence(task.id, body); reloadCare() }
        catch (e: HttpException) { if (e.code() == 409) reloadCare(); throw e }
    }
    fun selfRecord(body: Map<String, Any>) = action("Record saved") {
        val id = requireNotNull(_state.value.patientId)
        api.addSelfRecord(id, body); val records = api.selfRecords(id)
        _state.update { it.copy(selfRecords = records) }
    }
    fun searchPatients(query: String) {
        patientSearch?.cancel()
        _state.update { it.copy(patients = emptyList()) }
        patientSearch = viewModelScope.launch { read("patients", { api.patients(query) }) { s,v -> s.copy(patients = v) } }
    }
    fun updateDraft(data: ConsultationData) {
        val encounter = _state.value.encounter ?: return
        _state.update { it.copy(draft = data, draftDirty = true) }
        tokens.saveDraft(user.id, encounter.encounter_id, draftAdapter.toJson(data))
    }
    fun recoverDraft() {
        val encounter = _state.value.encounter ?: return
        val saved = tokens.getDraft(user.id, encounter.encounter_id) ?: return
        runCatching { draftAdapter.fromJson(saved) }.getOrNull()?.let { data -> _state.update { it.copy(draft = data, draftAvailable = false, draftDirty = true) } }
    }
    fun saveNote(complete: Boolean) = action(if (complete) "Consultation signed and completed" else "Draft saved to hospital") {
        val state = _state.value; val encounter = requireNotNull(state.encounter)
        check(!state.draftConflict) { "Review the changed hospital record before saving or signing" }
        val patient = requireNotNull(state.record?.patient)
        require(patient.id == encounter.patient_id) { "Review the current patient record before signing" }
        val write = ConsultationWrite(state.noteVersion, state.draft.forWrite(), requireNotNull(state.reviewedPatientVersion))
        val result = if (complete) api.complete(encounter.encounter_id, write)
            else api.saveNote(encounter.encounter_id, write)
        _state.update { it.copy(noteVersion = result.__v, draftDirty = false, draftConflict = false, draftAvailable = false) }
        tokens.removeDraft(user.id, encounter.encounter_id)
        reloadPatient(); reloadQueue()
    }
    fun transition(verb: String, triage: Map<String, Any>? = null, patientVersion: Int? = null,
                   allergyReviewConfirmed: Boolean = false, allergyChangeReason: String? = null) = action("Visit updated") {
        val encounter = requireNotNull(_state.value.encounter)
        val body = mutableMapOf<String, Any>("__v" to encounter.__v)
        triage?.let { body["data"] = it }
        patientVersion?.let { body["patient__v"] = it }
        if (allergyReviewConfirmed) body["allergy_review_confirmed"] = true
        allergyChangeReason?.takeIf { it.isNotBlank() }?.let { body["allergy_change_reason"] = it }
        api.encounterAction(encounter.encounter_id, verb, body); reloadQueue()
        if (triage != null) reloadPatient()
    }
    fun orderLab(test: String) = action("Laboratory test ordered") {
        val encounter = requireNotNull(_state.value.encounter)
        api.encounterAction(encounter.encounter_id, "labs", mapOf("test_id" to test)); reloadPatient()
    }
    fun lab(lab: Lab, verb: String, data: Map<String, Any>? = null) = action("Lab updated") {
        val body = mutableMapOf<String, Any>("__v" to lab.__v); data?.let { body["data"] = it }
        api.labAction(lab.id, verb, body)
        val labs = allLabs(); _state.update { it.copy(labs = labs) }; if (_state.value.patientId != null) reloadPatient()
    }
    companion object {
        fun factory(api: PortalApi, user: User, tokens: TokenManager) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PortalViewModel(api, user, tokens) as T
        }
    }
}

fun errorMessage(error: Throwable): String {
    if (error is IllegalStateException && error.message == "Review the changed hospital record before saving or signing") return error.message!!
    if (error is IllegalArgumentException && error.message?.contains("10 MB") == true) return "The file is too large. Choose a document under 10 MB."
    if (error is HttpException) {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val code = Regex("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.get(1)
        if (code in setOf("ALLERGY_WARNING", "DUPLICATE_MEDICATION")) {
            val envelope = runCatching { Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(ClinicalErrorEnvelope::class.java).fromJson(body) }.getOrNull()
            envelope?.error?.message?.let { return it }
        }
        if (code == "INVALID_CREDENTIALS" && error.response()?.raw()?.request?.url?.encodedPath?.endsWith("auth/change-password") == true)
            return "The current password is incorrect. Check it and try again."
        return when (error.code()) {
            401 -> "Your session has expired. Please sign in again."
            403 -> "Your account does not have access to this action."
            409 -> "${code?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "This record changed"}. Refresh and review before trying again."
            422, 400 -> "${code?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Check the entered details"}. Please review the form."
            429 -> "Too many requests. Please wait and try again."
            else -> "The hospital service could not complete this request. Please try again."
        }
    }
    return "Unable to connect. Check your connection and try again."
}

internal data class ClinicalErrorEnvelope(val error: ClinicalErrorMessage)
internal data class ClinicalErrorMessage(val message: String)
