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

@Composable internal fun TaskEditor(vm: PortalViewModel, state: PortalState, existing: CareTask? = null) {
    key(state.patientId, existing?.id) { CareTaskEditor(vm, state, existing) }
}

@Composable private fun CareTaskEditor(vm: PortalViewModel, state: PortalState, existing: CareTask?) {
    val reviewed = remember { CareTaskReview.capture(state.care, existing) }
    val changed = !reviewed.matches(state.care)
    val enabled = !state.busy && !changed
    var kind by remember { mutableStateOf(existing?.task_type ?: "MEASUREMENT") }; var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var instruction by remember { mutableStateOf(existing?.instruction.orEmpty()) }; var time by remember { mutableStateOf(existing?.scheduled_time ?: "08:00") }
    var date by remember { mutableStateOf(taskEffectiveDate(existing)) }; var observation by remember { mutableStateOf(existing?.observation_type ?: "GLUCOSE") }
    var timing by remember { mutableStateOf(existing?.timing_relation ?: "FASTING") }; var unit by remember { mutableStateOf(existing?.target_unit ?: "mg/dL") }
    var source by remember { mutableStateOf(existing?.prescription_item_id.orEmpty()) }
    var reason by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf(CareSchedule.from(existing)) }
    InfoCard(if (existing == null) "New care task" else "Revise care task", "Times and dates use India Standard Time. Medication and insulin tasks require a current prescription item.") {
        if (changed) Text("The care plan changed in another app. Close this editor and review the updated plan before making changes.", color = MaterialTheme.colorScheme.error)
        ChoiceRow(listOf("MEASUREMENT", "ACTIVITY", "MEDICATION", "INSULIN"), kind, { kind = it }, enabled)
        Field("Title", title, { title = it }, enabled); Field("Instruction", instruction, { instruction = it }, enabled, 2)
        Field("Scheduled time (HH:MM)", time, { time = it }, enabled)
        Field(if (existing == null) "Start date (YYYY-MM-DD)" else "Effective date (YYYY-MM-DD)", date, { date = it }, enabled)
        Text("Repeat", style = MaterialTheme.typography.titleSmall)
        listOf("DAILY" to "Every day", "SELECTED_DAYS" to "Selected weekdays", "EVERY_N_DAYS" to "Every N days", "ONCE" to "Once").forEach { (value, title) ->
            FilterChip(selected = schedule.frequency == value, onClick = { schedule = schedule.copy(frequency = value) }, enabled = enabled, label = { Text(title) })
        }
        when (schedule.frequency) {
            "SELECTED_DAYS" -> listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").forEachIndexed { day, name ->
                val bit = 1 shl day
                FilterChip(selected = schedule.weekdays and bit != 0, onClick = { schedule = schedule.copy(weekdays = schedule.weekdays xor bit) }, enabled = enabled, label = { Text(name) })
            }
            "EVERY_N_DAYS" -> Field("Interval in days (1–366)", schedule.interval, { schedule = schedule.copy(interval = it) }, enabled)
            "ONCE" -> Field("Occurrence date (YYYY-MM-DD)", schedule.onceDate, { schedule = schedule.copy(onceDate = it) }, enabled)
        }
        Field("End date (optional, YYYY-MM-DD)", schedule.endDate, { schedule = schedule.copy(endDate = it) }, enabled)
        Field("Response window before (minutes)", schedule.before, { schedule = schedule.copy(before = it) }, enabled)
        Field("Response window after (minutes)", schedule.after, { schedule = schedule.copy(after = it) }, enabled)
        Field("Expected duration (optional, minutes)", schedule.duration, { schedule = schedule.copy(duration = it) }, enabled)
        Row { Checkbox(schedule.reminder, { schedule = schedule.copy(reminder = it) }, enabled = enabled); Text("Enable task reminders") }
        if (kind == "MEASUREMENT") {
            ChoiceRow(listOf("GLUCOSE", "WEIGHT", "TEMPERATURE", "PULSE", "SPO2"), observation, { observation = it; unit = mapOf("GLUCOSE" to "mg/dL", "WEIGHT" to "kg", "TEMPERATURE" to "°C", "PULSE" to "bpm", "SPO2" to "%")[it].orEmpty(); timing = if (it == "GLUCOSE") "FASTING" else "SCHEDULED" }, enabled)
            Field("Timing relation", timing, { timing = it }, enabled); Field("Unit", unit, { unit = it }, enabled)
        }
        val prescriptionItems = state.care.available_prescription_items.filter { it.prescription_version == it.current_prescription_version }
        if (kind in listOf("MEDICATION", "INSULIN")) {
            if (prescriptionItems.isEmpty()) Text("No current prescription items are available. Authorize a prescription before adding this task.")
            prescriptionItems.forEach { item -> FilterChip(selected = source == item.id, onClick = { source = item.id }, enabled = enabled, label = { Text("${item.drug_name} ${item.strength.orEmpty()} · ${item.dose} · ${item.frequency}") }) }
        }
        if (existing != null) Field("Reason for change", reason, { reason = it }, enabled)
        val scheduleError = schedule.validationError(date)
        scheduleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val task = mutableMapOf<String, Any>("task_type" to kind, "title" to title.trim(), "instruction" to instruction.trim(), "scheduled_time" to time, "start_date" to date)
            task.putAll(schedule.payload(date))
            if (kind == "MEASUREMENT") { task["observation_type"] = observation; task["timing_relation"] = timing; task["target_unit"] = unit }
            if (kind in listOf("MEDICATION", "INSULIN")) task["prescription_item_id"] = source
            vm.action("Care task updated") {
                if (existing == null) vm.api.createTask(requireNotNull(state.patientId), mapOf("__v" to requireNotNull(reviewed.planVersion), "task" to task))
                else vm.api.changeTask(existing.id, mapOf("__v" to requireNotNull(reviewed.planVersion), "expected_revision" to requireNotNull(reviewed.taskRevision), "action" to "REPLACE", "effective_date" to date, "reason" to reason, "task" to task))
                vm.reloadCare()
            }
        }, enabled = enabled && title.isNotBlank() && title.length <= 160 && instruction.isNotBlank() && instruction.length <= 1000 && scheduleError == null && time.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) && (kind != "MEASUREMENT" || (timing.isNotBlank() && unit.isNotBlank())) && (kind !in listOf("MEDICATION", "INSULIN") || prescriptionItems.any { it.id == source }) && (existing == null || (reason.isNotBlank() && date > existing.start_date && date >= todayIndia()))) { Text(if (existing == null) "Add task" else "Save task revision") }
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
        trendSeries(recordType, records, state.timeline).forEach { series ->
            InfoCard("${label(series.component)} readings", "Values are shown as recorded. These charts do not provide a diagnosis.") {
                TrendLine(series.points.map { it.second }, series.unit)
            }
        }
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

