package com.example.staff.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppLogo
import com.example.domain.model.Role
import com.example.portal.PrivacyAndSupport

@Composable
fun LoginScreen(uiState: AuthUiState, onLogin: (String, String) -> Unit,
    onLoginSuccess: (Role) -> Unit, onErrorDismiss: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    LaunchedEffect(uiState.user) { uiState.user?.let { onLoginSuccess(it.role) } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppLogo(Modifier.width(220.dp).height(76.dp))
            Text("Chettinad Care Staff", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Text("Clinical Care. Connected.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Text("Secure Clinical Access", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start))
            OutlinedTextField(username, { username = it }, label = { Text("Staff ID / Username") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !uiState.isLoading)
            OutlinedTextField(password, { password = it }, label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !uiState.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(onClick = { onLogin(username, password) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                enabled = !uiState.isLoading && username.isNotBlank() && password.isNotBlank()) {
                if (uiState.isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Sign In")
            }
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Chettinad Hospital and Research Institute", style = MaterialTheme.typography.labelMedium)
            PrivacyAndSupport()
        }
    }
}
