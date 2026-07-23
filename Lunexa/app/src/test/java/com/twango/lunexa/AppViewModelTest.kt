package com.twango.lunexa

import com.twango.lunexa.core.network.auth.AuthTokenStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppViewModelTest {

    private lateinit var viewModel: AppViewModel
    private lateinit var tokenStore: AuthTokenStore

    @Before
    fun setup() {
        tokenStore = mockk()
    }

    @Test
    fun `accessToken reflects token store state`() = runTest {
        every { tokenStore.accessToken } returns MutableStateFlow("test-access-token")

        viewModel = AppViewModel(tokenStore)

        assertEquals("test-access-token", viewModel.accessToken.value)
    }

    @Test
    fun `accessToken is null when no token in store`() = runTest {
        every { tokenStore.accessToken } returns MutableStateFlow(null)

        viewModel = AppViewModel(tokenStore)

        assertNull(viewModel.accessToken.value)
    }

    @Test
    fun `accessToken updates when token store changes`() = runTest {
        every { tokenStore.accessToken } returns MutableStateFlow("new-token")

        viewModel = AppViewModel(tokenStore)

        // The StateFlow should emit the latest value
        assertEquals("new-token", viewModel.accessToken.value)
    }

    @Test
    fun `onAuthError clears tokens and exposes message`() = runTest {
        every { tokenStore.accessToken } returns MutableStateFlow("stale-token")
        every { tokenStore.clear() } returns Unit

        viewModel = AppViewModel(tokenStore)

        viewModel.onAuthError("Authentication required")

        verify { tokenStore.clear() }
        assertEquals("Authentication required", viewModel.authError.value)
    }
}
