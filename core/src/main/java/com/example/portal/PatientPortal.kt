package com.example.portal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.api.*
import com.example.domain.model.User
import com.example.di.AppContainer
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PatientPortal(container: AppContainer, user: User, onLogout: () -> Unit) {
    val vm: PortalViewModel = viewModel(key = "patient:${user.id}", factory = PortalViewModel.factory(container.portalApi, user, container.tokenManager))
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.today) { CareReminders.schedule(context, state.today) }
    var tab by remember { mutableStateOf("Home") }
    var more by remember { mutableStateOf(false) }
    val primary = listOf("Home" to Icons.Outlined.Home, "Care plan" to Icons.Outlined.Checklist,
        "Appointments" to Icons.Outlined.CalendarMonth, "Records" to Icons.Outlined.Folder, "More" to Icons.Outlined.MoreHoriz)
    BackHandler(tab != "Home") { tab = "Home" }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Chettinad Care", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
            actions = { TextButton(onClick = vm::refresh, enabled = !state.loading && !state.busy) { Text("Refresh") } }) },
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) { primary.forEach { (name, icon) ->
            NavigationBarItem(selected = if (name == "More") tab in listOf("Queue", "Trends", "Profile", "Notifications") else tab == name,
                onClick = { if (!state.busy) { if (name == "More") more = true else tab = name } },
                icon = { Icon(icon, contentDescription = name) }, label = { Text(if (name == "Appointments") "Visits" else name, maxLines = 1) })
        } } }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (tab) {
                "Home" -> {
                    PageHeading("Hello, ${user.name.substringBefore(' ')}", "Your care, connected.")
                    ErrorCards(state, setOf("appointments", "today"), vm::refresh)
                    val next = state.appointments.filter { it.status in setOf("PENDING_CONFIRMATION", "CONFIRMED", "CHECKED_IN") }.minByOrNull { it.scheduled_at }
                    if (next == null) EmptyCard("Your next appointment", "Book a visit when you need to see your care team.")
                    else InfoCard("Your next appointment", "${next.doctor_name ?: "Care team"} · ${displayDate(next.scheduled_at)}") { StatusPill(next.status); Text(next.reason.orEmpty()) }
                    Button(onClick = { tab = "Appointments" }, modifier = Modifier.fillMaxWidth()) { Text("Book an appointment") }
                    InfoCard("Today's care plan", "${state.today.count { it.status in setOf("COMPLETED", "RECORDED", "TAKEN") }} of ${state.today.size} tasks completed") {
                        state.today.take(3).forEach { Text("${it.due_time} · ${it.title} · ${label(it.status)}") }
                        TextButton(onClick = { tab = "Care plan" }) { Text("View today's tasks") }
                    }
                    InfoCard("Your health records", "Prescriptions, results and documents in one place.") {
                        TextButton(onClick = { tab = "Records" }) { Text("Open records") }
                        TextButton(onClick = { tab = "Trends" }) { Text("Log a measurement or activity") }
                    }
                }
                "Appointments" -> AppointmentPage(vm, state)
                "Care plan" -> CarePlanPage(vm, state, patient = true)
                "Queue" -> QueuePatientPage(state, vm::refresh)
                "Records" -> RecordsPage(vm, state, uploadAllowed = true)
                "Trends" -> TrendsPage(vm, state, canRecord = true)
                "Profile" -> ProfilePage(vm, state, onLogout)
                "Notifications" -> NotificationsPage(vm, state)
            }
        }
    }
    if (more) ModalBottomSheet(onDismissRequest = { more = false }) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("More", style = MaterialTheme.typography.titleLarge)
            listOf("Queue", "Trends", "Notifications", "Profile").forEach { name ->
                TextButton(onClick = { tab = name; more = false }, modifier = Modifier.fillMaxWidth()) { Text(name) }
            }
        }
    }
}

@Composable fun AppointmentPage(vm: PortalViewModel, state: PortalState, staff: Boolean = false) {
    var booking by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf<Appointment?>(null) }
    var checkIn by remember { mutableStateOf<Appointment?>(null) }
    PageHeading("Appointments", if (staff) "Requests, arrivals and upcoming visits." else "Plan your next visit with the care team.")
    ErrorCards(state, setOf("appointments", "directory", "slots"), vm::refresh)
    if (!staff || vm.user.role == com.example.domain.model.Role.ADMIN) OutlinedButton(onClick = { booking = !booking }, enabled = !state.busy) { Text(if (booking) "Close booking" else "Book an appointment") }
    if (booking) BookingForm(vm, state, staff)
    if (state.appointments.isEmpty() && "appointments" !in state.errors) EmptyCard("No appointments yet", "Your booked visits will appear here.")
    PagedRows(state.appointments.sortedByDescending { it.scheduled_at }) { appointment ->
        InfoCard(if (staff) appointment.patient_name ?: "Patient" else appointment.doctor_name ?: "Care team", displayDate(appointment.scheduled_at)) {
            StatusPill(appointment.status)
            Text(listOfNotNull(appointment.department_name, appointment.reason).joinToString(" · "))
            if (staff && vm.user.role != com.example.domain.model.Role.NURSE && appointment.status == "PENDING_CONFIRMATION") Button(onClick = { vm.appointment(appointment, "confirm") }, enabled = !state.busy) { Text("Confirm request") }
            if (staff && vm.user.role != com.example.domain.model.Role.NURSE && appointment.status == "CONFIRMED") Button(onClick = { checkIn = appointment }, enabled = !state.busy) { Text("Check in patient") }
            if ((!staff || vm.user.role == com.example.domain.model.Role.ADMIN) && appointment.status in setOf("CONFIRMED", "PENDING_CONFIRMATION")) {
                TextButton(onClick = { cancel = appointment }, enabled = !state.busy) { Text("Cancel appointment") }
                RescheduleForm(vm, state, appointment)
            }
        }
    }
    checkIn?.let { appointment -> ConfirmAction("Verify patient identity", "Confirm this patient’s name and hospital identity against the appointment before checking in: ${appointment.patient_name.orEmpty()} · ${appointment.mrn.orEmpty()}.", "Identity verified · Check in", { checkIn = null }, { vm.appointment(appointment, "check-in"); checkIn = null }) }
    cancel?.let { appointment -> ConfirmAction("Cancel this appointment?", "${appointment.doctor_name.orEmpty()} · ${displayDate(appointment.scheduled_at)}", "Cancel appointment", { cancel = null }) { vm.appointment(appointment, "cancel"); cancel = null } }
}

