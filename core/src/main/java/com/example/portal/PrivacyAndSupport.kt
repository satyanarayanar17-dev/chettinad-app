package com.example.portal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.core.BuildConfig

/** Available before sign-in and from both account pages. Never sends a support message automatically. */
@Composable fun PrivacyAndSupport() {
    var page by remember { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { page = "Privacy" }) { Text("Privacy") }
        TextButton(onClick = { page = "Help and access" }) { Text("Help and access") }
    }
    page?.let { title ->
        AlertDialog(
            onDismissRequest = { page = null },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(BuildConfig.PUBLISHER_NAME)
                    androidx.compose.foundation.text.selection.SelectionContainer { Text("Support: ${BuildConfig.SUPPORT_EMAIL}") }
                    if (title == "Privacy") {
                        Text("Your hospital profile and care")
                        Text("Your care team creates your patient profile and enables access using your recorded mobile number. The service processes contact details, hospital identity, clinical records, uploaded medical documents, measurements, activities, care-task responses and sign-in sessions to provide your care workspace.")
                        Text("Who can see your information")
                        Text("Your signed-in account and authorized hospital staff use the shared hospital service. Medical documents you upload are shared with your authorized care team. If you choose to open, print or share a document, the app you select receives that document.")
                        Text("On this phone")
                        Text("Sign-in information and recoverable clinical drafts use encrypted app-private storage. Drafts stay linked to the staff account and encounter that created them. Opened documents use temporary app-private files, which are removed when you sign out or change account. Device backup is disabled.")
                        Text("Notifications and permissions")
                        Text("Care reminders are optional. Their lock-screen text does not include your diagnosis, medicine or clinical values. The app uses the system document picker for files you choose. It does not need contacts, location, microphone, SMS inbox or unrestricted photo-library access.")
                        Text("Your choices")
                        Text("You can edit permitted contact details, turn off reminders, sign out and review signed-in devices. For mobile-access closure, privacy questions or requests about hospital records, contact your hospital care team. Closing mobile access does not erase the hospital’s clinical record.")
                        if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) TextButton(onClick = { uri.openUri(BuildConfig.PRIVACY_POLICY_URL) }) { Text("Hospital privacy notice") }
                    } else {
                        Text("For help signing in or closing mobile access, contact the hospital registration desk or your assigned doctor. Do not send an OTP or password to anyone offering support.")
                        Text("Your assigned doctor controls the prescribed care plan and medication reminder settings. Reminders are optional and timing can vary with your phone settings. Open the app each day to refresh them.")
                        Text("This app is not a medical device and does not diagnose, treat, cure or prevent any medical condition. Consult your treating clinician for medical advice. For urgent symptoms, use the hospital’s usual emergency services.")
                        if (BuildConfig.SUPPORT_URL.isNotBlank()) TextButton(onClick = { uri.openUri(BuildConfig.SUPPORT_URL) }) { Text("Contact support") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { page = null }) { Text("Close") } }
        )
    }
}
