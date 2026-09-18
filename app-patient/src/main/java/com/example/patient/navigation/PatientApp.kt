package com.example.patient.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.di.AppContainer
import com.example.domain.model.Role
import com.example.patient.ui.auth.AuthViewModel
import com.example.patient.ui.auth.LoginScreen
import com.example.ui.common.AppShell
import com.example.patient.ui.patient.PatientHomeScreen
import com.example.patient.ui.patient.PatientRecordsScreen
import com.example.patient.ui.patient.PatientRecordsViewModel

@Composable
fun PatientApp(appContainer: AppContainer) {
    val navController = rememberNavController()

    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.provideFactory(appContainer.authRepository)
    )
    val authState by authViewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (authState.user != null) "patient/home" else "auth"
    ) {
        composable("auth") {
            LoginScreen(
                uiState = authState,
                onLogin = authViewModel::login,
                onLoginSuccess = { role ->
                    if (role == Role.PATIENT) {
                        navController.navigate("patient/home") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                },
                onErrorDismiss = authViewModel::dismissError
            )
        }

        composable("patient/home") {
            val user = authState.user ?: return@composable
            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Patient Portal"
            ) { modifier ->
                Box(modifier = modifier) {
                    PatientHomeScreen(
                        user = user,
                        onViewRecords = { navController.navigate("patient/records") },
                        onViewPrescriptions = { /* not implemented yet */ }
                    )
                }
            }
        }

        composable("patient/records") {
            val user = authState.user ?: return@composable
            val recordsViewModel: PatientRecordsViewModel = viewModel(
                factory = PatientRecordsViewModel.provideFactory(appContainer.patientRepository)
            )
            val recordsUiState by recordsViewModel.uiState.collectAsState()

            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Medical Records"
            ) { modifier ->
                Box(modifier = modifier) {
                    PatientRecordsScreen(
                        patientId = user.id,
                        uiState = recordsUiState,
                        onLoadRecords = { recordsViewModel.loadRecords(user.id) }
                    )
                }
            }
        }
    }
}