@Composable private fun BookingForm(vm: PortalViewModel, state: PortalState, staff: Boolean) {
    var doctor by remember { mutableStateOf("") }; var date by remember { mutableStateOf(todayIndia()) }
    var slot by remember(doctor, date) { mutableStateOf("") }; var reason by remember { mutableStateOf("") }
    val offeredSlots = state.availableSlots(doctor, date)
    var patient by remember { mutableStateOf("") }; var search by remember { mutableStateOf("") }
    LaunchedEffect(state.message) { if (state.message == "Appointment request sent") { reason = ""; slot = "" } }
    InfoCard("New appointment", "Select an available slot. Requests need hospital confirmation.") {
        if (staff) {
            Field("Search patient by name or MRN", search, { search = it }, !state.busy)
            OutlinedButton(onClick = { vm.searchPatients(search) }, enabled = search.isNotBlank() && !state.busy) { Text("Find patient") }
            state.patients.forEach { p -> FilterChip(selected = patient == p.id, onClick = { patient = p.id }, label = { Text("${p.name} · ${p.mrn}") }) }
        }
        state.directory.doctors.forEach { d -> FilterChip(selected = doctor == d.id, onClick = { doctor = d.id }, enabled = !state.busy, label = { Text("${d.name} · ${d.department.orEmpty()}") }) }
        Field("Visit date (YYYY-MM-DD)", date, { date = it }, !state.busy)
        Button(onClick = { vm.slots(doctor, date) }, enabled = doctor.isNotBlank() && validDate(date) && !state.busy) { Text("Find available times") }
        offeredSlots.forEach { offered -> FilterChip(selected = slot == offered.scheduled_at, onClick = { slot = offered.scheduled_at }, enabled = !state.busy, label = { Text("${displayDate(offered.scheduled_at)} · ${offered.room}") }) }
        if (offeredSlots.isEmpty()) Text("Find times to see the available slots.", style = MaterialTheme.typography.bodySmall)
        Field("Reason for visit", reason, { reason = it }, !state.busy, 2)
        Button(onClick = { vm.book(doctor, slot, reason, patient.takeIf { staff }) }, enabled = !state.busy && slot.isNotBlank() && reason.isNotBlank() && (!staff || patient.isNotBlank())) { Text("Request appointment") }
    }
}

@Composable private fun RescheduleForm(vm: PortalViewModel, state: PortalState, appointment: Appointment) {
    var open by remember { mutableStateOf(false) }; var date by remember { mutableStateOf(todayIndia()) }
    TextButton(onClick = { open = !open }, enabled = !state.busy) { Text("Change appointment time") }
    if (open) {
        Field("New date (YYYY-MM-DD)", date, { date = it }, !state.busy)
        OutlinedButton(onClick = { vm.slots(appointment.doctor_id.orEmpty(), date) }, enabled = validDate(date) && appointment.doctor_id != null && !state.busy) { Text("Find times") }
        state.availableSlots(appointment.doctor_id.orEmpty(), date).forEach { slot -> TextButton(onClick = { vm.appointment(appointment, "reschedule", slot.scheduled_at); open = false }, enabled = !state.busy) { Text(displayDate(slot.scheduled_at)) } }
    }
}

@Composable fun QueuePatientPage(state: PortalState, refresh: () -> Unit) {
    PageHeading("Your queue", "Latest status from the hospital. Refresh for an update.")
    ErrorCards(state, setOf("queue"), refresh)
    if (state.queue.isEmpty() && "queue" !in state.errors) EmptyCard("You are not in the queue", "Your token appears after reception checks you in.")
    state.queue.forEach { entry -> InfoCard("Token ${entry.token ?: "—"}", entry.doctor_name) {
        StatusPill(entry.status)
        entry.patients_ahead?.let { Text("$it patients ahead") }
        entry.estimated_wait?.let { Text("Estimated wait: ${it.toInt()} minutes") }
        Text("Wait times are estimates and may change.", style = MaterialTheme.typography.bodySmall)
    } }
}
