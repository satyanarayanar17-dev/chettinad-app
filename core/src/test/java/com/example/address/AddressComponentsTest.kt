package com.example.address

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddressComponentsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun birthdaySelectionUsesCalendarAndKeepsTheChosenDay() {
        var selected = ""
        compose.setContent { AppTheme { BirthDateField("", { selected = it }) } }
        compose.onNodeWithText("Select date of birth").performClick()
        val dialog = ShadowDialog.getLatestDialog() as DatePickerDialog
        assertTrue(dialog.datePicker.maxDate <= System.currentTimeMillis())
        dialog.datePicker.updateDate(1992, 1, 29)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("1992-02-29", selected)
    }
    @Test fun changingTheStateRemovesAMismatchAndRevalidatesTheForm() {
        val state = mutableStateOf("Tamil Nadu")
        var valid = true
        compose.setContent { AppTheme { Column {
            IndianAddressFields(state.value, "110001", "New Delhi", { state.value = it }, {}, {}, onValidityChange = { valid = it })
        } } }
        compose.waitForIdle()
        assertFalse(valid)
        compose.onNodeWithText("This PIN code does not match the selected state. Check the PIN code or change the state.").assertExists()
        compose.onNodeWithText("Tamil Nadu").performClick()
        compose.onNodeWithText("Delhi").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(valid)
        compose.onNodeWithText("This PIN code does not match the selected state. Check the PIN code or change the state.").assertDoesNotExist()
    }
    @Test fun confirmingAnUnlistedPINDoesNotConfirmAnotherPIN() {
        val pin = mutableStateOf("999999")
        var valid = true
        compose.setContent { AppTheme { Column {
            IndianAddressFields("Tamil Nadu", pin.value, "Synthetic city", {}, { pin.value = it }, {}, onValidityChange = { valid = it })
        } } }
        compose.waitForIdle()
        assertFalse(valid)
        compose.onNodeWithText("I have checked this PIN and state").performClick()
        compose.waitForIdle()
        assertTrue(valid)
        compose.runOnIdle { pin.value = "999998" }
        compose.waitForIdle()
        assertFalse(valid)
    }
}
