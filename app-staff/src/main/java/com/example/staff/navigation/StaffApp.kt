package com.example.staff.navigation

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
import com.example.staff.ui.auth.AuthViewModel
import com.example.staff.ui.auth.LoginScreen
import com.example.ui.common.AppShell
import com.example.staff.ui.doctor.*
import com.example.staff.ui.nurse.*
import com.example.staff.ui.admin.*

@Composable
fun StaffApp(appContainer: AppContainer) {
    val navController = rememberNavController()

    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.provideFactory(appContainer.authRepository)
    )
    val authState by authViewModel.uiState.collectAsState()

    val startDestination = if (authState.user != null) {
        when (authState.user!!.role) {
            Role.DOCTOR -> "doctor/home"
            Role.NURSE -> "nurse/queue"
            Role.ADMIN -> "admin/dashboard"
            Role.PATIENT -> "auth" // Handled by viewmodel
        }
    } else {
        "auth"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("auth") {
            LoginScreen(
                uiState = authState,
                onLogin = authViewModel::login,
                onLoginSuccess = { role ->
                    val destination = when (role) {
                        Role.DOCTOR -> "doctor/home"
                        Role.NURSE -> "nurse/queue"
                        Role.ADMIN -> "admin/dashboard"
                        Role.PATIENT -> return@LoginScreen // viewmodel handles error
                    }
                    navController.navigate(destination) {
                        popUpTo("auth") { inclusive = true }
                    }
                },
                onErrorDismiss = authViewModel::dismissError
            )
        }

        // Doctor Flow
        composable("doctor/home") {
            val user = authState.user ?: return@composable
            val doctorViewModel: DoctorCommandCenterViewModel = viewModel(
                factory = DoctorCommandCenterViewModel.provideFactory(appContainer.doctorRepository)
            )
            val doctorUiState by doctorViewModel.uiState.collectAsState()

            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Command Center"
            ) { modifier ->
                Box(modifier = modifier) {
                    DoctorCommandCenterScreen(
                        uiState = doctorUiState,
                        department = user.department,
                        onLoadQueue = { dept -> doctorViewModel.loadQueue(dept) },
                        onPatientClick = { patientId ->
                            navController.navigate("doctor/dossier/$patientId")
                        }
                    )
                }
            }
        }

        composable("doctor/dossier/{id}") { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("id") ?: return@composable
            val user = authState.user ?: return@composable
            val dossierViewModel: PatientDossierViewModel = viewModel(
                factory = PatientDossierViewModel.provideFactory(appContainer.doctorRepository)
            )
            val dossierUiState by dossierViewModel.uiState.collectAsState()

            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Patient Dossier"
            ) { modifier ->
                Box(modifier = modifier) {
                    PatientDossierScreen(
                        patientId = patientId,
                        uiState = dossierUiState,
                        onLoadDossier = { dossierViewModel.loadDossier(patientId) },
                        onNavigateToNote = { navController.navigate("doctor/note/$patientId") },
                        onNavigateToPrescription = { navController.navigate("doctor/prescription/$patientId") }
                    )
                }
            }
        }

        composable("doctor/note/{id}") { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("id") ?: return@composable
            val user = authState.user ?: return@composable
            val noteViewModel: ClinicalNoteViewModel = viewModel(
                factory = ClinicalNoteViewModel.provideFactory(appContainer.doctorRepository)
            )
            val noteUiState by noteViewModel.uiState.collectAsState()

            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Clinical Note"
            ) { modifier ->
                Box(modifier = modifier) {
                    ClinicalNoteScreen(
                        patientId = patientId,
                        doctorId = user.id,
                        uiState = noteUiState,
                        onLoadDrafts = { noteViewModel.loadDrafts(patientId) },
                        onAutoSave = noteViewModel::autoSaveDraft,
                        onSubmit = noteViewModel::submitNote,
                        
                        onSuccessNavigateBack = {
                            noteViewModel.resetState()
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        composable("doctor/prescription/{id}") { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("id") ?: return@composable
            val user = authState.user ?: return@composable
            val prescriptionViewModel: PrescriptionViewModel = viewModel(
                factory = PrescriptionViewModel.provideFactory(appContainer.doctorRepository)
            )
            val prescriptionUiState by prescriptionViewModel.uiState.collectAsState()

            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Prescription Builder"
            ) { modifier ->
                Box(modifier = modifier) {
                    PrescriptionScreen(
                        patientId = patientId,
                        doctorId = user.id,
                        department = user.department ?: "General",
                        uiState = prescriptionUiState,
                        onSearchMedication = prescriptionViewModel::searchMedications,
                        onAddItem = prescriptionViewModel::addItem,
                        onRemoveItem = prescriptionViewModel::removeItem,
                        onSubmit = prescriptionViewModel::submitPrescription,
                        onSuccessNavigateBack = {
                            prescriptionViewModel.resetState()
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        // Nurse Flow
        composable("nurse/queue") {
            val user = authState.user ?: return@composable
            val nurseViewModel: NurseTriageQueueViewModel = viewModel(
                factory = NurseTriageQueueViewModel.provideFactory(appContainer.nurseRepository)
            )
            val nurseUiState by nurseViewModel.uiState.collectAsState()

            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Triage Queue"
            ) { modifier ->
                Box(modifier = modifier) {
                    NurseTriageQueueScreen(
                        uiState = nurseUiState,
                        onLoadQueue = { nurseViewModel.loadQueue() },
                        onPatientClick = { patientId ->
                            navController.navigate("nurse/triage/$patientId")
                        }
                    )
                }
            }
        }

        composable("nurse/triage/{id}") { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("id") ?: return@composable
            val user = authState.user ?: return@composable
            val triageViewModel: NurseTriageAssessmentViewModel = viewModel(
                factory = NurseTriageAssessmentViewModel.provideFactory(appContainer.nurseRepository)
            )
            val triageUiState by triageViewModel.uiState.collectAsState()

            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Triage Assessment"
            ) { modifier ->
                Box(modifier = modifier) {
                    NurseTriageAssessmentScreen(
                        patientId = patientId,
                        nurseId = user.id,
                        uiState = triageUiState,
                        onSubmit = triageViewModel::submitTriage,
                        onSuccessNavigateBack = {
                            triageViewModel.resetState()
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        // Admin Flow
        composable("admin/dashboard") {
            val user = authState.user ?: return@composable
            val adminViewModel: AdminDashboardViewModel = viewModel(
                factory = AdminDashboardViewModel.provideFactory(appContainer.adminRepository)
            )
            val adminUiState by adminViewModel.uiState.collectAsState()

            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Admin Dashboard"
            ) { modifier ->
                Box(modifier = modifier) {
                    AdminDashboardScreen(
                        uiState = adminUiState,
                        onLoadStaff = { adminViewModel.loadStaff() },
                        onAddStaffClick = { navController.navigate("admin/staff") }
                    )
                }
            }
        }

        composable("admin/staff") {
            val user = authState.user ?: return@composable
            val staffViewModel: AdminStaffViewModel = viewModel(
                factory = AdminStaffViewModel.provideFactory(appContainer.adminRepository)
            )
            val staffUiState by staffViewModel.uiState.collectAsState()

            AppShell(
                user = user,
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate("auth") { popUpTo(0) }
                },
                title = "Add Staff"
            ) { modifier ->
                Box(modifier = modifier) {
                    AdminStaffScreen(
                        uiState = staffUiState,
                        onSubmit = staffViewModel::createStaff,
                        onSuccessNavigateBack = {
                            staffViewModel.resetState()
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
