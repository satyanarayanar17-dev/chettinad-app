package com.example.portal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Role
import com.example.domain.model.User

@Composable internal fun StaffNavigation(user: User, destinations: List<String>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    val width = (LocalConfiguration.current.screenWidthDp.dp * .88f).coerceAtMost(320.dp)
    ModalDrawerSheet(modifier = Modifier.width(width).testTag("staff-menu"), drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Chettinad Care", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(user.name, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider()
            val groups = if (user.role == Role.ADMIN) listOf(
                "Reception" to listOf("Overview", "Appointments", "Patients"),
                "Administration" to listOf("Doctor schedules", "Staff", "Audit trail")
            ) else listOf("Clinical work" to listOf("Live queue", "Patient records", "Appointments", "Lab workflow"))
            (groups + ("Account" to listOf("Notifications", "Profile"))).forEach { (section, options) ->
                val visible = options.filter { it in destinations }
                if (visible.isNotEmpty()) {
                    Text(section, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp))
                    visible.forEach { destination ->
                        NavigationDrawerItem(selected = selected == destination,
                            onClick = { if (enabled) onSelect(destination) },
                            label = { Text(destination, style = MaterialTheme.typography.bodyLarge) },
                            icon = { Icon(when (destination) {
                                "Overview" -> Icons.Outlined.Dashboard
                                "Appointments" -> Icons.Outlined.CalendarMonth
                                "Patients" -> Icons.Outlined.Groups
                                "Doctor schedules" -> Icons.Outlined.EventAvailable
                                "Staff" -> Icons.Outlined.Badge
                                "Audit trail" -> Icons.Outlined.History
                                "Notifications" -> Icons.Outlined.NotificationsNone
                                "Profile" -> Icons.Outlined.PersonOutline
                                "Patient records" -> Icons.Outlined.FolderOpen
                                "Lab workflow" -> Icons.Outlined.Science
                                else -> Icons.Outlined.FormatListNumbered
                            }, contentDescription = null) }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}
