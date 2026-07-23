package com.twango.lunexa.core.network.auth

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: AuthTokenStore,
    private val gson: Gson
) : Interceptor {

    private val refreshLock = Any()

    private val refreshClient = OkHttpClient.Builder()
        .build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenStore.currentAccessToken()
        val request = if (token.isNullOrBlank() || originalRequest.isAuthRequest()) {
            originalRequest
        } else {
            originalRequest
                .newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        val response = chain.proceed(request)

        if (response.code == 401 && !token.isNullOrBlank() && !originalRequest.isAuthRequest()) {
            response.close()
            return handleTokenRefresh(chain, token)
        }

        return response
    }

    private fun handleTokenRefresh(chain: Interceptor.Chain, staleAccessToken: String): Response {
        synchronized(refreshLock) {
            val currentToken = tokenStore.currentAccessToken()
            if (!currentToken.isNullOrBlank() && currentToken != staleAccessToken) {
                return retryWithNewToken(chain, currentToken)
            }

            try {
                val refreshToken = tokenStore.getRefreshToken()
                if (refreshToken.isNullOrBlank()) {
                    throw IOException("No refresh token available")
                }

                val newTokens = performTokenRefresh(refreshToken)

                tokenStore.saveTokens(
                    accessToken = newTokens.first,
                    refreshToken = newTokens.second
                )

                return retryWithNewToken(chain, newTokens.first)
            } catch (e: Exception) {
                tokenStore.clear()
                throw IOException("Authentication failed - please log in again", e)
            }
        }
    }

    private fun performTokenRefresh(refreshToken: String): Pair<String, String> {
        val baseUrl = "https://lunexa-api.vardhanyadav01001.workers.dev/api/v1"
        val requestBody = gson
            .toJson(mapOf("refreshToken" to refreshToken))
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/auth/refresh")
            .post(requestBody)
            .build()

        refreshClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Refresh failed with code ${response.code}")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")
            val refreshResponse = gson.fromJson(responseBody, RefreshResponse::class.java)

            return Pair(
                refreshResponse.data.accessToken,
                refreshResponse.data.refreshToken
            )
        }
    }

    private fun retryWithNewToken(chain: Interceptor.Chain, token: String): Response {
        val newRequest = chain.request()
            .newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(newRequest)
    }

    private fun Request.isAuthRequest(): Boolean = url.encodedPath.contains("/auth/")

    private data class RefreshResponse(
        val data: TokenData
    )

    private data class TokenData(
        val accessToken: String,
        val refreshToken: String
    )
}
