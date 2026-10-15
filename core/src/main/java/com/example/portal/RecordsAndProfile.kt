package com.example.portal

import android.content.Intent
import android.net.Uri
import android.print.PrintManager
import android.content.Context
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.api.*
import com.example.domain.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

@Composable fun RecordsPage(vm: PortalViewModel, state: PortalState, uploadAllowed: Boolean) {
    val context = LocalContext.current
    var section by remember { mutableStateOf("Prescriptions") }
    var uploading by remember { mutableStateOf(false) }
    PageHeading("Medical records", "Hospital records and documents shared with your care team.")
    ErrorCards(state, setOf("record", "documents"), vm::refresh)
    ChoiceRow(listOf("Prescriptions", "Notes", "Lab results", "Documents"), section, { section = it })
    val bundle = state.record
    when (section) {
        "Prescriptions" -> {
            val rows = bundle?.prescriptions.orEmpty().filter { it.status == "AUTHORIZED" }
            if (rows.isEmpty() && "record" !in state.errors) EmptyCard("No prescriptions available", "Signed prescriptions appear after your consultation.")
            PagedRows(rows) { rx -> InfoCard("Prescription", "${rx.doctor_name ?: "Care team"} · ${displayDate(rx.created_at)}") {
                rx.rx_content?.diagnoses?.forEach { Text(it.name) }
                rx.rx_content?.medications?.forEach { m -> Text("${m.name ?: m.drug_id} ${m.strength.orEmpty()}\n${m.dose} · ${m.frequency} · ${m.duration}\n${m.instructions.orEmpty()}") }
                rx.rx_content?.advice?.let { Text("Advice: $it") }
                OutlinedButton(onClick = { bundle?.let { printPrescription(context, it.patient, rx) } }) { Text("Print or save PDF") }
            } }
        }
        "Notes" -> {
            if (bundle?.notes.isNullOrEmpty() && "record" !in state.errors) EmptyCard("No consultation notes", "Completed consultation notes appear here.")
            PagedRows(bundle?.notes.orEmpty()) { record -> InfoCard("${record.author_name ?: "Consultation"} · ${label(record.status)}", displayDate(record.created_at)) {
                record.draft_content?.let { note ->
                    listOf("Complaint" to note.complaint, "History" to note.history, "Examination" to note.examination,
                        "Assessment" to note.assessment, "Treatment" to note.treatment, "Advice" to note.advice).filter { !it.second.isNullOrBlank() }.forEach { Text("${it.first}: ${it.second}") }
                    note.diagnoses.forEach { Text("Diagnosis: ${it.name}") }
                }
            } }
        }
        "Lab results" -> LabList(vm, state, bundle?.labs.orEmpty(), patient = uploadAllowed)
        "Documents" -> {
            if (uploadAllowed) OutlinedButton(onClick = { uploading = !uploading }, enabled = !state.busy) { Text(if (uploading) "Close upload" else "Upload a medical document") }
            if (uploading) DocumentUpload(vm, state)
            if (state.documents.isEmpty() && "documents" !in state.errors) EmptyCard("No uploaded documents", "PDF, JPEG and PNG medical records will appear here.")
            PagedRows(state.documents) { document -> InfoCard(document.title, "${label(document.document_type)} · ${document.clinical_date}") {
                document.patient_note?.let { Text(it) }
                Text("${document.original_filename} · ${document.size_bytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.action("Document opened") {
                    val bytes = withContext(Dispatchers.IO) { vm.api.documentContent(requireNotNull(state.patientId), document.id).use { it.bytes() } }
                    openPrivateDocument(context, document, bytes)
                } }, enabled = !state.busy) { Text("Open document") }
                if (uploadAllowed) {
                    var links by remember(document.id) { mutableStateOf(false) }
                    TextButton(onClick = { links = !links }, enabled = !state.busy) { Text(if (document.appointment_id == null) "Attach to appointment" else "Change appointment attachment") }
                    if (links) {
                        state.appointments.filter { it.status != "CANCELLED" }.forEach { appointment ->
                            TextButton(onClick = { vm.action("Document attached") { vm.api.linkDocument(requireNotNull(state.patientId), document.id, mapOf("appointment_id" to appointment.id)); val docs = vm.api.documents(requireNotNull(state.patientId)); vm.reloadDocuments(docs) }; links = false }, enabled = !state.busy) { Text("${displayDate(appointment.scheduled_at)} · ${appointment.doctor_name.orEmpty()}") }
                        }
                        if (document.appointment_id != null) TextButton(onClick = { vm.action("Attachment removed") { vm.api.linkDocument(requireNotNull(state.patientId), document.id, mapOf("appointment_id" to null)); vm.reloadDocuments(vm.api.documents(requireNotNull(state.patientId))) }; links = false }) { Text("Remove attachment") }
                    }
                }
            } }
        }
    }
}

