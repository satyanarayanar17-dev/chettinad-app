package com.example.patient.ui.auth

import org.junit.Assert.*
import org.junit.Test

class PatientOtpValidationTest {
    @Test fun `Indian formats normalize to one identity`() {
        listOf("9876543210", "+91 98765 43210", "919876543210", "(98765)-43210").forEach {
            assertEquals("+919876543210", normalizePhone(it))
        }
    }
    @Test fun `invalid mobile numbers are rejected`() {
        listOf("", "1234567890", "+449876543210", "987654321", "98765432100").forEach {
            assertNull(normalizePhone(it))
        }
    }
}
