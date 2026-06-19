package com.twango.lunexa

import androidx.lifecycle.ViewModel
import com.twango.lunexa.core.network.auth.AuthTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    tokenStore: AuthTokenStore
) : ViewModel() {
    val accessToken: StateFlow<String?> = tokenStore.accessToken
}
