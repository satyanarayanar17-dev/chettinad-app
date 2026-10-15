package com.example.portal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.data.api.*

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
    PageHeading("Doctor schedules", "Weekly sessions used to offer appointment slots.")
    ErrorCards(state, setOf("directory"), vm::refresh)
    state.directory.doctors.forEach { d -> FilterChip(selected = doctor == d.id, onClick = { doctor = d.id }, label = { Text(d.name) }) }
    val selectedDoctor = state.directory.doctors.find { it.id == doctor }
    selectedDoctor?.schedules?.forEach { schedule -> InfoCard("${listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").getOrElse(schedule.weekday) { "Day" }} · ${schedule.start_time}–${schedule.end_time}", "${schedule.room} · ${schedule.slot_minutes} minute slots") }
    InfoCard("Configure weekly session", "Changes apply to newly offered slots. Existing appointments keep their time.") {
        ChoiceRow((0..6).map { it.toString() }, weekday, { weekday = it }, !state.busy)
        Text("0 Sunday · 1 Monday · 2 Tuesday · 3 Wednesday · 4 Thursday · 5 Friday · 6 Saturday", style = MaterialTheme.typography.bodySmall)
        Field("Start (HH:MM)", start, { start = it }, !state.busy); Field("End (HH:MM)", end, { end = it }, !state.busy)
        Field("Slot duration (minutes)", minutes, { minutes = it }, !state.busy); Field("Room", room, { room = it }, !state.busy)
        Button(onClick = { vm.action("Schedule updated") {
            val doc = requireNotNull(selectedDoctor); val department = state.directory.departments.first { it.name == doc.department }
            val old = doc.schedules.find { it.weekday == weekday.toInt() }
            val data = mutableMapOf<String, Any>("doctor_id" to doc.id, "department_id" to department.id, "weekday" to weekday.toInt(), "start_time" to start, "end_time" to end, "slot_minutes" to minutes.toInt(), "room" to room)
            old?.let { data["__v"] = it.__v }; vm.api.schedule(data); vm.reloadDirectory(vm.api.directory())
        } }, enabled = !state.busy && selectedDoctor != null && room.isNotBlank() && minutes.toIntOrNull() in 5..120 && listOf(start, end).all { it.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) } && start < end) { Text("Save schedule") }
    }
}

@Composable fun PatientRegistration(vm: PortalViewModel, state: PortalState) {
    var search by remember { mutableStateOf("") }; var create by remember { mutableStateOf(false) }
    PageHeading("Patient directory", "Search existing records before registering a patient.")
    ErrorCards(state, setOf("patients"), vm::refresh)
    Field("Search name, MRN or mobile", search, { search = it }, !state.busy)
    OutlinedButton(onClick = { vm.searchPatients(search) }, enabled = !state.busy && search.isNotBlank()) { Text("Find patient") }
    PagedRows(state.patients) { patient -> InfoCard(patient.name, "${patient.mrn} · ${patient.phone}") }
    OutlinedButton(onClick = { create = !create }, enabled = !state.busy) { Text(if (create) "Close registration" else "Register new patient") }
    if (create) {
        val values = remember { mutableStateMapOf<String, String>() }; var gender by remember { mutableStateOf("Not specified") }
        InfoCard("Patient details", "Check the patient's identity and mobile before creating the record.") {
            listOf("name" to "Full name", "phone" to "Mobile number", "dob" to "Date of birth (YYYY-MM-DD)", "address" to "Address", "city" to "City", "state" to "State", "pin_code" to "PIN code", "emergency_contact" to "Emergency contact mobile").forEach { (key,title) -> Field(title, values[key].orEmpty(), { values[key] = it }, !state.busy) }
            ChoiceRow(listOf("Female", "Male", "Other", "Not specified"), gender, { gender = it }, !state.busy)
            Button(onClick = { vm.action("Patient registered") { vm.api.createPatient(values.toMap() + ("gender" to gender)); values.clear(); create = false } }, enabled = !state.busy && listOf("name", "phone", "dob", "address", "city", "state", "pin_code", "emergency_contact").all { !values[it].isNullOrBlank() } && validDate(values["dob"].orEmpty())) { Text("Register patient") }
        }
    }
}
