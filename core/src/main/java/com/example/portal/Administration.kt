package com.example.portal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.example.data.api.*
import com.example.domain.model.Role
import com.example.address.BirthDateField
import com.example.address.IndianAddressFields
import com.example.address.validBirthDate

@Composable fun AdminOverview(vm: PortalViewModel, state: PortalState) {
    PageHeading("Hospital overview", "Current operations from the shared hospital backend.")
    ErrorCards(state, setOf("overview"), vm::refresh)
    listOf("requires_action" to "Appointment requests", "today_confirmed" to "Today's confirmed visits", "checked_in" to "Checked in", "waiting" to "Waiting for doctor", "triage" to "In triage", "consultation" to "In consultation", "completed" to "Completed", "results_pending" to "Results pending review").forEach { (key, title) ->
        InfoCard(title) { Text(state.dashboard[key]?.let { if (it is Number) it.toInt().toString() else it.toString() } ?: "—", style = MaterialTheme.typography.headlineLarge) }
    }
}
@Composable fun StaffAdministration(vm: PortalViewModel, state: PortalState) {
    var add by remember { mutableStateOf(false) }; var changing by remember { mutableStateOf<StaffMember?>(null) }
    PageHeading("Staff administration", "Manage staff accounts and access.")
    ErrorCards(state, setOf("staff"), vm::refresh)
    OutlinedButton(onClick = { add = !add }, enabled = !state.busy) { Text(if (add) "Close new account" else "Create staff account") }
    if (add) {
        var id by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var role by remember { mutableStateOf("NURSE") }
        var department by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
        InfoCard("Create account", "The staff member must change the initial password after signing in.") {
            Field("Username", id, { id = it }, !state.busy); Field("Full name", name, { name = it }, !state.busy)
            ChoiceRow(listOf("NURSE", "DOCTOR", "ADMIN"), role, { role = it }, !state.busy)
            state.directory.departments.forEach { d -> FilterChip(selected = department == d.name, onClick = { department = d.name }, enabled = !state.busy, label = { Text(d.name) }) }
            OutlinedTextField(password, { password = it }, label = { Text("Initial password") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Text("Use 12–72 characters with uppercase, lowercase, a number and a symbol.", style = MaterialTheme.typography.bodySmall)
            val strong = password.length in 12..72 && password.any { it.isUpperCase() } && password.any { it.isLowerCase() } && password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }
            Button(onClick = { vm.action("Staff account created") { vm.api.addStaff(mapOf("id" to id, "name" to name, "role" to role, "department" to department, "password" to password)); password = ""; add = false; vm.reloadStaff(vm.api.staff()) } }, enabled = !state.busy && id.matches(Regex("[a-zA-Z0-9_.-]{3,60}")) && name.isNotBlank() && department.isNotBlank() && strong) { Text("Create account") }
        }
    }
    PagedRows(state.staff) { member -> InfoCard(member.name, "${label(member.role)} · ${member.department.orEmpty()}") {
        StatusPill(if (member.active) "ACTIVE" else "INACTIVE")
        if (member.id != vm.user.id) TextButton(onClick = { changing = member }, enabled = !state.busy) { Text(if (member.active) "Disable access" else "Enable access") }
    } }
    changing?.let { member -> ConfirmAction(if (member.active) "Disable ${member.name}?" else "Enable ${member.name}?", if (member.active) "Their active sessions will be revoked." else "This account will be able to sign in again.", "Confirm", { changing = null }, {
        vm.action("Staff access updated") { vm.api.staffStatus(member.id, mapOf("active" to !member.active)); vm.reloadStaff(vm.api.staff()) }; changing = null
    }) }
}
@Composable fun SchedulePage(vm: PortalViewModel, state: PortalState) {
    var doctor by remember { mutableStateOf("") }; var weekday by remember { mutableStateOf("1") }
    var start by remember { mutableStateOf("09:00") }; var end by remember { mutableStateOf("13:00") }
    var minutes by remember { mutableStateOf("15") }; var room by remember { mutableStateOf("") }
    var breakStart by remember { mutableStateOf("") }; var breakEnd by remember { mutableStateOf("") }
    PageHeading("Doctor schedules", "Weekly sessions used to offer appointment slots.")
    ErrorCards(state, setOf("directory"), vm::refresh)
    state.directory.doctors.forEach { d -> FilterChip(selected = doctor == d.id, onClick = { doctor = d.id }, label = { Text(d.name) }) }
    val selectedDoctor = state.directory.doctors.find { it.id == doctor }
    LaunchedEffect(doctor, weekday, state.directory) {
        val old = selectedDoctor?.schedules?.find { it.weekday == weekday.toInt() }
        start = old?.start_time ?: "09:00"; end = old?.end_time ?: "13:00"
        room = old?.room.orEmpty(); minutes = (old?.slot_minutes ?: 15).toString()
        breakStart = old?.break_start.orEmpty(); breakEnd = old?.break_end.orEmpty()
    }
    selectedDoctor?.schedules?.forEach { schedule -> InfoCard("${listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").getOrElse(schedule.weekday) { "Day" }} · ${schedule.start_time}–${schedule.end_time}", "${schedule.room} · ${schedule.slot_minutes} minute slots") }
    InfoCard("Configure weekly session", "Changes apply to newly offered slots. Existing appointments keep their time.") {
        ChoiceRow((0..6).map { it.toString() }, weekday, { weekday = it }, !state.busy)
        Text("0 Sunday · 1 Monday · 2 Tuesday · 3 Wednesday · 4 Thursday · 5 Friday · 6 Saturday", style = MaterialTheme.typography.bodySmall)
        Field("Start (HH:MM)", start, { start = it }, !state.busy); Field("End (HH:MM)", end, { end = it }, !state.busy)
        Field("Break start (HH:MM, optional)", breakStart, { breakStart = it }, !state.busy)
        Field("Break end (HH:MM, optional)", breakEnd, { breakEnd = it }, !state.busy)
        Field("Slot duration (minutes)", minutes, { minutes = it }, !state.busy); Field("Room", room, { room = it }, !state.busy)
        Button(onClick = { vm.action("Schedule updated") {
            val doc = requireNotNull(selectedDoctor); val department = state.directory.departments.first { it.name == doc.department }
            val old = doc.schedules.find { it.weekday == weekday.toInt() }
            val data = mutableMapOf<String, Any>("doctor_id" to doc.id, "department_id" to department.id, "weekday" to weekday.toInt(), "start_time" to start, "end_time" to end, "slot_minutes" to minutes.toInt(), "room" to room)
            if (breakStart.isNotBlank()) { data["break_start"] = breakStart; data["break_end"] = breakEnd }
            old?.let { data["__v"] = it.__v }; vm.api.schedule(data); vm.reloadDirectory(vm.api.directory())
        } }, enabled = !state.busy && selectedDoctor != null && room.isNotBlank() && minutes.toIntOrNull() in 5..120 && listOf(start, end).all { it.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) } && start < end && ((breakStart.isBlank() && breakEnd.isBlank()) || (breakStart.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) && breakEnd.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) && breakStart >= start && breakStart < breakEnd && breakEnd <= end))) { Text("Save schedule") }
    }
}