internal data class TrendSeries(val component: String, val unit: String, val points: List<Pair<String, Double>>)

/** Never connect incompatible units or join systolic and diastolic readings into one line. */
internal fun trendSeries(type: String, records: List<SelfRecord>, timeline: List<AdherenceEntry>): List<TrendSeries> {
    val points = mutableListOf<Triple<Pair<String, String>, String, Double>>()
    fun add(component: String, unit: String, at: String, value: Double?) {
        if (value?.isFinite() == true) points += Triple(component to unit, at, value)
    }
    records.filter { it.record_type == type }.forEach { row ->
        add(if (type == "BLOOD_PRESSURE") "SYSTOLIC" else type, row.unit.orEmpty(), row.observed_at, row.numeric_value)
        if (type == "BLOOD_PRESSURE") add("DIASTOLIC", row.unit.orEmpty(), row.observed_at, row.secondary_numeric_value)
    }
    timeline.mapNotNull { it.observation }.filter { it.observation_type == type }.forEach { observation ->
        observation.components.forEach { add(it.component_code, it.unit, observation.observed_at, it.numeric_value) }
    }
    return points.groupBy { it.first }.map { (key, rows) ->
        TrendSeries(key.first, key.second, rows.sortedBy { it.second }.takeLast(30).map { it.second to it.third })
    }
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
    val reviewed = remember(task.id, state.patientId) { CareTaskReview.capture(state.care, task) }
    val changed = !reviewed.matches(state.care)
    var date by remember(task.id) { mutableStateOf(taskEffectiveDate(task)) }
    var reason by remember(task.id) { mutableStateOf("") }
    var confirm by remember(task.id) { mutableStateOf(false) }
    Field("Stop from date (YYYY-MM-DD)", date, { date = it }, !state.busy && !changed)
    Field("Reason for stopping", reason, { reason = it }, !state.busy && !changed)
    TextButton(onClick = { confirm = true }, enabled = !state.busy && !changed && validDate(date) && date > task.start_date && date >= todayIndia() && reason.isNotBlank()) { Text("Stop this task") }
    if (confirm) ConfirmAction("Stop ${task.title}?", "The change takes effect on $date. The hospital will reject changes that conflict with already recorded responses.", "Stop task", { confirm = false }, {
        confirm = false
        if (!changed) vm.action("Task stopped") { vm.api.changeTask(task.id, mapOf("__v" to requireNotNull(reviewed.planVersion), "expected_revision" to requireNotNull(reviewed.taskRevision), "action" to "DEACTIVATE", "effective_date" to date, "reason" to reason)); vm.reloadCare() }
    })
}

