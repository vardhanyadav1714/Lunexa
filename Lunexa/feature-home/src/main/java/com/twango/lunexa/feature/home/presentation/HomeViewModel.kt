package com.twango.lunexa.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twango.lunexa.core.network.toApiMessage
import com.twango.lunexa.core.network.dto.AccountDto
import com.twango.lunexa.core.network.dto.CategoryDto
import com.twango.lunexa.core.network.dto.MonthlySummaryDto
import com.twango.lunexa.feature.home.data.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val accounts: List<AccountDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val summary: MonthlySummaryDto? = null,
    val accountName: String = "",
    val openingBalance: String = "",
    val transactionAmount: String = "",
    val merchant: String = "",
    val note: String = "",
    val budgetAmount: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }

            runCatching {
                val accounts = repository.accounts()
                val categories = repository.expenseCategories()
                val summary = repository.monthlySummary(currentMonth())

                _uiState.update {
                    it.copy(
                        accounts = accounts,
                        categories = categories,
                        summary = summary,
                        isLoading = false
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.toApiMessage("Unable to load dashboard.")
                    )
                }
            }
        }
    }

    fun onAccountNameChange(value: String) {
        _uiState.update { it.copy(accountName = value, errorMessage = null, message = null) }
    }

    fun onOpeningBalanceChange(value: String) {
        _uiState.update { it.copy(openingBalance = value, errorMessage = null, message = null) }
    }

    fun onTransactionAmountChange(value: String) {
        _uiState.update { it.copy(transactionAmount = value, errorMessage = null, message = null) }
    }

    fun onMerchantChange(value: String) {
        _uiState.update { it.copy(merchant = value, errorMessage = null, message = null) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value, errorMessage = null, message = null) }
    }

    fun onBudgetAmountChange(value: String) {
        _uiState.update { it.copy(budgetAmount = value, errorMessage = null, message = null) }
    }

    fun createAccount() {
        val state = _uiState.value
        if (state.accountName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter an account name.") }
            return
        }
        if (!isMoneyValue(state.openingBalance, allowZeroOrNegative = true)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid opening balance.") }
            return
        }

        viewModelScope.launch {
            runAction("Account created.") {
                repository.createAccount(
                    name = state.accountName.trim(),
                    openingBalance = state.openingBalance.trim()
                )
            }
        }
    }

    fun createExpense() {
        val state = _uiState.value
        val account = state.accounts.firstOrNull()
        val category = state.categories.firstOrNull()

        if (account == null) {
            _uiState.update { it.copy(errorMessage = "Create an account first.") }
            return
        }
        if (category == null) {
            _uiState.update { it.copy(errorMessage = "No expense category found for this user.") }
            return
        }
        if (!isMoneyValue(state.transactionAmount)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid expense amount.") }
            return
        }

        viewModelScope.launch {
            runAction("Expense added.") {
                repository.createExpense(
                    accountId = account.id,
                    categoryId = category.id,
                    amount = state.transactionAmount.trim(),
                    merchant = state.merchant.trim(),
                    note = state.note.trim(),
                    transactionDate = today()
                )
            }
        }
    }

    fun createBudget() {
        val state = _uiState.value
        val category = state.categories.firstOrNull()
        if (category == null) {
            _uiState.update { it.copy(errorMessage = "No expense category found for this user.") }
            return
        }
        if (!isMoneyValue(state.budgetAmount)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid budget amount.") }
            return
        }

        viewModelScope.launch {
            runAction("Budget created.") {
                repository.createBudget(
                    categoryId = category.id,
                    periodMonth = "${currentMonth()}-01",
                    amount = state.budgetAmount.trim()
                )
            }
        }
    }

    fun logout() {
        repository.logout()
        _uiState.update { it.copy(isLoggedOut = true) }
    }

    private suspend fun runAction(successMessage: String, action: suspend () -> Unit) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }

        runCatching {
            action()
        }.onSuccess {
            val accounts = repository.accounts()
            val categories = repository.expenseCategories()
            val summary = repository.monthlySummary(currentMonth())

            _uiState.update {
                it.copy(
                    accounts = accounts,
                    categories = categories,
                    summary = summary,
                    isLoading = false,
                    message = successMessage
                )
            }
        }.onFailure { throwable ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = throwable.toApiMessage("Unable to complete action.")
                )
            }
        }
    }

    private fun isMoneyValue(value: String, allowZeroOrNegative: Boolean = false): Boolean {
        val amount = value.trim().toBigDecimalOrNull() ?: return false
        return if (allowZeroOrNegative) {
            amount.scale() <= 2
        } else {
            amount > BigDecimal.ZERO && amount.scale() <= 2
        }
    }

    private fun currentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
