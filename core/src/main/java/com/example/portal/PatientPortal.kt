package com.example.portal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.ui.components.AppLogo
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
    ForegroundPortalSync(vm)
    val context = LocalContext.current
    LaunchedEffect(state.today, state.care, state.errors) { CareReminders.schedule(context, reminderTasks(state)) }
    var tab by remember { mutableStateOf("Home") }
    var more by remember { mutableStateOf(false) }
    val primary = listOf("Home" to Icons.Outlined.Home, "Care plan" to Icons.Outlined.FavoriteBorder,
        "Records" to Icons.Outlined.FolderOpen, "Profile" to Icons.Outlined.PersonOutline)
    BackHandler(tab != "Home") { tab = "Home" }
    Scaffold(
        topBar = { TopAppBar(title = { AppLogo(Modifier.width(136.dp).height(44.dp)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            actions = { TextButton(onClick = vm::refresh, enabled = !state.loading && !state.busy) { Text("Refresh") } }) },
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) { primary.forEach { (name, icon) ->
            NavigationBarItem(selected = if (name == "Profile") tab in listOf("Queue", "Trends", "Profile", "Notifications", "Appointments") else tab == name,
                onClick = { if (!state.busy) { tab = name } },
                icon = { Icon(icon, contentDescription = name) }, label = { Text(if (name == "Home") "Today" else if (name == "Profile") "You" else name, maxLines = 1) })
        } } }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            when (tab) {
                "Home" -> {
                    PageHeading("Hello, ${user.name.substringBefore(' ')}", "Your care, connected.")
                    ErrorCards(state, setOf("appointments", "today"), vm::refresh)
                    val next = state.appointments.filter { it.status in setOf("PENDING_CONFIRMATION", "CONFIRMED", "CHECKED_IN") }.minByOrNull { it.scheduled_at }
                    if (next == null) EmptyCard("Your next appointment", "Visits scheduled by your care team appear here.")
                    else InfoCard("Your next appointment", "${next.doctor_name ?: "Care team"} · ${displayDate(next.scheduled_at)}") { StatusPill(next.status); Text(next.reason.orEmpty()) }
                    CareCompletionCard(state.today)
                    InfoCard("Today's care plan", "${careCompletion(state.today.map { it.status }).completed} of ${state.today.size} tasks completed") {
                        state.today.take(3).forEach { Text("${it.due_time} · ${it.title} · ${label(it.status)}") }
                        TextButton(onClick = { tab = "Care plan" }) { Text("View today's tasks") }
                    }
                    InfoCard("Your health records", "Prescriptions, results and documents in one place.") {
                        TextButton(onClick = { tab = "Records" }) { Text("Open records") }
                        TextButton(onClick = { tab = "Care plan" }) { Text("Log a measurement or activity") }
                    }
                }
                "Appointments" -> AppointmentPage(vm, state)
                "Care plan" -> CarePlanPage(vm, state, patient = true)
                "Queue" -> QueuePatientPage(state, vm::refresh)
                "Records" -> RecordsPage(vm, state, uploadAllowed = true)
                "Trends" -> { TextButton(onClick = { tab = "Profile" }) { Text("Back to your account") }; TrendsPage(vm, state) }
                "Profile" -> {
                    PageHeading("Your account", user.name)
                    ChoiceRow(listOf("Profile", "Appointments", "Trends", "Notifications", "Queue"), "Profile", { tab = it }, !state.busy)
                    ProfilePage(vm, state, onLogout)
                }
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
    PageHeading("Visits", if (staff) "Check in arrivals and view scheduled visits." else "Your visits with the care team.")
    ErrorCards(state, setOf("appointments"), vm::refresh)
    if (state.appointments.isEmpty() && "appointments" !in state.errors) EmptyCard("No visits yet", "Visits scheduled by your care team appear here.")
    PagedRows(state.appointments.sortedByDescending { it.scheduled_at }) { appointment ->
        InfoCard(if (staff) appointment.patient_name ?: "Patient" else appointment.doctor_name ?: "Care team", displayDate(appointment.scheduled_at)) {
            StatusPill(appointment.status)
            Text(listOfNotNull(appointment.department_name, appointment.reason).joinToString(" · "))
            if (staff) Text("MRN ${appointment.mrn.orEmpty()}")
            val today = runCatching { java.time.Instant.parse(appointment.scheduled_at).atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate().toString() == todayIndia() }.getOrDefault(false)
            if (staff && vm.user.role in setOf(com.example.domain.model.Role.ADMIN, com.example.domain.model.Role.DOCTOR) && appointment.status in setOf("PENDING_CONFIRMATION", "CONFIRMED") && today) {
                Text("Verify the patient name and MRN, then check in.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.appointment(appointment, "check-in") }, enabled = !state.busy) { Text("Check in patient") }
            }
        }
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
