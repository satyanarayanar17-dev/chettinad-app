package com.example.address

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.portal.todayIndia
import com.example.portal.validDate
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun IndianAddressFields(
    state: String, pin: String, city: String,
    onStateChange: (String) -> Unit, onPinChange: (String) -> Unit, onCityChange: (String) -> Unit,
    enabled: Boolean = true, onValidityChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var unlistedConfirmed by remember(pin, state) { mutableStateOf(false) }
    val areas = remember(pin) { IndiaPostalDirectory.areas(pin) }
    val match = IndiaPostalDirectory.match(state, pin)
    val valid = match == IndiaPostalDirectory.Match.MATCH ||
        (match == IndiaPostalDirectory.Match.UNKNOWN_PIN && unlistedConfirmed)
    LaunchedEffect(valid) { onValidityChange(valid) }
    LaunchedEffect(pin, state) {
        val canonical = IndiaPostalDirectory.canonicalState(state)
        if (canonical != null && canonical != state) onStateChange(canonical)
        else if (state.isBlank()) areas.map { it.state }.distinct().singleOrNull()?.let(onStateChange)
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value = state, onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text("State / Union Territory") }, placeholder = { Text("Select a state") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            IndiaPostalDirectory.states.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onStateChange(option); expanded = false })
            }
        }
    }
    OutlinedTextField(value = pin, onValueChange = { onPinChange(it.filter { c -> c in '0'..'9' }.take(6)) },
        label = { Text("PIN code") }, placeholder = { Text("6 digits") }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        isError = match in setOf(IndiaPostalDirectory.Match.MISMATCH, IndiaPostalDirectory.Match.INVALID_FORMAT))
    when (match) {
        IndiaPostalDirectory.Match.MISMATCH -> Text("This PIN code does not match the selected state. Check the PIN code or change the state.", color = MaterialTheme.colorScheme.error)
        IndiaPostalDirectory.Match.INVALID_FORMAT -> Text("Enter a six-digit Indian PIN code, starting with 1–9.", color = MaterialTheme.colorScheme.error)
        IndiaPostalDirectory.Match.UNKNOWN_PIN -> {
            Text("This PIN is not in the offline directory. Check it before continuing.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().toggleable(value = unlistedConfirmed, enabled = enabled, role = Role.Checkbox,
                onValueChange = { unlistedConfirmed = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = unlistedConfirmed, onCheckedChange = null, enabled = enabled)
                Text("I have checked this PIN and state", modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            }
        }
        else -> Unit
    }
    if (areas.isNotEmpty()) Text("Postal district: ${areas.map { it.district }.distinct().joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = city, onValueChange = onCityChange, enabled = enabled,
        label = { Text("City / town / locality") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

fun validBirthDate(value: String): Boolean = validDate(value) && value <= todayIndia()

@Composable fun BirthDateField(value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val parts = value.takeIf(::validBirthDate)?.split('-')?.map { it.toInt() }
        val calendar = Calendar.getInstance()
        val dialog = DatePickerDialog(context, { _, year, month, day ->
            onChange(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
        }, parts?.get(0) ?: 2000, parts?.get(1)?.minus(1) ?: Calendar.JANUARY, parts?.get(2) ?: 1)
        dialog.datePicker.maxDate = calendar.timeInMillis
        dialog.show()
    }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Date of birth", style = MaterialTheme.typography.labelMedium)
            Text(value.ifBlank { "Select date of birth" })
        }
        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Open birthday calendar")
    }
}