internal data class CareTaskReview(val planId: String?, val planVersion: Int?, val taskId: String?, val taskRevision: Int?) {
    fun matches(care: CarePlanBundle): Boolean = planId != null && planId == care.plan?.id && planVersion == care.plan?.__v &&
        (taskId == null || care.tasks.any { it.id == taskId && it.revision == taskRevision && it.status == "ACTIVE" })
    companion object { fun capture(care: CarePlanBundle, task: CareTask?) = CareTaskReview(care.plan?.id, care.plan?.__v, task?.id, task?.revision) }
}

internal fun taskEffectiveDate(task: CareTask?): String = task?.let {
    runCatching { maxOf(todayIndia(), java.time.LocalDate.parse(it.start_date).plusDays(1).toString()) }.getOrNull()
} ?: todayIndia()

internal data class CareSchedule(
    val frequency: String = "DAILY", val weekdays: Int = 0, val interval: String = "2", val onceDate: String = todayIndia(),
    val endDate: String = "", val before: String = "0", val after: String = "120", val duration: String = "", val reminder: Boolean = true
) {
    fun validationError(start: String): String? = when {
        !validDate(start) -> "Enter a valid start or effective date."
        endDate.isNotBlank() && (!validDate(endDate) || endDate < start) -> "End date must be on or after the start date."
        frequency !in listOf("DAILY", "SELECTED_DAYS", "EVERY_N_DAYS", "ONCE") -> "Choose a repeat schedule."
        frequency == "SELECTED_DAYS" && weekdays !in 1..127 -> "Select at least one weekday."
        frequency == "EVERY_N_DAYS" && interval.toIntOrNull() !in 1..366 -> "Enter an interval from 1 to 366 days."
        frequency == "ONCE" && (!validDate(onceDate) || onceDate < start || (endDate.isNotBlank() && onceDate > endDate)) -> "Occurrence date must fall within the task dates."
        before.toIntOrNull() !in 0..1440 || after.toIntOrNull() !in 0..1440 -> "Response windows must be between 0 and 1440 minutes."
        duration.isNotBlank() && duration.toIntOrNull() !in 1..1440 -> "Duration must be between 1 and 1440 minutes."
        else -> null
    }
    fun payload(start: String): Map<String, Any> {
        require(validationError(start) == null)
        return buildMap {
            put("frequency_type", frequency); put("window_before_minutes", before.toInt()); put("window_after_minutes", after.toInt()); put("reminder_enabled", reminder)
            when (frequency) { "SELECTED_DAYS" -> put("weekdays_mask", weekdays); "EVERY_N_DAYS" -> put("interval_days", interval.toInt()); "ONCE" -> put("one_time_date", onceDate) }
            if (endDate.isNotBlank()) put("end_date", endDate)
            if (duration.isNotBlank()) put("expected_duration_minutes", duration.toInt())
        }
    }
    companion object { fun from(task: CareTask?) = task?.let { CareSchedule(it.frequency_type, it.weekdays_mask ?: 0, (it.interval_days ?: 2).toString(), it.one_time_date ?: todayIndia(), it.end_date.orEmpty(), it.window_before_minutes.toString(), it.window_after_minutes.toString(), it.expected_duration_minutes?.toString().orEmpty(), it.reminder_enabled == true || it.reminder_enabled == 1 || it.reminder_enabled == 1.0) } ?: CareSchedule() }
}
