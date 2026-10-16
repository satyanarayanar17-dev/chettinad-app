package com.example.portal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.api.*
import com.example.domain.model.User
import com.example.domain.model.Role
import com.example.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StaffPortal(container: AppContainer, user: User, onLogout: () -> Unit) {
    val vm: PortalViewModel = viewModel(key = "staff:${user.id}", factory = PortalViewModel.factory(container.portalApi, user, container.tokenManager))
    val state by vm.state.collectAsState()
    ForegroundPortalSync(vm)
    var tab by remember { mutableStateOf(if (user.mustChangePassword) "Profile" else if (user.role == Role.ADMIN) "Overview" else "Live queue") }
    var menu by remember { mutableStateOf(false) }; var patientPage by remember { mutableStateOf("Visit") }
    var patientOpen by remember { mutableStateOf(false) }
    val destinations = if (user.mustChangePassword) listOf("Profile") else if (user.role == Role.ADMIN) listOf("Overview", "Appointments", "Patients", "Doctor schedules", "Staff", "Audit trail", "Notifications", "Profile")
        else listOf("Live queue", "Patient records", "Appointments", "Lab workflow", "Notifications", "Profile")
    BackHandler(patientOpen) { patientOpen = false }
    Scaffold(topBar = { TopAppBar(title = { Text("Chettinad Care Staff", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Menu, "Open navigation") } },
        actions = { TextButton(onClick = vm::refresh, enabled = !state.loading && !state.busy) { Text("Refresh") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (patientOpen) {
                TextButton(onClick = { patientOpen = false }, enabled = !state.busy) { Text(if (tab == "Live queue") "Back to live queue" else "Back to ${tab.lowercase()}") }
                PageHeading(state.record?.patient?.name ?: state.encounter?.patient_name ?: "Patient", "${state.record?.patient?.mrn ?: state.encounter?.mrn.orEmpty()} · Visit ${state.encounter?.token.orEmpty()}")
                ChoiceRow(listOf("Visit", "Records", "Care plan", "Trends"), patientPage, { if (!state.busy) patientPage = it })
                when (patientPage) {
                    "Visit" -> if (user.role == Role.NURSE) TriagePage(vm, state) else ConsultationPage(vm, state)
                    "Records" -> RecordsPage(vm, state, uploadAllowed = false, onAmend = { patientPage = "Visit" })
                    "Care plan" -> CarePlanPage(vm, state, patient = false)
                    "Trends" -> TrendsPage(vm, state, canRecord = false)
                }
            } else when (tab) {
                "Live queue" -> {
                    PageHeading("Live queue", "${user.department.orEmpty()} · ${if (user.role == Role.NURSE) "Triage and handoff" else "Consultations"}")
                    ErrorCards(state, setOf("queue"), vm::refresh)
                    val queue = state.queue.filter { if (user.role == Role.NURSE) it.status in listOf("WAITING", "TRIAGE", "WAITING_DOCTOR") else it.status in listOf("WAITING_DOCTOR", "DOCTOR_READY", "CONSULTATION", "WAITING") }
                    if (queue.isEmpty() && "queue" !in state.errors) EmptyCard("No patients waiting", "New arrivals will appear here after check-in.")
                    PagedRows(queue) { entry -> InfoCard(entry.patient_name, "${entry.mrn} · Token ${entry.token.orEmpty()}") {
                        StatusPill(entry.status); entry.wait_minutes?.let { Text("Waiting ${it.toInt()} minutes") }
                        Button(onClick = { vm.loadPatient(entry.patient_id, entry); patientOpen = true; patientPage = "Visit" }, enabled = !state.busy) { Text("Open visit") }
                    } }
                }
                "Patient records" -> PatientRecordsSearch(vm, state) { patient ->
                    vm.loadPatient(patient.id); patientOpen = true; patientPage = "Records"
                }
                "Appointments" -> AppointmentPage(vm, state, staff = true)
                "Lab workflow" -> { PageHeading("Lab workflow", "Collection, verified results and clinical review."); ErrorCards(state, setOf("labs"), vm::refresh); LabList(vm, state, state.labs, patient = false) }
                "Overview" -> AdminOverview(vm, state)
                "Staff" -> StaffAdministration(vm, state)
                "Patients" -> PatientRegistration(vm, state)
                "Doctor schedules" -> SchedulePage(vm, state)
                "Audit trail" -> { PageHeading("Audit trail"); ErrorCards(state, setOf("audit"), vm::refresh); PagedRows(state.audit) { InfoCard(label(it.action.orEmpty()), displayDate(it.timestamp ?: it.created_at)) { Text("Actor: ${it.actor_id ?: it.user_id ?: "System"}") } } }
                "Notifications" -> NotificationsPage(vm, state)
                "Profile" -> ProfilePage(vm, state, onLogout)
            }
        }
    }
    if (menu) ModalBottomSheet(onDismissRequest = { menu = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(user.name, style = MaterialTheme.typography.titleLarge)
            destinations.forEach { destination -> TextButton(onClick = { if (!state.busy) { tab = destination; patientOpen = false; menu = false } }, modifier = Modifier.fillMaxWidth()) { Text(destination) } }
        }
    }
}

@Composable internal fun PatientRecordsSearch(vm: PortalViewModel, state: PortalState, onOpen: (PatientProfile) -> Unit) {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf<String?>(null) }
    PageHeading("Patient records", "Find past visits and shared care records by name or MRN. Hospital access rules apply.")
    ErrorCards(state, setOf("patients"), vm::refresh)
    Field("Patient name or MRN", query, { query = it }, !state.busy)
    Button(onClick = { searched = query; vm.searchPatients(query.trim()) }, enabled = !state.busy && query.trim().length in 2..200) { Text("Search records") }
    if (searched == query && state.patientSearchQuery == query.trim()) {
        if (state.patientSearchLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (state.patients.isEmpty() && "patients" !in state.errors) EmptyCard("No matching patients", "Check the patient name or MRN and search again.")
        PagedRows(state.patients) { patient ->
            InfoCard(patient.name, patient.mrn) {
                Button(onClick = { onOpen(patient) }, enabled = !state.busy) { Text("Open records") }
            }
        }
    }
}

@Composable internal fun TriagePage(vm: PortalViewModel, state: PortalState) {
    val encounter = state.encounter
    ErrorCards(state, setOf("record", "queue"), vm::refresh)
    state.record?.patient?.allergies?.takeIf { it.isNotBlank() }?.let { InfoCard("Recorded allergies", it) }
    if (encounter == null) { EmptyCard("Visit no longer in the queue", "Refresh or return to the live queue."); return }
    StatusPill(encounter.status)
    if (encounter.status == "WAITING") Button(onClick = { vm.transition("start-triage") }, enabled = !state.busy) { Text("Start triage") }
    if (encounter.status == "WAITING_DOCTOR") { EmptyCard("Triage complete", "The patient is waiting for the assigned doctor."); return }
    if (encounter.status != "TRIAGE") return
    val patient = state.record?.patient ?: run { Text("Loading the patient record before triage…"); return }
    var reviewedPatient by remember(encounter.encounter_id) { mutableStateOf(patient) }
    val patientChanged = reviewedPatient.__v != patient.__v
    val values = remember(encounter.encounter_id) { mutableStateMapOf<String, String>() }
    var complaint by remember(encounter.encounter_id) { mutableStateOf("") }
    var allergies by remember(encounter.encounter_id, reviewedPatient.__v) { mutableStateOf(reviewedPatient.allergies.orEmpty()) }
    val originalStatus = reviewedPatient.allergy_review_status ?: if (reviewedPatient.allergies.isNullOrBlank()) "UNKNOWN" else "RECORDED"
    var allergyStatus by remember(encounter.encounter_id, reviewedPatient.__v) { mutableStateOf(originalStatus) }
    var allergyConfirmed by remember(encounter.encounter_id, reviewedPatient.__v) { mutableStateOf(false) }
    var allergyReason by remember(encounter.encounter_id, reviewedPatient.__v) { mutableStateOf("") }
    var notes by remember(encounter.encounter_id) { mutableStateOf("") }; var priority by remember(encounter.encounter_id) { mutableStateOf("ROUTINE") }
    InfoCard("Vital signs", "Enter measured values. The hospital applies its clinical validation and escalation rules.") {
        if (patientChanged) {
            Text("The patient record changed in another app. Your measurements are kept here. Load and review the updated allergy record before submitting.")
            OutlinedButton(onClick = { reviewedPatient = patient }, enabled = !state.busy) { Text("Load updated allergy record") }
        }
        listOf("temperature" to "Temperature (°C)", "systolic" to "Systolic BP (mmHg)", "diastolic" to "Diastolic BP (mmHg)", "pulse" to "Pulse (bpm)", "spo2" to "Oxygen saturation (%)", "weight" to "Weight (kg)", "height" to "Height (cm)", "pain" to "Pain score (0–10)", "glucose" to "Glucose (mg/dL, optional)").forEach { (key, title) -> Field(title, values[key].orEmpty(), { values[key] = it }, !state.busy) }
        Field("Presenting complaint", complaint, { complaint = it }, !state.busy, 2)
        Field("Allergies", allergies, { allergies = it; allergyConfirmed = false }, !state.busy)
        ChoiceRow(listOf("UNKNOWN", "NONE_REPORTED", "RECORDED"), allergyStatus, { allergyStatus = it; allergyConfirmed = false }, !state.busy)
        Row { Checkbox(allergyConfirmed, { allergyConfirmed = it }, enabled = !state.busy); Text("I reviewed the allergy record with the patient") }
        val allergyChanged = allergies != reviewedPatient.allergies.orEmpty() || allergyStatus != originalStatus
        if (allergyChanged) Field("Reason for allergy update", allergyReason, { allergyReason = it }, !state.busy)
        Field("Triage notes", notes, { notes = it }, !state.busy, 2)
        ChoiceRow(listOf("ROUTINE", "URGENT", "EMERGENCY"), priority, { priority = it }, !state.busy)
        val ranges = mapOf("temperature" to 25.0..45.0, "systolic" to 40.0..300.0, "diastolic" to 20.0..200.0, "pulse" to 20.0..250.0, "spo2" to 1.0..100.0, "weight" to 0.5..500.0, "height" to 20.0..250.0, "pain" to 0.0..10.0)
        val valid = allergyConfirmed && (!allergyChanged || allergyReason.isNotBlank()) &&
            (allergyStatus != "NONE_REPORTED" || allergies.isBlank()) && (allergyStatus != "RECORDED" || allergies.isNotBlank()) &&
            ranges.all { (key, range) -> values[key]?.toDoubleOrNull()?.let { it.isFinite() && it in range } == true } &&
            listOf("systolic", "diastolic", "pulse", "pain").all { values[it]?.toIntOrNull() != null } &&
            (values["systolic"]?.toIntOrNull() ?: 0) > (values["diastolic"]?.toIntOrNull() ?: 0) && complaint.isNotBlank() &&
            (values["glucose"].isNullOrBlank() || values["glucose"]?.toDoubleOrNull()?.let { it > 0 && it <= 1500 } == true)
        Button(onClick = {
            val payload = mutableMapOf<String, Any>()
            ranges.keys.forEach { key -> payload[key] = if (key in listOf("systolic", "diastolic", "pulse", "pain")) values[key]!!.toInt() else values[key]!!.toDouble() }
            values["glucose"]?.toDoubleOrNull()?.let { payload["glucose"] = it }
            payload["complaint"] = complaint; payload["allergies"] = allergies; payload["notes"] = notes
            payload["allergy_review_status"] = allergyStatus
            payload["priority"] = listOf("ROUTINE", "URGENT", "EMERGENCY").indexOf(priority)
            vm.transition("triage", payload, reviewedPatient.__v, true, allergyReason)
        }, enabled = valid && !patientChanged && !state.busy) { Text("Save triage and send to doctor") }
    }
}

@Composable private fun ConsultationPage(vm: PortalViewModel, state: PortalState) {
    var confirm by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(false) }
    ErrorCards(state, setOf("record", "catalogues", "queue"), vm::refresh)
    val amendment = state.amendingEncounterId != null
    val encounter = state.encounter
    if (encounter == null && !amendment) { EmptyCard("Visit completed or unavailable", "Use Records to review or amend a signed consultation, or return to the queue for the next patient."); return }
    val encounterId = state.amendingEncounterId ?: requireNotNull(encounter).encounter_id
    StatusPill(if (amendment) "AMENDMENT" else requireNotNull(encounter).status)
    state.record?.patient?.allergies?.takeIf { it.isNotBlank() }?.let { InfoCard("Recorded allergies", it) }
    state.record?.triage?.filter { it.encounter_id == encounterId }?.lastOrNull()?.let { triage -> InfoCard("Latest triage") { triage.data?.forEach { (key, value) -> if (value != null) Text("${label(key)}: $value") } } }
    if (!amendment && encounter?.status != "CONSULTATION") {
        Button(onClick = { vm.transition("start") }, enabled = !state.busy && state.record != null) { Text("Start consultation") }
        return
    }
    if (state.record == null) { Text("Loading the patient record before editing…"); return }
    if (state.draftConflict) InfoCard("Hospital record changed", "Another app updated this note or patient record. Your edits are kept here. Review the latest hospital draft before saving or signing.") {
        OutlinedButton(onClick = { reload = true }, enabled = !state.busy) { Text("Review hospital draft") }
    }
    if (reload) ConfirmAction("Load the hospital draft?", "This replaces your unsaved local edits with the latest hospital draft and patient record. Review them before signing.", "Load hospital draft", { reload = false }, { reload = false; vm.reloadHospitalDraft() })
    if (state.draftAvailable) InfoCard("A local draft is available", "Review it against the hospital record before saving.") { OutlinedButton(onClick = vm::recoverDraft, enabled = !state.busy) { Text("Recover local draft") } }
    val draft = state.draft
    InfoCard("Consultation", "Changes are kept in an encrypted local draft. Save to hospital when ready.") {
        Field("Chief complaint", draft.complaint.orEmpty(), { vm.updateDraft(draft.copy(complaint = it)) }, !state.busy, 2)
        Field("History", draft.history.orEmpty(), { vm.updateDraft(draft.copy(history = it)) }, !state.busy, 3)
        Field("Previous history", draft.previous_history.orEmpty(), { vm.updateDraft(draft.copy(previous_history = it)) }, !state.busy, 2)
        Field("Examination", draft.examination.orEmpty(), { vm.updateDraft(draft.copy(examination = it)) }, !state.busy, 3)
        Field("Assessment", draft.assessment.orEmpty(), { vm.updateDraft(draft.copy(assessment = it)) }, !state.busy, 2)
        var search by remember { mutableStateOf("") }
        Field("Search diagnoses", search, { search = it }, !state.busy)
        state.catalogues.diagnoses.filter { it.id in draft.diagnosis_ids || (search.isNotBlank() && it.name.contains(search, true)) }.take(30).forEach { diagnosis ->
            FilterChip(selected = diagnosis.id in draft.diagnosis_ids, onClick = { vm.updateDraft(draft.copy(diagnosis_ids = if (diagnosis.id in draft.diagnosis_ids) draft.diagnosis_ids - diagnosis.id else draft.diagnosis_ids + diagnosis.id)) }, enabled = !state.busy, label = { Text(diagnosis.name) })
        }
        Field("Treatment plan", draft.treatment.orEmpty(), { vm.updateDraft(draft.copy(treatment = it)) }, !state.busy, 2)
        Field("Advice and follow-up instructions", draft.advice.orEmpty(), { vm.updateDraft(draft.copy(advice = it)) }, !state.busy, 3)
    }
    MedicationEditor(vm, state)
    if (!amendment) {
    FollowUpEditor(vm, state)
    InfoCard("Laboratory tests") {
        var query by remember { mutableStateOf("") }
        Field("Search tests", query, { query = it }, !state.busy)
        state.catalogues.tests.filter { query.isNotBlank() && it.name.contains(query, true) }.take(20).forEach { test ->
            val ordered = state.record.labs.any { it.encounter_id == encounterId && it.test_id == test.id }
            TextButton(onClick = { vm.orderLab(test.id) }, enabled = !state.busy && !ordered) { Text(if (ordered) "${test.name} · ordered" else "Order ${test.name}") }
        }
    }
    }
    if (amendment) {
        InfoCard("Reason for amendment", "The hospital keeps every signed version. Follow-up appointments stay linked to the original visit.") {
            Field("Amendment reason", state.amendmentReason, vm::updateAmendmentReason, !state.busy, 2)
        }
    } else OutlinedButton(onClick = { vm.saveNote(false) }, enabled = !state.busy && !state.draftConflict) { Text("Save draft to hospital") }
    Button(onClick = { confirm = true }, enabled = !state.busy && !state.draftConflict && (!amendment || state.amendmentReason.isNotBlank()) && !draft.history.isNullOrBlank() && !draft.examination.isNullOrBlank() && !draft.assessment.isNullOrBlank() && !draft.advice.isNullOrBlank() && draft.diagnosis_ids.isNotEmpty()) { Text(if (amendment) "Sign amendment" else "Complete and sign consultation") }
    if (confirm) ConfirmAction(if (amendment) "Sign this amendment?" else "Sign this consultation?", if (amendment) "Review the patient identity, amended note, medicines and reason. This creates a new signed version visible to the patient and care team." else "Review the patient identity, diagnosis, medicines, advice and follow-up. Signing finalizes this visit and releases the prescription.", if (amendment) "Sign amendment" else "Sign and complete", { confirm = false }, { confirm = false; if (amendment) vm.saveAmendment() else vm.saveNote(true) })
}

@Composable private fun MedicationEditor(vm: PortalViewModel, state: PortalState) {
    var search by remember { mutableStateOf("") }; var selected by remember { mutableStateOf<DrugApi?>(null) }
    var dose by remember { mutableStateOf("") }; var frequency by remember { mutableStateOf("") }; var duration by remember { mutableStateOf("") }; var instructions by remember { mutableStateOf("") }
    InfoCard("Prescription", "Select a catalogue medicine and enter the intended regimen.") {
        state.draft.medications.forEachIndexed { index, medication ->
            Text("${medication.name ?: medication.drug_id} · ${medication.dose} · ${medication.frequency} · ${medication.duration}")
            TextButton(onClick = { vm.updateDraft(state.draft.copy(medications = state.draft.medications.filterIndexed { i, _ -> i != index })) }, enabled = !state.busy) { Text("Remove medicine") }
        }
        Field("Search medicine", search, { search = it; selected = null }, !state.busy)
        state.catalogues.drugs.filter { search.isNotBlank() && it.name.contains(search, true) }.take(15).forEach { drug -> FilterChip(selected = selected?.id == drug.id, onClick = { selected = drug }, enabled = !state.busy, label = { Text("${drug.name} ${drug.strength} · ${drug.form} · ${drug.route}") }) }
        if (selected != null) {
            Field("Dose", dose, { dose = it }, !state.busy); Field("Frequency", frequency, { frequency = it }, !state.busy)
            Field("Duration", duration, { duration = it }, !state.busy); Field("Instructions", instructions, { instructions = it }, !state.busy)
            OutlinedButton(onClick = {
                val drug = requireNotNull(selected)
                vm.updateDraft(state.draft.copy(medications = state.draft.medications + Medication(drug.id, dose, frequency, duration, instructions, drug.name, drug.strength, drug.form, drug.route)))
                selected = null; search = ""; dose = ""; frequency = ""; duration = ""; instructions = ""
            }, enabled = !state.busy && dose.isNotBlank() && frequency.isNotBlank() && duration.isNotBlank()) { Text("Add medicine") }
        }
    }
}

@Composable internal fun FollowUpEditor(vm: PortalViewModel, state: PortalState) {
    var show by remember { mutableStateOf(false) }; var date by remember { mutableStateOf(todayIndia()) }; var reason by remember { mutableStateOf("") }
    InfoCard("Follow-up", state.draft.follow_up?.get("scheduled_at")?.let(::displayDate)) {
        TextButton(onClick = { show = !show }) { Text("Schedule follow-up with this doctor") }
        if (show) {
            Field("Date (YYYY-MM-DD)", date, { date = it }, !state.busy); Field("Reason", reason, { reason = it }, !state.busy)
            OutlinedButton(onClick = { vm.slots(vm.user.id, date) }, enabled = validDate(date) && !state.busy) { Text("Find times") }
            state.availableSlots(vm.user.id, date).forEach { slot -> TextButton(onClick = { vm.updateDraft(state.draft.copy(follow_up = mapOf("doctor_id" to vm.user.id, "scheduled_at" to slot.scheduled_at, "reason" to reason))); show = false }, enabled = reason.isNotBlank() && !state.busy) { Text(displayDate(slot.scheduled_at)) } }
        }
        if (state.draft.follow_up != null) TextButton(onClick = { vm.updateDraft(state.draft.copy(follow_up = null)) }, enabled = !state.busy) { Text("Remove follow-up booking") }
    }
}

@Composable fun LabList(vm: PortalViewModel, state: PortalState, labs: List<Lab>, patient: Boolean) {
    if (labs.isEmpty()) EmptyCard("No laboratory orders", "Tests and released results will appear here.")
    PagedRows(labs) { lab -> InfoCard(lab.name ?: "Laboratory test", lab.patient_name) {
        StatusPill(lab.status)
        lab.result?.let { result -> Text("${result.value} ${result.unit}", style = MaterialTheme.typography.titleLarge); Text("Reference: ${result.reference_range}"); StatusPill(result.flag) }
        if (!patient && vm.user.role == Role.NURSE) {
            if (lab.status == "ORDERED") Button(onClick = { vm.lab(lab, "collect") }, enabled = !state.busy) { Text("Mark sample collected") }
            if (lab.status == "COLLECTED") Button(onClick = { vm.lab(lab, "process") }, enabled = !state.busy) { Text("Mark processing") }
            if (lab.status in listOf("PROCESSING", "AVAILABLE", "REVIEWED")) LabResultForm(vm, state, lab)
        }
        if (!patient && vm.user.role == Role.DOCTOR && lab.status == "AVAILABLE") Button(onClick = { vm.lab(lab, "review") }, enabled = !state.busy) { Text("Mark result reviewed") }
    } }
}
@Composable private fun LabResultForm(vm: PortalViewModel, state: PortalState, lab: Lab) {
    var open by remember(lab.id) { mutableStateOf(false) }; var value by remember(lab.id) { mutableStateOf("") }; var unit by remember(lab.id) { mutableStateOf(lab.unit.orEmpty()) }
    var reference by remember(lab.id) { mutableStateOf(lab.reference_range.orEmpty()) }; var flag by remember(lab.id) { mutableStateOf("NORMAL") }
    var reason by remember(lab.id) { mutableStateOf("") }; var verified by remember(lab.id) { mutableStateOf(false) }; var released by remember(lab.id) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, enabled = !state.busy) { Text("Enter verified result") }
    if (open) {
        Field("Result value", value, { value = it }, !state.busy); Field("Unit", unit, { unit = it }, !state.busy)
        Field("Reference range", reference, { reference = it }, !state.busy); ChoiceRow(listOf("NORMAL", "HIGH", "LOW", "CRITICAL"), flag, { flag = it }, !state.busy)
        Field("Reason / source", reason, { reason = it }, !state.busy)
        Row { Checkbox(verified, { verified = it }, enabled = !state.busy); Text("I have verified this result") }
        Row { Checkbox(released, { released = it }, enabled = !state.busy); Text("Release to patient") }
        Button(onClick = { vm.lab(lab, "result", mapOf("value" to value, "unit" to unit, "reference_range" to reference, "flag" to flag, "verified" to true, "released" to released, "reason" to reason)); open = false }, enabled = !state.busy && value.isNotBlank() && reference.isNotBlank() && reason.isNotBlank() && verified) { Text("Save verified result") }
    }
}
