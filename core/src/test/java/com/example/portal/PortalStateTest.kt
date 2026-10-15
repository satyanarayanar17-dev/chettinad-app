package com.example.portal

import com.example.data.api.*
import com.example.domain.model.Role
import com.example.domain.model.User
import com.example.util.TokenManager
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PortalStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<PortalApi>(relaxed = true)
    private val tokens = mockk<TokenManager>(relaxed = true)
    private lateinit var vm: PortalViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { api.appointments(any(), any()) } returns emptyList()
        coEvery { api.labs(any(), any()) } returns emptyList()
        coEvery { api.directory() } returns Directory()
        vm = PortalViewModel(api, User("doctor-1", "Synthetic doctor", Role.DOCTOR), tokens)
    }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun bundle(id: String) = ClinicalBundle(PatientProfile(id, "MRN-$id", "Synthetic $id", "+919100000001", "1990-01-01", "Female"))
    @Test fun `switching patients cancels an older response`() = runTest(dispatcher) {
        advanceUntilIdle()
        coEvery { api.record("a") } coAnswers { delay(1000); bundle("a") }
        coEvery { api.record("b") } returns bundle("b")
        vm.loadPatient("a"); runCurrent()
        vm.loadPatient("b"); advanceUntilIdle()
        assertEquals("b", vm.state.value.patientId)
        assertEquals("b", vm.state.value.record?.patient?.id)
    }
    @Test fun `double tap executes a write only once while in flight`() = runTest(dispatcher) {
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>(); var calls = 0
        vm.action("Saved") { calls++; gate.await() }
        vm.action("Saved again") { calls++; gate.await() }
        runCurrent()
        assertEquals(1, calls); assertTrue(vm.state.value.busy)
        gate.complete(Unit); advanceUntilIdle()
        assertFalse(vm.state.value.busy)
    }
    @Test fun `slots are only offered for the searched doctor and date`() = runTest(dispatcher) {
        advanceUntilIdle()
        val slot = Slot("2026-10-16T05:00:00Z", "2026-10-16T05:15:00Z")
        coEvery { api.slots("doctor-a", "2026-10-16") } returns listOf(slot)
        vm.slots("doctor-a", "2026-10-16"); advanceUntilIdle()
        val state = vm.state.value
        assertEquals(listOf(slot), state.availableSlots("doctor-a", "2026-10-16"))
        assertTrue(state.availableSlots("doctor-b", "2026-10-16").isEmpty())
        assertTrue(state.availableSlots("doctor-a", "2026-10-17").isEmpty())
    }
    @Test fun `a slower slot search cannot replace a newer doctor's availability`() = runTest(dispatcher) {
        advanceUntilIdle()
        val old = Slot("2026-10-16T05:00:00Z", "2026-10-16T05:15:00Z")
        val current = Slot("2026-10-17T06:00:00Z", "2026-10-17T06:15:00Z")
        coEvery { api.slots("doctor-a", "2026-10-16") } coAnswers { delay(1000); listOf(old) }
        coEvery { api.slots("doctor-b", "2026-10-17") } returns listOf(current)
        vm.slots("doctor-a", "2026-10-16"); runCurrent()
        vm.slots("doctor-b", "2026-10-17"); advanceUntilIdle()
        val state = vm.state.value
        assertEquals(listOf(current), state.availableSlots("doctor-b", "2026-10-17"))
        assertTrue(state.availableSlots("doctor-a", "2026-10-16").isEmpty())
    }
}
