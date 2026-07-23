package com.twango.lunexa.core.network.api

import com.twango.lunexa.core.network.dto.AccountDto
import com.twango.lunexa.core.network.dto.AccountPayloadDto
import com.twango.lunexa.core.network.dto.ApiEnvelope
import com.twango.lunexa.core.network.dto.AuthPayloadDto
import com.twango.lunexa.core.network.dto.BudgetPayloadDto
import com.twango.lunexa.core.network.dto.CategoryDto
import com.twango.lunexa.core.network.dto.ConfirmPasswordResetRequest
import com.twango.lunexa.core.network.dto.CreateAccountRequest
import com.twango.lunexa.core.network.dto.CreateBudgetRequest
import com.twango.lunexa.core.network.dto.CreateTransactionRequest
import com.twango.lunexa.core.network.dto.EmailVerificationPayloadDto
import com.twango.lunexa.core.network.dto.LoginRequest
import com.twango.lunexa.core.network.dto.RefreshTokenRequest
import com.twango.lunexa.core.network.dto.MonthlySummaryDto
import com.twango.lunexa.core.network.dto.PasswordResetPayloadDto
import com.twango.lunexa.core.network.dto.RegisterRequest
import com.twango.lunexa.core.network.dto.StartEmailVerificationRequest
import com.twango.lunexa.core.network.dto.StartPasswordResetRequest
import com.twango.lunexa.core.network.dto.TokenPairDto
import com.twango.lunexa.core.network.dto.TransactionPayloadDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LunexaApiService {
    @POST("auth/verification/start")
    suspend fun startEmailVerification(@Body request: StartEmailVerificationRequest): ApiEnvelope<EmailVerificationPayloadDto>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiEnvelope<AuthPayloadDto>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiEnvelope<AuthPayloadDto>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiEnvelope<TokenPairDto>

    @POST("auth/password-reset/start")
    suspend fun startPasswordReset(@Body request: StartPasswordResetRequest): ApiEnvelope<PasswordResetPayloadDto>

    @POST("auth/password-reset/confirm")
    suspend fun confirmPasswordReset(@Body request: ConfirmPasswordResetRequest): ApiEnvelope<AuthPayloadDto>

    @GET("accounts")
    suspend fun getAccounts(): ApiEnvelope<List<AccountDto>>

    @POST("accounts")
    suspend fun createAccount(@Body request: CreateAccountRequest): ApiEnvelope<AccountPayloadDto>

    @GET("categories")
    suspend fun getCategories(@Query("type") type: String? = null): ApiEnvelope<List<CategoryDto>>

    @POST("transactions")
    suspend fun createTransaction(@Body request: CreateTransactionRequest): ApiEnvelope<TransactionPayloadDto>

    @POST("budgets")
    suspend fun createBudget(@Body request: CreateBudgetRequest): ApiEnvelope<BudgetPayloadDto>

    @GET("analytics/monthly-summary")
    suspend fun getMonthlySummary(@Query("month") month: String): ApiEnvelope<MonthlySummaryDto>
}
