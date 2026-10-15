package com.example.staff.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.di.AppContainer
import com.example.staff.ui.auth.AuthViewModel
import com.example.staff.ui.auth.LoginScreen
import com.example.portal.StaffPortal
import com.example.portal.CareReminders

@Composable fun StaffApp(appContainer: AppContainer) {
    val auth: AuthViewModel = viewModel(factory = AuthViewModel.provideFactory(appContainer.authRepository))
    val state by auth.uiState.collectAsState()
    val context = LocalContext.current
    val user = state.user
    if (user == null) LoginScreen(state, auth::login, {}, auth::dismissError)
    else key(user.id) {
        val store = remember { ViewModelStore() }
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = store } }
        DisposableEffect(store) { onDispose { store.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        StaffPortal(appContainer, user) {
            CareReminders.cancel(context)
            java.io.File(context.cacheDir, "medical-documents").deleteRecursively()
            auth.logout()
        }
        }
    }
}
