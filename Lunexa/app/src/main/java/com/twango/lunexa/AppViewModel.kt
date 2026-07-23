package com.twango.lunexa

import androidx.lifecycle.ViewModel
import com.twango.lunexa.core.network.auth.AuthTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val tokenStore: AuthTokenStore
) : ViewModel() {

    val accessToken: StateFlow<String?> = tokenStore.accessToken

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    /**
     * Call this when an authentication error occurs (e.g., 401 after failed refresh)
     */
    fun onAuthError(message: String) {
        tokenStore.clear()
        _authError.value = message
    }

    /**
     * Clear the authentication error after it's been handled
     */
    fun clearAuthError() {
        _authError.value = null
    }

    /**
     * Clear tokens and reset authentication state
     */
    fun clearAuth() {
        tokenStore.clear()
        _authError.value = "Session expired. Please log in again."
    }
}
