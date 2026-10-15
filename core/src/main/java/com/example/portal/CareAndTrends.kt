package com.example.portal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.api.*
import com.example.domain.model.Role

@Composable fun CarePlanPage(vm: PortalViewModel, state: PortalState, patient: Boolean) {
    var selected by remember { mutableStateOf<CareOccurrence?>(null) }
    var value by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    PageHeading(if (patient) "Today's care plan" else "Patient care plan", "Instructions from the care team. Times are in India Standard Time.")
    ErrorCards(state, setOf("care plan", "today", "timeline"), vm::refresh)
    if (state.today.isEmpty() && "today" !in state.errors) EmptyCard("No tasks for today", "Tasks assigned by your care team will appear here.")
    state.today.forEach { task ->
        InfoCard(task.title, "${task.due_time} · ${label(task.task_type)}") {
            StatusPill(task.status); Text(task.instruction)
            task.recorded_value?.let { Text("Recorded: $it ${task.target_unit.orEmpty()}") }
            task.patient_note?.let { Text(it) }
            if (task.medication_source?.get("outdated") == true) Text("Prescription updated. Contact your care team before recording this task.", color = MaterialTheme.colorScheme.error)
            if (patient && task.status == "DUE" && task.medication_source?.get("outdated") != true) {
                Button(onClick = { selected = task; value = ""; note = "" }, enabled = !state.busy) { Text(if (task.task_type == "MEASUREMENT") "Record measurement" else "Record response") }
            }
        }
    }
    if (!patient && vm.user.role == Role.DOCTOR) {
        if (state.care.plan == null) Button(onClick = { vm.action("Care plan created") { vm.api.createPlan(requireNotNull(state.patientId), mapOf("start_date" to todayIndia())); vm.reloadCare() } }, enabled = !state.busy) { Text("Create care plan") }
        else {
            OutlinedButton(onClick = { editing = !editing }, enabled = !state.busy) { Text(if (editing) "Close task editor" else "Add care task") }
            if (editing) TaskEditor(vm, state)
        }
    }
    if (!patient && state.care.plan != null) {
        OutlinedButton(onClick = { vm.action("Care plan review recorded") { vm.api.reviewPlan(requireNotNull(state.patientId), mapOf("reviewed_through" to nowIso())) } }, enabled = !state.busy) { Text("Mark care plan reviewed") }
    }
    state.care.tasks.filter { it.status == "ACTIVE" }.forEach { task -> InfoCard(task.title, "${task.scheduled_time} · ${task.instruction}") {
        Text("Starts ${task.start_date}${task.end_date?.let { " · Ends $it" }.orEmpty()}")
        if (!patient && vm.user.role == Role.DOCTOR) {
            var edit by remember(task.id) { mutableStateOf(false) }
            TextButton(onClick = { edit = !edit }, enabled = !state.busy) { Text(if (edit) "Close changes" else "Change or stop task") }
            if (edit) {
                TaskEditor(vm, state, task)
                StopTask(vm, state, task)
            }
        }
    } }
    if (selected != null) {
        val task = selected!!
        AlertDialog(onDismissRequest = { if (!state.busy) selected = null }, title = { Text(task.title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(task.instruction)
                if (task.task_type == "MEASUREMENT") Field("Value (${task.target_unit.orEmpty()})", value, { value = it }, !state.busy)
                Field("Optional note", note, { note = it }, !state.busy, 2)
                Text("Review your entry before saving. Your care team can see it.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            TextButton(onClick = {
                val status = when (task.task_type) { "MEASUREMENT" -> "RECORDED"; "ACTIVITY" -> "COMPLETED"; else -> "TAKEN" }
                vm.adherence(task, status, value.toDoubleOrNull(), note); selected = null
            }, enabled = !state.busy && (task.task_type != "MEASUREMENT" || value.toDoubleOrNull()?.isFinite() == true)) { Text(if (task.task_type in listOf("MEDICATION", "INSULIN")) "Mark taken" else "Save") }
        }, dismissButton = {
            Row {
                TextButton(onClick = { vm.adherence(task, "SKIPPED", null, note); selected = null }, enabled = !state.busy) { Text("Skip task") }
                TextButton(onClick = { selected = null }, enabled = !state.busy) { Text("Close") }
            }
        })
    }
}

