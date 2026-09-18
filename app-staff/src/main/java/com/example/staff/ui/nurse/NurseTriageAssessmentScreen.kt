package com.example.staff.ui.nurse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.TriagePriority

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NurseTriageAssessmentScreen(
    patientId: String,
    nurseId: String,
    uiState: NurseTriageUiState,
    onSubmit: (
        patientId: String,
        nurseId: String,
        chiefComplaint: String,
        symptoms: String,
        temp: String,
        pulse: String,
        bpSystolic: String,
        bpDiastolic: String,
        spo2: String,
        respRate: String,
        weight: String,
        height: String,
        painScore: String,
        priority: TriagePriority,
        notes: String
    ) -> Unit,
    onSuccessNavigateBack: () -> Unit
) {
    var chiefComplaint by remember { mutableStateOf("") }
    var symptoms by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    var bpSystolic by remember { mutableStateOf("") }
    var bpDiastolic by remember { mutableStateOf("") }
    var spo2 by remember { mutableStateOf("") }
    var respRate by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var painScore by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TriagePriority.ROUTINE) }

    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            onSuccessNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Triage Assessment",
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Presenting Complaint", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = chiefComplaint,
                onValueChange = { chiefComplaint = it },
                label = { Text("Chief Complaint") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = symptoms,
                onValueChange = { symptoms = it },
                label = { Text("Symptoms & Context") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vital Signs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalField("Temp (°C)", temp, { temp = it }, Modifier.weight(1f))
                VitalField("Pulse (bpm)", pulse, { pulse = it }, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalField("BP Sys (mmHg)", bpSystolic, { bpSystolic = it }, Modifier.weight(1f))
                VitalField("BP Dia (mmHg)", bpDiastolic, { bpDiastolic = it }, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalField("SpO2 (%)", spo2, { spo2 = it }, Modifier.weight(1f))
                VitalField("Resp Rate (/min)", respRate, { respRate = it }, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalField("Weight (kg)", weight, { weight = it }, Modifier.weight(1f), isDecimal = true)
                VitalField("Height (cm)", height, { height = it }, Modifier.weight(1f), isDecimal = true)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalField("Pain Score (0-10)", painScore, { painScore = it }, Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f)) // Empty spacer for alignment
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("Acuity Priority", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TriagePriority.entries.forEach { p ->
                    PriorityChip(
                        priority = p, 
                        selected = priority == p, 
                        onClick = { priority = p },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("Additional Clinical Notes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("Enter clinical observations...") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (uiState.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = uiState.error, 
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    onSubmit(
                        patientId, nurseId, chiefComplaint, symptoms, temp, pulse,
                        bpSystolic, bpDiastolic, spo2, respRate, weight, height, painScore, priority, notes
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
                enabled = !uiState.isSubmitting,
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Complete Triage", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VitalField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, isDecimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.titleMedium
    )
}

@Composable
fun PriorityChip(priority: TriagePriority, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val containerColor = if (selected) {
        when(priority) {
            TriagePriority.EMERGENCY -> MaterialTheme.colorScheme.error
            TriagePriority.URGENT -> Color(0xFFF57F17) // Orange
            TriagePriority.ROUTINE -> MaterialTheme.colorScheme.secondary
        }
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
        modifier = modifier.padding(horizontal = 4.dp).height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = priority.name, 
                color = contentColor, 
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
