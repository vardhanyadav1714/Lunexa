package com.twango.lunexa.feature.auth.data

import com.twango.lunexa.core.network.api.LunexaApiService
import com.twango.lunexa.core.network.auth.AuthTokenStore
import com.twango.lunexa.core.network.dto.ConfirmPasswordResetRequest
import com.twango.lunexa.core.network.dto.EmailVerificationPayloadDto
import com.twango.lunexa.core.network.dto.LoginRequest
import com.twango.lunexa.core.network.dto.PasswordResetPayloadDto
import com.twango.lunexa.core.network.dto.RegisterRequest
import com.twango.lunexa.core.network.dto.StartEmailVerificationRequest
import com.twango.lunexa.core.network.dto.StartPasswordResetRequest
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: LunexaApiService,
    private val tokenStore: AuthTokenStore
) {
    suspend fun startEmailVerification(
        fullName: String,
        email: String,
        password: String
    ): EmailVerificationPayloadDto {
        val response = apiService.startEmailVerification(
            StartEmailVerificationRequest(
                fullName = fullName,
                email = email,
                password = password
            )
        )
        return response.data
    }

    suspend fun register(
        fullName: String,
        email: String,
        password: String,
        emailVerificationId: String,
        emailVerificationCode: String
    ) {
        val response = apiService.register(
            RegisterRequest(
                fullName = fullName,
                email = email,
                password = password,
                emailVerificationId = emailVerificationId,
                emailVerificationCode = emailVerificationCode
            )
        )
        tokenStore.saveTokens(
            accessToken = response.data.tokens.accessToken,
            refreshToken = response.data.tokens.refreshToken
        )
    }

    suspend fun login(email: String, password: String) {
        val response = apiService.login(
            LoginRequest(
                email = email,
                password = password
            )
        )
        tokenStore.saveTokens(
            accessToken = response.data.tokens.accessToken,
            refreshToken = response.data.tokens.refreshToken
        )
    }

    suspend fun startPasswordReset(email: String): PasswordResetPayloadDto {
        val response = apiService.startPasswordReset(
            StartPasswordResetRequest(email = email)
        )
        return response.data
    }

    suspend fun confirmPasswordReset(
        resetId: String,
        email: String,
        code: String,
        newPassword: String
    ) {
        val response = apiService.confirmPasswordReset(
            ConfirmPasswordResetRequest(
                resetId = resetId,
                email = email,
                code = code,
                newPassword = newPassword
            )
        )
        tokenStore.saveTokens(
            accessToken = response.data.tokens.accessToken,
            refreshToken = response.data.tokens.refreshToken
        )
    }
}
