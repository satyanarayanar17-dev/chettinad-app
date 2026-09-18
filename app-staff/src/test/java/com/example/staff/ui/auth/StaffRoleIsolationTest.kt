package com.example.staff.ui.auth

import com.example.data.repository.AuthRepository
import com.example.domain.model.Role
import com.example.domain.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaffRoleIsolationTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val currentUserFlow = MutableStateFlow<User?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        every { authRepository.getStoredUser() } returns null
        every { authRepository.currentUser } returns currentUserFlow
        viewModel = AuthViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockLoginResponse(role: Role): User {
        val user = User(id = "1", name = "Test", role = role, isActive = true)
        coEvery { authRepository.login(any()) } returns Result.success(user)
        return user
    }

    @Test
    fun `PATIENT role is rejected in Staff app`() = runTest {
        mockLoginResponse(Role.PATIENT)
        
        viewModel.login("user", "pass")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertNull(state.user)
        assertTrue(state.error?.contains("patient application") == true)
        coVerify { authRepository.logout() }
    }

    @Test
    fun `DOCTOR role is allowed in Staff app`() = runTest {
        mockLoginResponse(Role.DOCTOR)
        
        viewModel.login("user", "pass")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(Role.DOCTOR, state.user?.role)
        assertNull(state.error)
        coVerify(exactly = 0) { authRepository.logout() }
    }

    @Test
    fun `NURSE role is allowed in Staff app`() = runTest {
        mockLoginResponse(Role.NURSE)
        
        viewModel.login("user", "pass")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(Role.NURSE, state.user?.role)
        assertNull(state.error)
        coVerify(exactly = 0) { authRepository.logout() }
    }

    @Test
    fun `ADMIN role is allowed in Staff app`() = runTest {
        mockLoginResponse(Role.ADMIN)
        
        viewModel.login("user", "pass")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(Role.ADMIN, state.user?.role)
        assertNull(state.error)
        coVerify(exactly = 0) { authRepository.logout() }
    }
}
