package com.example.portal

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.api.PatientProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** A focused booking flow, with patient identity and the offered slot reviewed before sending. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppointmentBooking(vm: PortalViewModel, state: PortalState, staff: Boolean, onClose: () -> Unit) {
    val firstStep = if (staff) 0 else 1
    var step by remember { mutableIntStateOf(firstStep) }
    var patient by remember { mutableStateOf<PatientProfile?>(null) }
    var query by remember { mutableStateOf("") }
    var doctorId by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayIndia()) }
    var slot by remember(doctorId, date) { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val doctor = state.directory.doctors.find { it.id == doctorId }
    val offered = state.availableSlots(doctorId, date)
    val chosenSlot = offered.find { it.scheduled_at == slot }
    val findingTimes = state.slotsLoading && state.slotsDoctorId == doctorId && state.slotsDate == date
    val visitValid = doctor != null && validDate(date) && date >= todayIndia() && chosenSlot != null && !findingTimes
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()
    val back: () -> Unit = { if (!state.busy) { keyboard?.hide(); if (step == firstStep) onClose() else step-- } }
    LaunchedEffect(step) { scroll.scrollTo(0) }
    LaunchedEffect(state.message, submitted) {
        if (submitted && state.message == "Appointment request sent") onClose()
    }
    Dialog(onDismissRequest = back, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !state.busy,
        dismissOnClickOutside = false, decorFitsSystemWindows = false
    )) {
        Scaffold(modifier = Modifier.fillMaxSize().imePadding().testTag("booking-screen"),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopAppBar(title = { Text("New appointment", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = back, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }) },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            keyboard?.hide()
                            if (step < 2) step++
                            else {
                                submitted = true
                                vm.book(doctorId, slot, reason.trim(), if (staff) patient?.id else null)
                            }
                        }, enabled = !state.busy && when (step) {
                            0 -> patient != null
                            1 -> visitValid
                            else -> visitValid && reason.isNotBlank() && (!staff || patient != null)
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(if (state.busy) "Sending request…" else if (step == 2) "Request appointment" else "Continue")
                        }
                        if (step == 2) Text("The hospital will confirm your request.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Step ${step - firstStep + 1} of ${3 - firstStep}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(progress = { (step - firstStep + 1).toFloat() / (3 - firstStep) }, modifier = Modifier.fillMaxWidth())
                    Text(when (step) { 0 -> "Select patient"; 1 -> "Visit details"; else -> "Review appointment" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                ErrorCards(state.copy(message = null), setOf("patients", "directory", "slots"), vm::refresh)
                when (step) {
                    0 -> {
                        val search: () -> Unit = { if (query.trim().length in 2..200 && !state.busy) { keyboard?.hide(); vm.searchPatients(query.trim()) } }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(value = query, onValueChange = { query = it; patient = null; reason = "" },
                                label = { Text("Patient name or MRN") }, singleLine = true, enabled = !state.busy,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }), modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = search, enabled = query.trim().length in 2..200 && !state.busy && !state.patientSearchLoading) { Text("Find patient") }
                        }
                        Text("Check the patient's name and hospital number before continuing.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.patientSearchQuery == query.trim()) {
                            if (state.patientSearchLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else if (state.patients.isEmpty() && "patients" !in state.errors) Text("No matching patients. Try another name or hospital number.")
                            else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PagedRows(state.patients) { candidate ->
                                    val selected = patient?.id == candidate.id
                                    Surface(shape = MaterialTheme.shapes.medium,
                                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.selectable(selected, enabled = !state.busy, role = Role.RadioButton,
                                            onClick = { if (patient?.id != candidate.id) reason = ""; patient = candidate }).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            RadioButton(selected = selected, onClick = null)
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(candidate.name, fontWeight = FontWeight.SemiBold)
                                                Text(candidate.mrn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (staff) patient?.let { BookingSummary("Patient", it.name, it.mrn) }
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            BookingDropdown("Doctor", doctor?.name.orEmpty(), !state.busy, doctor?.department) { close ->
                                state.directory.doctors.forEach { option ->
                                    DropdownMenuItem(text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(option.name)
                                            option.department?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        }
                                    }, onClick = { doctorId = option.id; close() })
                                }
                            }
                            if (state.directory.doctors.isEmpty() && "directory" !in state.errors) Text("No doctors are available. Refresh or contact reception.")
                            VisitDateField(date, { date = it }, !state.busy)
                            OutlinedButton(onClick = { slot = ""; vm.slots(doctorId, date) },
                                enabled = doctor != null && validDate(date) && date >= todayIndia() && !state.busy && !findingTimes,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (findingTimes) "Finding times…" else "Find available times") }
                            if (findingTimes) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else if (offered.isNotEmpty()) {
                                BookingDropdown("Available time", chosenSlot?.let { bookingTime(it.scheduled_at) }.orEmpty(), !state.busy, "Times are in India Standard Time.") { close ->
                                    offered.forEach { option ->
                                        DropdownMenuItem(text = {
                                            Column {
                                                Text(bookingTime(option.scheduled_at))
                                                if (option.room.isNotBlank()) Text(option.room, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }, onClick = { slot = option.scheduled_at; close() })
                                    }
                                }
                            } else if (state.slotsDoctorId == doctorId && state.slotsDate == date && "slots" !in state.errors) {
                                Text("No times available for this date. Choose another date or doctor.")
                            } else Text("Choose a doctor and date, then find available times.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (staff) patient?.let { BookingSummary("Patient", it.name, it.mrn) }
                            BookingSummary("Doctor", doctor?.name ?: "Doctor no longer available", doctor?.department)
                            BookingSummary("Visit", bookingDate(date), chosenSlot?.let { "${bookingTime(it.scheduled_at)} IST${if (it.room.isBlank()) "" else " · ${it.room}"}" })
                        }
                        if (!visitValid) Text("This time is no longer available. Go back and choose an available visit time.", color = MaterialTheme.colorScheme.error)
                        Field("Reason for visit", reason, { reason = it }, !state.busy, 3)
                    }
                }
            }
        }
    }
}

@Composable private fun BookingSummary(label: String, title: String, detail: String?) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BookingDropdown(label: String, value: String, enabled: Boolean, detail: String? = null, items: @Composable ColumnScope.(() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, singleLine = true, enabled = enabled,
            label = { Text(label) }, placeholder = { Text("Select ${label.lowercase()}") },
            supportingText = detail?.let { { Text(it) } }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            items { expanded = false }
        }
    }
}

@Composable private fun VisitDateField(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val parts = value.split('-').map { it.toInt() }
        val dialog = DatePickerDialog(context, { _, year, month, day -> onChange(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)) }, parts[0], parts[1] - 1, parts[2])
        dialog.datePicker.minDate = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        dialog.show()
    }, enabled = enabled, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Visit date", style = MaterialTheme.typography.labelMedium)
            Text(bookingDate(value), style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Outlined.CalendarMonth, "Choose visit date")
    }
}

private fun bookingDate(value: String): String = runCatching {
    SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(requireNotNull(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)))
}.getOrDefault(value)
private fun bookingTime(value: String): String = displayDate(value).substringAfter(" · ")
