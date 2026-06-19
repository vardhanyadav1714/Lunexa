package com.twango.lunexa.core.network.dto

data class ApiEnvelope<T>(
    val data: T,
    val meta: ApiMeta? = null
)

data class ApiMeta(
    val requestId: String? = null,
    val pagination: PaginationDto? = null
)

data class PaginationDto(
    val limit: Int,
    val cursor: String?,
    val nextCursor: String?,
    val hasMore: Boolean
)

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val emailVerificationId: String,
    val emailVerificationCode: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class StartEmailVerificationRequest(
    val fullName: String,
    val email: String,
    val password: String
)

data class StartPasswordResetRequest(
    val email: String
)

data class ConfirmPasswordResetRequest(
    val resetId: String,
    val email: String,
    val code: String,
    val newPassword: String
)

data class EmailVerificationPayloadDto(
    val verificationId: String,
    val email: String,
    val expiresInSeconds: Long,
    val delivery: String? = null
)

data class PasswordResetPayloadDto(
    val resetId: String? = null,
    val email: String,
    val expiresInSeconds: Long,
    val message: String,
    val delivery: String? = null
)

data class AuthPayloadDto(
    val user: UserDto,
    val tokens: TokenPairDto
)

data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val refreshExpiresIn: Long
)

data class UserDto(
    val id: String,
    val fullName: String,
    val email: String,
    val status: String,
    val createdAt: String? = null,
    val lastLoginAt: String? = null
)

data class CreateAccountRequest(
    val name: String,
    val type: String,
    val currency: String = "INR",
    val openingBalance: String,
    val sortOrder: Int = 0
)

data class AccountPayloadDto(
    val account: AccountDto
)

data class AccountDto(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val openingBalance: String,
    val currentBalance: String,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val version: Int
)

data class CategoryDto(
    val id: String,
    val name: String,
    val type: String,
    val iconKey: String?,
    val colorHex: String?,
    val isDefault: Boolean,
    val sortOrder: Int,
    val createdAt: String?,
    val updatedAt: String?,
    val version: Int
)

data class CreateTransactionRequest(
    val accountId: String,
    val categoryId: String?,
    val type: String,
    val amount: String,
    val currency: String = "INR",
    val note: String?,
    val merchant: String?,
    val transactionDate: String,
    val metadata: Map<String, String> = emptyMap()
)

data class TransactionPayloadDto(
    val transaction: TransactionDto
)

data class TransactionDto(
    val id: String,
    val accountId: String,
    val transferAccountId: String?,
    val categoryId: String?,
    val type: String,
    val amount: String,
    val currency: String,
    val note: String?,
    val merchant: String?,
    val transactionDate: String,
    val postedAt: String?,
    val metadata: Map<String, Any>?,
    val createdAt: String?,
    val updatedAt: String?,
    val version: Int
)

data class CreateBudgetRequest(
    val categoryId: String,
    val periodMonth: String,
    val amount: String,
    val alertThresholdPercent: String = "80.00"
)

data class BudgetPayloadDto(
    val budget: BudgetDto
)

data class BudgetDto(
    val id: String,
    val categoryId: String,
    val periodMonth: String,
    val amount: String,
    val alertThresholdPercent: String,
    val createdAt: String?,
    val updatedAt: String?,
    val version: Int
)

data class MonthlySummaryDto(
    val month: String,
    val currency: String,
    val incomeTotal: String,
    val expenseTotal: String,
    val transferTotal: String,
    val netCashflow: String,
    val transactionCount: Int,
    val largestExpense: LargestExpenseDto?,
    val budgetSummary: BudgetSummaryDto
)

data class LargestExpenseDto(
    val transactionId: String,
    val amount: String,
    val categoryId: String,
    val transactionDate: String
)

data class BudgetSummaryDto(
    val budgetedAmount: String,
    val spentAmount: String,
    val remainingAmount: String,
    val utilizationPercent: String
)
