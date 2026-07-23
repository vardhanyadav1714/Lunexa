package com.twango.lunexa.core.network.auth

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthTokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("lunexa_auth", Context.MODE_PRIVATE)
    private val _accessToken = MutableStateFlow(preferences.getString(KEY_ACCESS_TOKEN, null))

    val accessToken: StateFlow<String?> = _accessToken

    fun currentAccessToken(): String? = _accessToken.value

    fun getRefreshToken(): String? = preferences.getString(KEY_REFRESH_TOKEN, null)

    fun saveTokens(accessToken: String, refreshToken: String) {
        preferences.edit(commit = true) {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        _accessToken.value = accessToken
    }

    fun clear() {
        preferences.edit(commit = true) {
            clear()
        }
        _accessToken.value = null
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
