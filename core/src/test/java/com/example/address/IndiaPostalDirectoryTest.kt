package com.example.address

import org.junit.Assert.*
import org.junit.Test

class IndiaPostalDirectoryTest {
    @Test fun `bundled directory covers current states and common PINs`() {
        assertEquals(36, IndiaPostalDirectory.states.size)
        listOf("600001" to "Tamil Nadu", "110001" to "Delhi", "560001" to "Karnataka", "194101" to "Ladakh", "605001" to "Puducherry").forEach { (pin, state) ->
            assertEquals("$pin / $state", IndiaPostalDirectory.Match.MATCH, IndiaPostalDirectory.match(state, pin))
        }
    }
    @Test fun `a PIN from another state is rejected`() {
        assertEquals(IndiaPostalDirectory.Match.MISMATCH, IndiaPostalDirectory.match("Tamil Nadu", "110001"))
        assertEquals(IndiaPostalDirectory.Match.MISMATCH, IndiaPostalDirectory.match("Delhi", "600001"))
    }
    @Test fun `legacy state spellings normalize without losing valid addresses`() {
        assertEquals("Odisha", IndiaPostalDirectory.canonicalState("ORISSA"))
        assertEquals("Puducherry", IndiaPostalDirectory.canonicalState("Pondicherry"))
        assertEquals("Dadra and Nagar Haveli and Daman and Diu", IndiaPostalDirectory.canonicalState("DADRA & NAGAR HAVELI & DAMAN & DIU"))
    }
    @Test fun `malformed PINs differ from unlisted six digit PINs`() {
        for (pin in listOf("000001", "60001", "6000011", "٦٠٠٠٠١")) {
            assertEquals(IndiaPostalDirectory.Match.INVALID_FORMAT, IndiaPostalDirectory.match("Tamil Nadu", pin))
        }
        assertEquals(IndiaPostalDirectory.Match.UNKNOWN_PIN, IndiaPostalDirectory.match("Tamil Nadu", "999999"))
        assertEquals(IndiaPostalDirectory.Match.INCOMPLETE, IndiaPostalDirectory.match("", "600001"))
    }
    @Test fun `birth dates reject future and impossible days`() {
        assertTrue(validBirthDate("1992-02-29"))
        assertFalse(validBirthDate("1993-02-29"))
        assertFalse(validBirthDate("2099-01-01"))
        assertFalse(validBirthDate(""))
    }
}
