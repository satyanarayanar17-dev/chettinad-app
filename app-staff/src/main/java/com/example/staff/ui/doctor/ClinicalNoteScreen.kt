package com.example.staff.ui.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalNoteScreen(
    patientId: String,
    doctorId: String,
    uiState: ClinicalNoteUiState,
    onLoadDrafts: (String) -> Unit,
    onAutoSave: (String, String, String, String, String, String, String) -> Unit,
    onSubmit: (String, String, String, String, String, String, String, String, String) -> Unit,
    onSuccessNavigateBack: () -> Unit
) {
    var chiefComplaint by remember { mutableStateOf("") }
    var history by remember { mutableStateOf("") }
    var examination by remember { mutableStateOf("") }
    var assessment by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("") }
    var followUp by remember { mutableStateOf("") }
    var draftLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(patientId) {
        onLoadDrafts(patientId)
    }

    LaunchedEffect(uiState.existingDraft) {
        if (!draftLoaded && uiState.existingDraft != null) {
            val draft = uiState.existingDraft
            chiefComplaint = draft.chiefComplaint
            history = draft.history
            examination = draft.examination
            assessment = draft.assessment
            diagnosis = draft.diagnosis
            plan = draft.plan
            draftLoaded = true
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onSuccessNavigateBack()
        }
    }

    // Trigger autosave when text changes
    LaunchedEffect(chiefComplaint, history, examination, assessment, diagnosis, plan) {
        if (chiefComplaint.isNotBlank() || history.isNotBlank()) {
            onAutoSave(patientId, chiefComplaint, history, examination, assessment, diagnosis, plan)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Sticky Header with Status
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Clinical Documentation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Chettinad Care EMR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                
                AnimatedVisibility(visible = uiState.draftSaved, enter = fadeIn(), exit = fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Saved locally", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Editor
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            ClinicalSection("Chief Complaint") {
                EditorField(
                    value = chiefComplaint,
                    onValueChange = { chiefComplaint = it },
                    minLines = 2
                )
            }
            
            ClinicalSection("History of Present Illness") {
                EditorField(
                    value = history,
                    onValueChange = { history = it },
                    minLines = 4
                )
            }
            
            ClinicalSection("Examination") {
                EditorField(
                    value = examination,
                    onValueChange = { examination = it },
                    minLines = 4
                )
            }
            
            ClinicalSection("Assessment") {
                EditorField(
                    value = assessment,
                    onValueChange = { assessment = it },
                    minLines = 3
                )
            }
            
            ClinicalSection("Diagnosis") {
                EditorField(
                    value = diagnosis,
                    onValueChange = { diagnosis = it },
                    minLines = 2
                )
            }
            
            ClinicalSection("Plan") {
                EditorField(
                    value = plan,
                    onValueChange = { plan = it },
                    minLines = 4
                )
            }
            
            ClinicalSection("Follow-up Instructions") {
                EditorField(
                    value = followUp,
                    onValueChange = { followUp = it },
                    minLines = 2
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            if (uiState.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (uiState.hasConflict) "Version Conflict Detected" else "Submission Error", 
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = if (uiState.hasConflict) 
                                "The hospital record was updated by another user after you opened this note. Your draft has been preserved locally, but cannot be saved until you review the latest changes." 
                            else 
                                uiState.error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Action Footer
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    onSubmit(
                        patientId, doctorId, chiefComplaint, history,
                        examination, assessment, diagnosis, plan, followUp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(56.dp),
                enabled = !uiState.isSaving && chiefComplaint.isNotBlank(),
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Saving to hospital record...")
                } else if (uiState.saveSuccess) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Signed & Saved")
                } else {
                    Text("Sign & Save to Record")
                }
            }
        }
    }
}

@Composable
fun ClinicalSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = title, 
            style = MaterialTheme.typography.titleMedium, 
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    )
}