@Composable private fun TaskEditor(vm: PortalViewModel, state: PortalState, existing: CareTask? = null) {
    var kind by remember { mutableStateOf(existing?.task_type ?: "MEASUREMENT") }; var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var instruction by remember { mutableStateOf(existing?.instruction.orEmpty()) }; var time by remember { mutableStateOf(existing?.scheduled_time ?: "08:00") }
    var date by remember { mutableStateOf(todayIndia()) }; var observation by remember { mutableStateOf(existing?.observation_type ?: "GLUCOSE") }
    var timing by remember { mutableStateOf(existing?.timing_relation ?: "FASTING") }; var unit by remember { mutableStateOf(existing?.target_unit ?: "mg/dL") }
    var source by remember { mutableStateOf(existing?.prescription_item_id.orEmpty()) }
    var reason by remember { mutableStateOf("") }
    InfoCard("New daily task", "Medication and insulin tasks must use a current, authorized prescription item.") {
        ChoiceRow(listOf("MEASUREMENT", "ACTIVITY", "MEDICATION", "INSULIN"), kind, { kind = it }, !state.busy)
        Field("Title", title, { title = it }, !state.busy); Field("Instruction", instruction, { instruction = it }, !state.busy, 2)
        Field("Daily time (HH:MM)", time, { time = it }, !state.busy); Field("Start date (YYYY-MM-DD)", date, { date = it }, !state.busy)
        if (kind == "MEASUREMENT") {
            ChoiceRow(listOf("GLUCOSE", "WEIGHT", "TEMPERATURE", "PULSE", "SPO2"), observation, { observation = it }, !state.busy)
            Field("Timing relation", timing, { timing = it }, !state.busy); Field("Unit", unit, { unit = it }, !state.busy)
        }
        if (kind in listOf("MEDICATION", "INSULIN")) state.care.available_prescription_items.filter { it.prescription_version == it.current_prescription_version }.forEach { item ->
            FilterChip(selected = source == item.id, onClick = { source = item.id }, label = { Text("${item.drug_name} ${item.strength.orEmpty()} · ${item.dose} · ${item.frequency}") })
        }
        if (existing != null) Field("Reason for change", reason, { reason = it }, !state.busy)
        Button(onClick = {
            val task = mutableMapOf<String, Any>("task_type" to kind, "title" to title, "instruction" to instruction, "frequency_type" to "DAILY", "scheduled_time" to time, "start_date" to date)
            if (kind == "MEASUREMENT") { task["observation_type"] = observation; task["timing_relation"] = timing; task["target_unit"] = unit }
            if (kind in listOf("MEDICATION", "INSULIN")) task["prescription_item_id"] = source
            vm.action("Care task updated") {
                val version = requireNotNull(state.care.plan).__v
                if (existing == null) vm.api.createTask(requireNotNull(state.patientId), mapOf("__v" to version, "task" to task))
                else vm.api.changeTask(existing.id, mapOf("__v" to version, "expected_revision" to existing.revision, "action" to "REPLACE", "effective_date" to date, "reason" to reason, "task" to task))
                vm.reloadCare()
            }
        }, enabled = !state.busy && title.isNotBlank() && instruction.isNotBlank() && validDate(date) && time.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) && (kind !in listOf("MEDICATION", "INSULIN") || source.isNotBlank()) && (existing == null || reason.isNotBlank())) { Text(if (existing == null) "Add task" else "Save task revision") }
    }
}

@Composable fun TrendsPage(vm: PortalViewModel, state: PortalState, canRecord: Boolean) {
    var recordType by remember { mutableStateOf("GLUCOSE") }; var adding by remember { mutableStateOf(false) }
    PageHeading("Health trends", "Measurements, activity and care responses shared with the care team.")
    ErrorCards(state, setOf("timeline", "measurements"), vm::refresh)
    if (canRecord) OutlinedButton(onClick = { adding = !adding }, enabled = !state.busy) { Text(if (adding) "Close entry" else "Log measurement or activity") }
    if (adding) SelfRecordForm(vm, state)
    ChoiceRow(listOf("GLUCOSE", "BLOOD_PRESSURE", "WEIGHT", "TEMPERATURE", "PULSE", "SPO2", "ACTIVITY", "MEDICATION", "INSULIN"), recordType, { recordType = it })
    val records = state.selfRecords.filter { it.record_type == recordType }.sortedByDescending { it.observed_at }
    if (recordType !in listOf("ACTIVITY", "MEDICATION", "INSULIN")) {
        val points = records.mapNotNull { row -> row.numeric_value?.let { row.observed_at to it } } + state.timeline.flatMap { row -> row.observation?.takeIf { it.observation_type == recordType }?.let { observation -> observation.components.map { observation.observed_at to it.numeric_value } }.orEmpty() }
        if (points.isNotEmpty()) InfoCard("Recent readings", "Values are shown as recorded. These charts do not provide a diagnosis.") { TrendLine(points.sortedBy { it.first }.takeLast(30).map { it.second }, records.firstOrNull()?.unit ?: state.timeline.firstNotNullOfOrNull { it.observation?.components?.firstOrNull()?.unit }.orEmpty()) }
    }
    if (records.isEmpty() && state.timeline.none { it.task_type == recordType || it.observation?.observation_type == recordType }) EmptyCard("No entries yet", "Your recorded history will appear here.")
    PagedRows(records) { row -> InfoCard(if (row.record_type == "ACTIVITY") row.activity_name.orEmpty() else label(row.record_type), displayDate(row.observed_at)) {
        if (row.record_type == "ACTIVITY") Text("${row.duration_minutes} minutes")
        else Text("${row.numeric_value ?: "—"}${row.secondary_numeric_value?.let { " / $it" }.orEmpty()} ${row.unit.orEmpty()}", style = MaterialTheme.typography.titleLarge)
        row.timing_context?.let { Text(label(it)) }; row.patient_note?.let { Text(it) }
        Text("Patient recorded", style = MaterialTheme.typography.labelSmall)
    } }
    PagedRows(state.timeline.filter { it.task_type == recordType || it.observation?.observation_type == recordType }) { row -> InfoCard(row.title ?: label(row.task_type.orEmpty()), displayDate(row.recorded_at)) {
        StatusPill(row.status)
        row.observation?.components?.forEach { Text("${it.numeric_value} ${it.unit}") }
        row.patient_note?.let { Text(it) }
    } }
}

