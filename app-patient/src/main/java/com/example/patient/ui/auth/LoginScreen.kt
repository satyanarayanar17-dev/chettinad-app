package com.example.patient.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.address.BirthDateField
import com.example.address.IndianAddressFields
import com.example.address.validBirthDate
import com.example.domain.model.Role
import com.example.ui.components.AppLogo

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onRequestOtp: (String) -> Unit,
    onVerifyOtp: (String, Map<String, String>?) -> Unit,
    onChangePhone: () -> Unit,
    onLoginSuccess: (Role) -> Unit
) {
    // Keep OTP and demographics in memory only, never in saved instance state.
    var phone by remember { mutableStateOf("") }
    var code by remember(uiState.challengeId, uiState.otpRequested) { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.expiresAt) { while (uiState.otpRequested) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    var addressValid by remember(uiState.phone) { mutableStateOf(false) }
    val profile = remember(uiState.phone) { mutableStateMapOf<String, String>() }
    LaunchedEffect(uiState.user) { uiState.user?.let { onLoginSuccess(it.role) } }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppLogo(modifier = Modifier.width(220.dp).height(76.dp))
            Text("Chettinad Care Patient Access", style = MaterialTheme.typography.headlineMedium)
            Text("Sign in securely with a code sent to your mobile.")
            if (!uiState.otpRequested) {
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Mobile number") }, placeholder = { Text("+91") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { onRequestOtp(phone) }, enabled = !uiState.isLoading && phone.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()) { Text("Send code") }
            } else {
                Text("Code sent to ${uiState.phone}")
                uiState.demoCode?.let { Text("Development test code: $it · synthetic testing only", color = MaterialTheme.colorScheme.error) }
                Text(if (now < uiState.expiresAt) "Code expires in ${((uiState.expiresAt-now)/1000).coerceAtLeast(0)} seconds" else "Code expired. Request a new code.")
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter { c -> c in '0'..'9' }.take(6) },
                    label = { Text("Six-digit SMS code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth()
                )
                if (uiState.profileRequired) {
                    Text("New patient registration", style = MaterialTheme.typography.titleLarge)
                    Text("Enter the patient's details as they should appear in hospital records.")
                    OutlinedTextField(value = profile["name"].orEmpty(), onValueChange = { profile["name"] = it },
                        label = { Text("Full name") }, singleLine = true, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth())
                    BirthDateField(profile["dob"].orEmpty(), { profile["dob"] = it }, !uiState.isLoading)
                    OutlinedTextField(value = profile["address"].orEmpty(), onValueChange = { profile["address"] = it },
                        label = { Text("Address") }, enabled = !uiState.isLoading, modifier = Modifier.fillMaxWidth())
                    IndianAddressFields(profile["state"].orEmpty(), profile["pin_code"].orEmpty(), profile["city"].orEmpty(),
                        { profile["state"] = it }, { profile["pin_code"] = it }, { profile["city"] = it },
                        enabled = !uiState.isLoading, onValidityChange = { addressValid = it })
                    OutlinedTextField(value = profile["emergency_contact"].orEmpty(), onValueChange = { profile["emergency_contact"] = it },
                        label = { Text("Emergency contact mobile") }, singleLine = true, enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                    Text("Gender", modifier = Modifier.align(Alignment.Start))
                    listOf("Female", "Male", "Other", "Not specified").forEach { gender ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = profile["gender"] == gender,
                                onClick = { profile["gender"] = gender }, enabled = !uiState.isLoading)
                            Text(gender)
                        }
                    }
                }
                val required = listOf("name", "dob", "address", "city", "state", "pin_code", "emergency_contact", "gender")
                Button(
                    onClick = { onVerifyOtp(code, if (uiState.profileRequired) profile.toMap() else null) },
                    enabled = !uiState.isLoading && now < uiState.expiresAt && code.length == 6 &&
                        (!uiState.profileRequired || (addressValid && validBirthDate(profile["dob"].orEmpty()) && required.all { !profile[it].isNullOrBlank() })),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (uiState.profileRequired) "Register and sign in" else "Verify and sign in") }
                TextButton(onClick = { code = ""; onRequestOtp(uiState.phone) }, enabled = !uiState.isLoading && now >= uiState.resendAt) {
                    Text(if (now >= uiState.resendAt) "Request a new code" else "Resend in ${((uiState.resendAt-now)/1000).coerceAtLeast(0)} seconds")
                }
                TextButton(onClick = { code = ""; profile.clear(); onChangePhone() }, enabled = !uiState.isLoading) {
                    Text("Change mobile number")
                }
            }
            if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Chettinad Hospital and Research Institute", style = MaterialTheme.typography.labelMedium)
        }
    }
}
