package com.example.patient.ui.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.patient.ui.auth.AuthUiState
import com.example.patient.ui.auth.LoginScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class AuthScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_displaysElements() {
        composeTestRule.setContent {
            LoginScreen(
                uiState = AuthUiState(),
                onRequestOtp = {},
                onVerifyOtp = { _, _ -> },
                onChangePhone = {},
                onLoginSuccess = {}
            )
        }

        composeTestRule.onNodeWithText("Chettinad Care Patient Access").assertExists()
        composeTestRule.onNodeWithText("Mobile number").assertExists()
        composeTestRule.onNodeWithText("Send code").assertExists()
    }
}
