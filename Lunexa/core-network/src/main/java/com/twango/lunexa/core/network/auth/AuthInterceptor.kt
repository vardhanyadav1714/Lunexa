package com.twango.lunexa.core.network.auth

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: AuthTokenStore,
    private val gson: Gson
) : Interceptor {

    // Flag to prevent multiple simultaneous refresh attempts
    private val isRefreshing = AtomicBoolean(false)
    // Mutex to synchronize access to token refresh
    private val refreshLock = Any()

    // Create a separate OkHttpClient for refresh calls (without auth interceptor)
    private val refreshClient = OkHttpClient.Builder()
        .build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.currentAccessToken()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request()
                .newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        val response = chain.proceed(request)

        // If we got a 401, try to refresh the token
        if (response.code == 401 && token != null) {
            response.close()
            return handleTokenRefresh(chain)
        }

        return response
    }

    private fun handleTokenRefresh(chain: Interceptor.Chain): Response {
        // Synchronize to prevent multiple threads from refreshing simultaneously
        synchronized(refreshLock) {
            // Check if we're already refreshing
            if (isRefreshing.get()) {
                // Wait for refresh to complete and retry
                waitForRefreshComplete()
                val refreshedToken = tokenStore.currentAccessToken()
                if (refreshedToken != null) {
                    return retryWithNewToken(chain, refreshedToken)
                } else {
                    throw IOException("Token refresh failed - no token available")
                }
            }

            // Double-check if another thread already refreshed the token
            val currentToken = tokenStore.currentAccessToken()
            if (currentToken != null && currentToken != token) {
                // Token was already refreshed by another thread, retry with new token
                return retryWithNewToken(chain, currentToken)
            }

            // Start refresh process
            isRefreshing.set(true)
            try {
                val refreshToken = tokenStore.getRefreshToken()
                if (refreshToken == null) {
                    throw IOException("No refresh token available")
                }

                // Perform the refresh using direct HTTP call
                val newTokens = performTokenRefresh(refreshToken)

                // Save the new tokens
                tokenStore.saveTokens(
                    accessToken = newTokens.first,
                    refreshToken = newTokens.second
                )

                // Retry the original request with the new token
                return retryWithNewToken(chain, newTokens.first)
            } catch (e: Exception) {
                // Refresh failed, clear tokens and propagate the error
                tokenStore.clear()
                throw IOException("Authentication failed - please log in again", e)
            } finally {
                isRefreshing.set(false)
            }
        }
    }

    private fun performTokenRefresh(refreshToken: String): Pair<String, String> {
        // Extract base URL from the request
        val baseUrl = "https://lunexa-api.vardhanyadav01001.workers.dev/api/v1/"

        val requestBody = FormBody.Builder()
            .add("refreshToken", refreshToken)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/auth/refresh")
            .post(requestBody)
            .build()

        val response = refreshClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Refresh failed with code ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body")

        // Parse the response using Gson
        val refreshResponse = gson.fromJson(responseBody, RefreshResponse::class.java)

        return Pair(
            refreshResponse.data.tokens.accessToken,
            refreshResponse.data.tokens.refreshToken
        )
    }

    private fun retryWithNewToken(chain: Interceptor.Chain, token: String): Response {
        val newRequest = chain.request()
            .newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(newRequest)
    }

    private fun waitForRefreshComplete() {
        var attempts = 0
        while (isRefreshing.get() && attempts < 50) {
            Thread.sleep(100)
            attempts++
        }
    }

    // Data classes for parsing the refresh response
    private data class RefreshResponse(
        val data: TokenData
    )

    private data class TokenData(
        val tokens: Tokens
    )

    private data class Tokens(
        val accessToken: String,
        val refreshToken: String
    )
}