@Composable private fun DocumentUpload(vm: PortalViewModel, state: PortalState) {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }; var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("LAB_REPORT") }; var date by remember { mutableStateOf(todayIndia()) }
    var note by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri = it }
    InfoCard("Upload medical document", "PDF, JPEG or PNG · maximum 10 MB") {
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, enabled = !state.busy) { Text(if (uri == null) "Choose a file" else "File selected · choose another") }
        Field("Document title", title, { title = it }, !state.busy)
        ChoiceRow(listOf("LAB_REPORT", "SCAN", "PRESCRIPTION", "DISCHARGE_SUMMARY", "REFERRAL", "OTHER_MEDICAL_RECORD"), type, { type = it }, !state.busy)
        Field("Clinical date (YYYY-MM-DD)", date, { date = it }, !state.busy)
        Field("Optional note", note, { note = it }, !state.busy, 2)
        Button(onClick = { val selected = uri ?: return@Button; vm.action("Document uploaded") {
            val file = withContext(Dispatchers.IO) {
                val mime = context.contentResolver.getType(selected) ?: throw IllegalArgumentException("Unsupported file")
                require(mime in listOf("application/pdf", "image/jpeg", "image/png"))
                val bytes = context.contentResolver.openInputStream(selected)?.use { it.readBytesLimited(10 * 1024 * 1024) } ?: throw IllegalStateException("Cannot read file")
                val ext = when (mime) { "application/pdf" -> "pdf"; "image/png" -> "png"; else -> "jpg" }
                MultipartBody.Part.createFormData("file", "medical-record.$ext", bytes.toRequestBody(mime.toMediaType()))
            }
            val fields = mutableMapOf("title" to title, "document_type" to type, "clinical_date" to date)
            if (note.isNotBlank()) fields["patient_note"] = note
            vm.api.uploadDocument(requireNotNull(state.patientId), file, fields.mapValues { it.value.toRequestBody("text/plain".toMediaType()) })
            vm.reloadDocuments(vm.api.documents(requireNotNull(state.patientId))); uri = null; title = ""; note = ""
        } }, enabled = !state.busy && uri != null && title.isNotBlank() && validDate(date) && date <= todayIndia()) { Text("Upload securely") }
    }
}
private fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break; require(out.size() + count <= max) { "File exceeds 10 MB" }; out.write(buffer, 0, count) }
    return out.toByteArray()
}
private suspend fun openPrivateDocument(context: Context, document: PatientDocument, bytes: ByteArray) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "medical-documents").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3600000 }?.forEach { it.delete() }
        val ext = when (document.mime_type) { "application/pdf" -> ".pdf"; "image/png" -> ".png"; else -> ".jpg" }
        File(dir, "document-${java.util.UUID.randomUUID()}$ext").apply { writeBytes(bytes) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mime_type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Open medical document"))
}
private fun printPrescription(context: Context, patient: PatientProfile, rx: PrescriptionRecord) {
    fun esc(v: String?) = android.text.TextUtils.htmlEncode(v.orEmpty())
    val signedPatient = rx.rx_content?.patient_snapshot
    val rows = rx.rx_content?.medications.orEmpty().joinToString("") { "<tr><td>${esc(it.name)} ${esc(it.strength)}</td><td>${esc(it.dose)}</td><td>${esc(it.frequency)}</td><td>${esc(it.duration)}</td><td>${esc(it.instructions)}</td></tr>" }
    val html = """<html><head><meta name="viewport" content="width=device-width"/><style>body{font-family:sans-serif;color:#191c1d;padding:24px}h1{color:#000666}table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:8px;border-bottom:1px solid #ddd}</style></head><body><h1>Chettinad Care</h1><h2>Prescription</h2><p>${esc(signedPatient?.name ?: patient.name)} · ${esc(signedPatient?.mrn ?: patient.mrn)}<br/>${esc(rx.doctor_name)} · ${esc(displayDate(rx.created_at))}</p><table><tr><th>Medicine</th><th>Dose</th><th>Frequency</th><th>Duration</th><th>Instructions</th></tr>$rows</table><p>${esc(rx.rx_content?.advice)}</p><p>Prescription reference: ${esc(rx.id)}</p></body></html>"""
    val web = WebView(context)
    web.settings.javaScriptEnabled = false
    web.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("Chettinad prescription", view.createPrintDocumentAdapter("Prescription"), null)
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

@Composable fun NotificationsPage(vm: PortalViewModel, state: PortalState) {
    PageHeading("Notifications", "Updates from the hospital.")
    ErrorCards(state, setOf("notifications"), vm::refresh)
    TextButton(onClick = { vm.action("Notifications marked read") { vm.api.readNotifications(); vm.reloadNotices(vm.api.notifications()) } }, enabled = !state.busy) { Text("Mark all read") }
    if (state.notices.isEmpty() && "notifications" !in state.errors) EmptyCard("All caught up", "New hospital updates will appear here.")
    PagedRows(state.notices) { notice -> InfoCard(label(notice.code), displayDate(notice.created_at)) { if (notice.read_at == null) Text("Unread", color = MaterialTheme.colorScheme.primary) } }
}

@Composable fun ProfilePage(vm: PortalViewModel, state: PortalState, onLogout: () -> Unit) {
    var logout by remember { mutableStateOf(false) }; var current by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    PageHeading("My profile", "Manage your access to Chettinad Care.")
    ErrorCards(state, setOf("record", "sessions"), vm::refresh)
    InfoCard(vm.user.name, label(vm.user.role.name)) {
        vm.user.department?.let { Text(it) }
        state.record?.patient?.let { p ->
            Text("Hospital number: ${p.mrn}"); Text("Mobile: ${p.phone}"); Text("Date of birth: ${p.dob}")
            Text(listOfNotNull(p.address, p.city, p.state, p.pin_code).joinToString(", "))
            Text("Emergency contact: ${p.emergency_contact ?: "—"}")
        }
    }
    if (vm.user.role == Role.PATIENT) state.record?.patient?.let { PatientProfileEditor(vm, state, it) }
    if (vm.user.role != Role.PATIENT) InfoCard("Change password") {
        Text("Use 12–72 characters with uppercase, lowercase, a number and a symbol.")
        OutlinedTextField(current, { current = it }, label = { Text("Current password") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("New password") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.action("Password changed. Sign in again.") { vm.api.changePassword(mapOf("currentPassword" to current, "newPassword" to password)); current = ""; password = ""; onLogout() } }, enabled = current.isNotBlank() && validStaffPassword(password) && !state.busy) { Text("Update password") }
    }
    InfoCard("Signed-in devices", "Revoke access for devices you no longer use.") {
        state.sessions.forEach { session ->
            Text(session.device_name.ifBlank { "Device" }); Text("Expires ${displayDate(session.expires_at)}", style = MaterialTheme.typography.bodySmall)
            if (session.current) Text("This device", color = MaterialTheme.colorScheme.secondary)
            else TextButton(onClick = { vm.action("Device signed out") { vm.api.revokeSession(session.id); vm.reloadSessions(vm.api.sessions()) } }, enabled = !state.busy) { Text("Revoke session") }
        }
    }
    if (vm.user.role == Role.PATIENT) ReminderPreferences(state)
    Button(onClick = { logout = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    if (logout) ConfirmAction("Sign out?", "You will need to sign in again to access your records.", "Sign out", { logout = false }, { logout = false; onLogout() })
}

@Composable private fun PatientProfileEditor(vm: PortalViewModel, state: PortalState, profile: PatientProfile) {
    var open by remember(profile.id) { mutableStateOf(false) }
    OutlinedButton(onClick = { open = !open }, enabled = !state.busy) { Text(if (open) "Close profile editor" else "Edit contact details") }
    if (!open) return
    val fields = remember(profile.id, profile.__v) { mutableStateMapOf(
        "email" to profile.email.orEmpty(), "address" to profile.address.orEmpty(),
        "city" to profile.city.orEmpty(), "state" to profile.state.orEmpty(),
        "pin_code" to profile.pin_code.orEmpty(), "emergency_contact" to profile.emergency_contact.orEmpty()) }
    InfoCard("Contact details", "Your sign-in mobile and hospital identity require reception assistance to change.") {
        listOf("email" to "Email (optional)", "address" to "Address", "city" to "City", "state" to "State", "pin_code" to "PIN code", "emergency_contact" to "Emergency contact mobile").forEach { (key, title) ->
            Field(title, fields[key].orEmpty(), { fields[key] = it }, !state.busy)
        }
        val email = fields["email"].orEmpty().trim()
        val phone = fields["emergency_contact"].orEmpty().replace(Regex("[\\s()-]"), "")
        val valid = listOf("address", "city", "state").all { !fields[it].isNullOrBlank() } &&
            fields["pin_code"].orEmpty().matches(Regex("[1-9][0-9]{5}")) &&
            phone.matches(Regex("(?:\\+91|91|0)?[6-9][0-9]{9}")) &&
            (email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
        Button(onClick = { vm.action("Contact details updated") {
            vm.api.updateProfile(profile.id, mapOf("__v" to profile.__v, "data" to fields.mapValues { it.value.trim() }))
            vm.reloadPatient(); open = false
        } }, enabled = valid && !state.busy) { Text("Save contact details") }
    }
}