@Composable fun PatientRegistration(vm: PortalViewModel, state: PortalState, onOpen: (String, QueuePatient?) -> Unit = { _, _ -> }) {
    var search by remember { mutableStateOf("") }; var create by remember { mutableStateOf(false) }
    PageHeading(if (vm.user.role == Role.DOCTOR) "Your patients" else "Patient directory", if (vm.user.role == Role.DOCTOR) vm.user.name else "Search existing records before registering a patient.")
    ErrorCards(state, setOf("patients"), vm::refresh)
    Field("Search name, MRN or mobile", search, { search = it }, !state.busy)
    OutlinedButton(onClick = { vm.searchPatients(search) }, enabled = !state.busy && search.isNotBlank()) { Text("Find patient") }
    LaunchedEffect(Unit) { vm.searchPatients("") }
    OutlinedButton(onClick = { create = !create }, enabled = !state.busy) { Text(if (create) "Close registration" else "Register new patient") }
    if (!create) PagedRows(state.patients) { patient ->
        var expanded by remember(patient.id) { mutableStateOf(false) }
        var tasks by remember(patient.id) { mutableStateOf<List<CareOccurrence>?>(null) }
        var progressError by remember(patient.id) { mutableStateOf(false) }
        LaunchedEffect(patient.id, state.loading, state.message) {
            if (vm.user.role == Role.DOCTOR) runCatching { vm.api.today(patient.id) }.onSuccess { tasks = it; progressError = false }.onFailure { progressError = true }
        }
        OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Text(patient.name.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString(""), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(patient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(patient.mrn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ChevronRight, if (expanded) "Hide patient actions" else "Show patient actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (vm.user.role == Role.DOCTOR) {
                    val progress = tasks?.let { careCompletion(it.map { task -> task.status }) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (progress == null) { if (progressError) "Care progress unavailable" else "Loading care progress…" } else if (progress.total == 0) "Care plan not assigned" else "${progress.completed} of ${progress.total} care tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (progress?.allCompleted == true) Icon(Icons.Outlined.CheckCircle, "All care tasks completed", tint = Color(0xFF16754B))
                    }
                    if (progress != null && progress.total > 0) LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth(), color = if (progress.allCompleted) Color(0xFF16754B) else MaterialTheme.colorScheme.primary)
                }
                if (expanded) {
        Text(patient.phone, style = MaterialTheme.typography.bodyMedium)
        Text(if (patient.portal_access_enabled == 1) "Mobile access enabled" else "Mobile access disabled")
        if (vm.user.role == Role.DOCTOR) {
            Button(onClick = { vm.action("Visit ready") {
                val entry = vm.api.startVisit(patient.id, mapOf("identity_verified" to true))
                onOpen(patient.id, entry)
            } }, enabled = !state.busy) { Text("Start visit") }
            Text("Check the patient's name and MRN. An existing open visit is resumed.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onOpen(patient.id, null) }, enabled = !state.busy) { Text("Care plan & records") }
            TextButton(onClick = { vm.action("Mobile access updated") {
                vm.api.patientAccess(patient.id, mapOf("portal_access_enabled" to (patient.portal_access_enabled != 1), "__v" to patient.__v))
                vm.searchPatients(search)
            } }, enabled = !state.busy) { Text(if (patient.portal_access_enabled == 1) "Disable mobile access" else "Enable mobile access") }
        }
                }
            }
        }
    }
    if (create) {
        var mobileAccess by remember { mutableStateOf(true) }
        var addressValid by remember { mutableStateOf(false) }
        val values = remember { mutableStateMapOf<String, String>() }; var gender by remember { mutableStateOf("Not specified") }
        InfoCard("Patient details", "Check the patient's identity and mobile before creating the record.") {
            listOf("name" to "Full name", "phone" to "Mobile number").forEach { (key,title) -> Field(title, values[key].orEmpty(), { values[key] = it }, !state.busy) }
            BirthDateField(values["dob"].orEmpty(), { values["dob"] = it }, !state.busy)
            Field("Address", values["address"].orEmpty(), { values["address"] = it }, !state.busy)
            IndianAddressFields(values["state"].orEmpty(), values["pin_code"].orEmpty(), values["city"].orEmpty(),
                { values["state"] = it }, { values["pin_code"] = it }, { values["city"] = it },
                enabled = !state.busy, onValidityChange = { addressValid = it })
            Field("Emergency contact mobile", values["emergency_contact"].orEmpty(), { values["emergency_contact"] = it }, !state.busy)
            ChoiceRow(listOf("Female", "Male", "Other", "Not specified"), gender, { gender = it }, !state.busy)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(mobileAccess, { mobileAccess = it }, enabled = !state.busy); Text("Enable patient mobile sign-in") }
            Text("The patient signs in with an OTP using the mobile number saved above.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.action("Patient registered") { vm.api.createPatient(values.toMap() + mapOf("gender" to gender, "portal_access_enabled" to mobileAccess)); values.clear(); create = false; vm.searchPatients(search) } }, enabled = !state.busy && addressValid && listOf("name", "phone", "dob", "address", "city", "state", "pin_code", "emergency_contact").all { !values[it].isNullOrBlank() } && validBirthDate(values["dob"].orEmpty())) { Text("Register patient") }
        }
    }
}
