package com.example.staff.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStaffScreen(
    uiState: AdminStaffUiState,
    onSubmit: (String, Role, String) -> Unit,
    onSuccessNavigateBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.DOCTOR) }
    var department by remember { mutableStateOf("") }
    var expandedRoleDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            onSuccessNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Identity Management",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = "Provision Staff",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Profile Details", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Legal Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                shape = MaterialTheme.shapes.medium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Access Privileges", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            ExposedDropdownMenuBox(
                expanded = expandedRoleDropdown,
                onExpandedChange = { expandedRoleDropdown = it }
            ) {
                OutlinedTextField(
                    value = role.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("System Role") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRoleDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                ExposedDropdownMenu(
                    expanded = expandedRoleDropdown,
                    onDismissRequest = { expandedRoleDropdown = false }
                ) {
                    listOf(Role.DOCTOR, Role.NURSE, Role.ADMIN).forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r.name) },
                            onClick = {
                                role = r
                                expandedRoleDropdown = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (role == Role.DOCTOR || role == Role.NURSE) {
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Clinical Department (e.g. Cardiology)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    placeholder = { Text("e.g. General Medicine") }
                )
            }
            
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
            
            Button(
                onClick = { onSubmit(name, role, department) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isSubmitting && name.isNotBlank(),
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Provision Staff Account")
                }
            }
        }
    }
}
