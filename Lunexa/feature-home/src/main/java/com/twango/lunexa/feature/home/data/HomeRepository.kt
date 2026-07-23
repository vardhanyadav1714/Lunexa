package com.twango.lunexa.feature.home.data

import com.twango.lunexa.core.network.api.LunexaApiService
import com.twango.lunexa.core.network.auth.AuthTokenStore
import com.twango.lunexa.core.network.dto.AccountDto
import com.twango.lunexa.core.network.dto.CategoryDto
import com.twango.lunexa.core.network.dto.CreateAccountRequest
import com.twango.lunexa.core.network.dto.CreateBudgetRequest
import com.twango.lunexa.core.network.dto.CreateTransactionRequest
import com.twango.lunexa.core.network.dto.MonthlySummaryDto
import javax.inject.Inject

class HomeRepository @Inject constructor(
    private val apiService: LunexaApiService,
    private val tokenStore: AuthTokenStore
) {
    suspend fun accounts(): List<AccountDto> = apiService.getAccounts().data

    suspend fun expenseCategories(): List<CategoryDto> = apiService.getCategories("EXPENSE").data

    suspend fun monthlySummary(month: String): MonthlySummaryDto =
        apiService.getMonthlySummary(month).data

    suspend fun createAccount(name: String, openingBalance: String): AccountDto =
        apiService.createAccount(
            CreateAccountRequest(
                name = name,
                type = "CASH",
                openingBalance = openingBalance
            )
        ).data.account

    suspend fun createExpense(
        accountId: String,
        categoryId: String,
        amount: String,
        merchant: String,
        note: String,
        transactionDate: String
    ) {
        apiService.createTransaction(
            CreateTransactionRequest(
                accountId = accountId,
                categoryId = categoryId,
                type = "EXPENSE",
                amount = amount,
                note = note.ifBlank { null },
                merchant = merchant.ifBlank { null },
                transactionDate = transactionDate
            )
        )
    }

    suspend fun createBudget(categoryId: String, periodMonth: String, amount: String) {
        apiService.createBudget(
            CreateBudgetRequest(
                categoryId = categoryId,
                periodMonth = periodMonth,
                amount = amount
            )
        )
    }

    fun logout() {
        tokenStore.clear()
    }
}
