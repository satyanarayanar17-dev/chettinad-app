package com.example.staff.ui.doctor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.Medication
import com.example.domain.model.PrescriptionItem

@Composable
fun PrescriptionScreen(
    patientId: String,
    doctorId: String,
    department: String,
    uiState: PrescriptionUiState,
    onSearchMedication: (String) -> Unit,
    onAddItem: (PrescriptionItem) -> Unit,
    onRemoveItem: (PrescriptionItem) -> Unit,
    onSubmit: (String, String, String) -> Unit,
    onSuccessNavigateBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMedication by remember { mutableStateOf<Medication?>(null) }

    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            onSuccessNavigateBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Electronic Prescription",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = "Patient $patientId",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Medication Order", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.currentItems.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No medications added yet.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uiState.currentItems) { item ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.medicationName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${item.dose} • ${item.frequency} • ${item.durationDays} days", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Route: ${item.route} | Instr: ${item.instructions}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onRemoveItem(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Medication")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (uiState.submitError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = uiState.submitError, 
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                Button(
                    onClick = { onSubmit(patientId, doctorId, department) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !uiState.isSubmitting && uiState.currentItems.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Sign & Authorize Prescription")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())
                ) {
                    Text("Add Medication", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it
                            onSearchMedication(it)
                        },
                        label = { Text("Search Formulary") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    
                    if (uiState.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    } else if (searchQuery.isNotBlank() && uiState.searchResults.isNotEmpty() && selectedMedication == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium, 
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                uiState.searchResults.take(4).forEach { med ->
                                    Text(
                                        text = med.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                selectedMedication = med
                                                searchQuery = med.name 
                                            }
                                            .padding(12.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    
                    if (selectedMedication != null) {
                        var dose by remember { mutableStateOf("") }
                        var frequency by remember { mutableStateOf("") }
                        var duration by remember { mutableStateOf("") }
                        var route by remember { mutableStateOf("") }
                        var instructions by remember { mutableStateOf("") }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Dosage & Administration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = dose,
                                onValueChange = { dose = it },
                                label = { Text("Dose (e.g. 500mg)") },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            )
                            OutlinedTextField(
                                value = frequency,
                                onValueChange = { frequency = it },
                                label = { Text("Freq (e.g. BD)") },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = duration,
                                onValueChange = { duration = it },
                                label = { Text("Days") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            )
                            OutlinedTextField(
                                value = route,
                                onValueChange = { route = it },
                                label = { Text("Route (e.g. PO)") },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = instructions,
                            onValueChange = { instructions = it },
                            label = { Text("Special Instructions") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val item = PrescriptionItem(
                                    medicationId = selectedMedication!!.id,
                                    medicationName = selectedMedication!!.name,
                                    dose = dose,
                                    frequency = frequency,
                                    durationDays = duration.toIntOrNull() ?: 0,
                                    route = route,
                                    instructions = instructions
                                )
                                onAddItem(item)
                                showAddDialog = false
                                selectedMedication = null
                                searchQuery = ""
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = dose.isNotBlank() && frequency.isNotBlank() && duration.isNotBlank(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Confirm & Add to Order")
                        }
                    }
                }
            }
        }
    }
}