@Composable private fun SelfRecordForm(vm: PortalViewModel, state: PortalState) {
    var type by remember { mutableStateOf("GLUCOSE") }; var first by remember { mutableStateOf("") }; var second by remember { mutableStateOf("") }
    var timing by remember { mutableStateOf("FASTING") }; var note by remember { mutableStateOf("") }; var activity by remember { mutableStateOf("Walking") }
    LaunchedEffect(state.message) { if (state.message == "Record saved") { first = ""; second = ""; note = "" } }
    val units = mapOf("GLUCOSE" to "mg/dL", "BLOOD_PRESSURE" to "mmHg", "WEIGHT" to "kg", "TEMPERATURE" to "°C", "PULSE" to "bpm", "SPO2" to "%")
    InfoCard("Add your record", "Saved as patient reported data, with the current time.") {
        ChoiceRow(units.keys.toList() + "ACTIVITY", type, { type = it; first = ""; second = "" }, !state.busy)
        if (type == "ACTIVITY") { Field("Activity", activity, { activity = it }, !state.busy); Field("Duration (minutes)", first, { first = it }, !state.busy) }
        else {
            if (type == "GLUCOSE") ChoiceRow(listOf("FASTING", "AFTER_BREAKFAST", "AFTER_LUNCH", "POST_DINNER", "RANDOM"), timing, { timing = it }, !state.busy)
            Field(if (type == "BLOOD_PRESSURE") "Systolic (mmHg)" else "Value (${units[type]})", first, { first = it }, !state.busy)
            if (type == "BLOOD_PRESSURE") Field("Diastolic (mmHg)", second, { second = it }, !state.busy)
        }
        Field("Optional note", note, { note = it }, !state.busy, 2)
        val valid = if (type == "ACTIVITY") first.toIntOrNull() in 1..1440 && activity.isNotBlank()
            else first.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true && (type != "BLOOD_PRESSURE" || (second.toDoubleOrNull()?.let { it > 0 && it < (first.toDoubleOrNull() ?: 0.0) } == true))
        Button(onClick = {
            val body = mutableMapOf<String, Any>("record_type" to type, "observed_at" to nowIso())
            if (type == "ACTIVITY") { body["activity_name"] = activity; body["duration_minutes"] = first.toInt() }
            else { body["numeric_value"] = first.toDouble(); body["unit"] = requireNotNull(units[type]); if (type == "BLOOD_PRESSURE") body["secondary_numeric_value"] = second.toDouble() }
            if (type == "GLUCOSE") body["timing_context"] = timing
            if (note.isNotBlank()) body["patient_note"] = note
            vm.selfRecord(body)
        }, enabled = valid && !state.busy) { Text("Save record") }
    }
}

@Composable private fun StopTask(vm: PortalViewModel, state: PortalState, task: CareTask) {
    var date by remember(task.id) { mutableStateOf(todayIndia()) }
    var reason by remember(task.id) { mutableStateOf("") }
    var confirm by remember(task.id) { mutableStateOf(false) }
    Field("Stop from date (YYYY-MM-DD)", date, { date = it }, !state.busy)
    Field("Reason for stopping", reason, { reason = it }, !state.busy)
    TextButton(onClick = { confirm = true }, enabled = !state.busy && validDate(date) && reason.isNotBlank()) { Text("Stop this task") }
    if (confirm) ConfirmAction("Stop ${task.title}?", "The change takes effect on $date. The hospital will reject changes that conflict with already recorded responses.", "Stop task", { confirm = false }, {
        confirm = false
        vm.action("Task stopped") { vm.api.changeTask(task.id, mapOf("__v" to requireNotNull(state.care.plan).__v, "expected_revision" to task.revision, "action" to "DEACTIVATE", "effective_date" to date, "reason" to reason)); vm.reloadCare() }
    })
}
