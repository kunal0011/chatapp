package com.chatapp

import com.chatapp.domain.repository.AuthRepository
import com.chatapp.ui.viewmodel.AuthViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submitLoginSuccessClearsLoading() = runTest {
        val repository = mockk<AuthRepository>()
        coEvery { repository.login(any(), any()) } returns Unit

        val vm = AuthViewModel(repository)
        vm.onPhoneChange("+15550000001")
        vm.onPasswordChange("Password123")

        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.loading)
    }
}
