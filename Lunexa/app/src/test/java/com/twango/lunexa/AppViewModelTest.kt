package com.twango.lunexa

import com.twango.lunexa.core.network.auth.AuthTokenStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
        every { tokenStore.accessToken } returns flowOf("test-access-token")

        viewModel = AppViewModel(tokenStore)

        assertEquals("test-access-token", viewModel.accessToken.value)
    }

    @Test
    fun `accessToken is null when no token in store`() = runTest {
        every { tokenStore.accessToken } returns flowOf(null)

        viewModel = AppViewModel(tokenStore)

        assertNull(viewModel.accessToken.value)
    }

    @Test
    fun `accessToken updates when token store changes`() = runTest {
        every { tokenStore.accessToken } returns flowOf(null, "new-token")

        viewModel = AppViewModel(tokenStore)

        // The StateFlow should emit the latest value
        assertEquals("new-token", viewModel.accessToken.value)
    }
}
