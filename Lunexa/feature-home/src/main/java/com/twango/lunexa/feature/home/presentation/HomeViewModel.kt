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
    val selectedExpenseAccountId: String? = null,
    val selectedExpenseCategoryId: String? = null,
    val selectedBudgetCategoryId: String? = null,
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
    internal val _uiState = MutableStateFlow(HomeUiState())
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
                    ).withValidSelections()
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

    fun onExpenseAccountSelected(accountId: String) {
        _uiState.update { state ->
            if (state.activeAccounts().any { it.id == accountId }) {
                state.copy(selectedExpenseAccountId = accountId, errorMessage = null, message = null)
            } else {
                state.copy(errorMessage = "Selected account is no longer available.", message = null)
            }
        }
    }

    fun onExpenseCategorySelected(categoryId: String) {
        _uiState.update { state ->
            if (state.expenseCategories().any { it.id == categoryId }) {
                state.copy(selectedExpenseCategoryId = categoryId, errorMessage = null, message = null)
            } else {
                state.copy(errorMessage = "Selected category is no longer available.", message = null)
            }
        }
    }

    fun onBudgetCategorySelected(categoryId: String) {
        _uiState.update { state ->
            if (state.expenseCategories().any { it.id == categoryId }) {
                state.copy(selectedBudgetCategoryId = categoryId, errorMessage = null, message = null)
            } else {
                state.copy(errorMessage = "Selected category is no longer available.", message = null)
            }
        }
    }

    fun createAccount() {
        val state = _uiState.value
        if (state.accountName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter an account name.") }
            return
        }
        if (state.accounts.any { it.name.equals(state.accountName.trim(), ignoreCase = true) && !it.isArchived }) {
            _uiState.update { it.copy(errorMessage = "An account with this name already exists.") }
            return
        }
        if (!isMoneyValue(state.openingBalance, allowZeroOrNegative = true)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid opening balance.") }
            return
        }

        viewModelScope.launch {
            var createdAccountId: String? = null
            runAction(
                successMessage = "Account created.",
                clearInputs = { it.copy(accountName = "", openingBalance = "") },
                afterReload = { reloaded ->
                    val selectedId = createdAccountId
                        ?.takeIf { accountId -> reloaded.activeAccounts().any { it.id == accountId } }
                    if (selectedId == null) {
                        reloaded
                    } else {
                        reloaded.copy(selectedExpenseAccountId = selectedId)
                    }
                },
                action = {
                    val createdAccount = repository.createAccount(
                        name = state.accountName.trim(),
                        openingBalance = state.openingBalance.trim()
                    )
                    createdAccountId = createdAccount.id
                }
            )
        }
    }

    fun createExpense() {
        val state = _uiState.value
        val account = state.selectedExpenseAccount()
        val category = state.selectedExpenseCategory()

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
            runAction(
                successMessage = "Expense added.",
                clearInputs = {
                    it.copy(
                        transactionAmount = "",
                        merchant = "",
                        note = ""
                    )
                },
                action = {
                    repository.createExpense(
                        accountId = account.id,
                        categoryId = category.id,
                        amount = state.transactionAmount.trim(),
                        merchant = state.merchant.trim(),
                        note = state.note.trim(),
                        transactionDate = today()
                    )
                }
            )
        }
    }

    fun createBudget() {
        val state = _uiState.value
        val category = state.selectedBudgetCategory()
        if (category == null) {
            _uiState.update { it.copy(errorMessage = "No expense category found for this user.") }
            return
        }
        if (!isMoneyValue(state.budgetAmount)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid budget amount.") }
            return
        }

        viewModelScope.launch {
            runAction(
                successMessage = "Budget created.",
                clearInputs = { it.copy(budgetAmount = "") },
                action = {
                    repository.createBudget(
                        categoryId = category.id,
                        periodMonth = "${currentMonth()}-01",
                        amount = state.budgetAmount.trim()
                    )
                }
            )
        }
    }

    fun logout() {
        repository.logout()
        _uiState.update { it.copy(isLoggedOut = true) }
    }

    private suspend fun runAction(
        successMessage: String,
        clearInputs: (HomeUiState) -> HomeUiState = { it },
        afterReload: (HomeUiState) -> HomeUiState = { it },
        action: suspend () -> Unit
    ) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }

        runCatching {
            action()
        }.onSuccess {
            val accounts = repository.accounts()
            val categories = repository.expenseCategories()
            val summary = repository.monthlySummary(currentMonth())

            _uiState.update {
                val reloaded = clearInputs(it).copy(
                    accounts = accounts,
                    categories = categories,
                    summary = summary,
                    isLoading = false,
                    message = successMessage,
                    errorMessage = null
                ).withValidSelections()
                afterReload(reloaded).withValidSelections()
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

private fun HomeUiState.withValidSelections(): HomeUiState {
    val activeAccounts = activeAccounts()
    val expenseCategories = expenseCategories()
    return copy(
        selectedExpenseAccountId = selectedExpenseAccountId
            ?.takeIf { selectedId -> activeAccounts.any { it.id == selectedId } }
            ?: activeAccounts.firstOrNull()?.id,
        selectedExpenseCategoryId = selectedExpenseCategoryId
            ?.takeIf { selectedId -> expenseCategories.any { it.id == selectedId } }
            ?: expenseCategories.firstOrNull()?.id,
        selectedBudgetCategoryId = selectedBudgetCategoryId
            ?.takeIf { selectedId -> expenseCategories.any { it.id == selectedId } }
            ?: expenseCategories.firstOrNull()?.id
    )
}

private fun HomeUiState.selectedExpenseAccount(): AccountDto? =
    activeAccounts().firstOrNull { it.id == selectedExpenseAccountId } ?: activeAccounts().firstOrNull()

private fun HomeUiState.selectedExpenseCategory(): CategoryDto? =
    expenseCategories().firstOrNull { it.id == selectedExpenseCategoryId } ?: expenseCategories().firstOrNull()

private fun HomeUiState.selectedBudgetCategory(): CategoryDto? =
    expenseCategories().firstOrNull { it.id == selectedBudgetCategoryId } ?: expenseCategories().firstOrNull()

private fun HomeUiState.activeAccounts(): List<AccountDto> =
    accounts
        .filterNot { it.isArchived }
        .sortedWith(compareBy<AccountDto> { it.sortOrder }.thenBy { it.name.lowercase(Locale.US) })

private fun HomeUiState.expenseCategories(): List<CategoryDto> =
    categories
        .filter { it.type.equals("EXPENSE", ignoreCase = true) }
        .sortedWith(compareBy<CategoryDto> { it.sortOrder }.thenBy { it.name.lowercase(Locale.US) })
