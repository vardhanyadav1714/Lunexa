package com.twango.lunexa.feature.home.presentation

import com.twango.lunexa.core.network.dto.AccountDto
import com.twango.lunexa.core.network.dto.BudgetSummaryDto
import com.twango.lunexa.core.network.dto.CategoryDto
import com.twango.lunexa.core.network.dto.MonthlySummaryDto
import com.twango.lunexa.feature.home.data.HomeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.Calendar
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private lateinit var repository: HomeRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val mockAccounts = listOf(
        AccountDto(
            id = "acc-1",
            name = "Cash",
            type = "CASH",
            currency = "INR",
            openingBalance = "1000.00",
            currentBalance = "1500.00",
            isArchived = false,
            sortOrder = 0,
            createdAt = null,
            updatedAt = null,
            version = 1
        ),
        AccountDto(
            id = "acc-2",
            name = "Bank",
            type = "BANK",
            currency = "INR",
            openingBalance = "10000.00",
            currentBalance = "9500.00",
            isArchived = false,
            sortOrder = 1,
            createdAt = null,
            updatedAt = null,
            version = 1
        )
    )

    private val createdAccount = AccountDto(
        id = "acc-new",
        name = "New Account",
        type = "CASH",
        currency = "INR",
        openingBalance = "5000.00",
        currentBalance = "5000.00",
        isArchived = false,
        sortOrder = 2,
        createdAt = null,
        updatedAt = null,
        version = 1
    )

    private val mockCategories = listOf(
        CategoryDto(
            id = "cat-1",
            name = "Food",
            type = "EXPENSE",
            iconKey = "food",
            colorHex = "#FF5722",
            isDefault = true,
            sortOrder = 0,
            createdAt = null,
            updatedAt = null,
            version = 1
        ),
        CategoryDto(
            id = "cat-2",
            name = "Transport",
            type = "EXPENSE",
            iconKey = "transport",
            colorHex = "#2196F3",
            isDefault = false,
            sortOrder = 1,
            createdAt = null,
            updatedAt = null,
            version = 1
        )
    )

    private val mockSummary = MonthlySummaryDto(
        month = "2025-01",
        currency = "INR",
        incomeTotal = "50000.00",
        expenseTotal = "32000.50",
        transferTotal = "0.00",
        netCashflow = "18000.50",
        transactionCount = 45,
        largestExpense = null,
        budgetSummary = BudgetSummaryDto(
            budgetedAmount = "40000.00",
            spentAmount = "32000.50",
            remainingAmount = "7999.50",
            utilizationPercent = "80.00"
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        every { repository.logout() } returns Unit
        viewModel = HomeViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============== Input Field Tests ==============

    @Test
    fun `onAccountNameChange updates account name`() {
        viewModel.onAccountNameChange("My Savings")

        assertEquals("My Savings", viewModel.uiState.value.accountName)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onOpeningBalanceChange updates opening balance`() {
        viewModel.onOpeningBalanceChange("5000.00")

        assertEquals("5000.00", viewModel.uiState.value.openingBalance)
    }

    @Test
    fun `onTransactionAmountChange updates transaction amount`() {
        viewModel.onTransactionAmountChange("100.50")

        assertEquals("100.50", viewModel.uiState.value.transactionAmount)
    }

    @Test
    fun `onMerchantChange updates merchant`() {
        viewModel.onMerchantChange("Amazon")

        assertEquals("Amazon", viewModel.uiState.value.merchant)
    }

    @Test
    fun `onNoteChange updates note`() {
        viewModel.onNoteChange("Monthly subscription")

        assertEquals("Monthly subscription", viewModel.uiState.value.note)
    }

    @Test
    fun `onBudgetAmountChange updates budget amount`() {
        viewModel.onBudgetAmountChange("5000.00")

        assertEquals("5000.00", viewModel.uiState.value.budgetAmount)
    }

    // ============== Load Dashboard Tests ==============

    @Test
    fun `load successfully fetches accounts, categories, and summary`() = runTest {
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.load()

        coVerify { repository.accounts() }
        coVerify { repository.expenseCategories() }
        coVerify { repository.monthlySummary(any()) }

        assertEquals(mockAccounts, viewModel.uiState.value.accounts)
        assertEquals(mockCategories, viewModel.uiState.value.categories)
        assertEquals(mockSummary, viewModel.uiState.value.summary)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `load selects first active account and first expense category by default`() = runTest {
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.load()

        assertEquals("acc-1", viewModel.uiState.value.selectedExpenseAccountId)
        assertEquals("cat-1", viewModel.uiState.value.selectedExpenseCategoryId)
        assertEquals("cat-1", viewModel.uiState.value.selectedBudgetCategoryId)
    }

    @Test
    fun `load sets loading state correctly`() = runTest {
        coEvery { repository.accounts() } coAnswers { delay(100); mockAccounts }
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.load()

        // After starting the coroutine, isLoading should be true
        // Then after completion, it should be false
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `load handles error gracefully`() = runTest {
        coEvery { repository.accounts() } throws Exception("Network error")

        viewModel.load()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.message)
    }

    // ============== Create Account Tests ==============

    @Test
    fun `createAccount with blank name shows error`() {
        viewModel.onAccountNameChange("")
        viewModel.onOpeningBalanceChange("1000.00")
        viewModel.createAccount()

        assertEquals("Enter an account name.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createAccount with invalid balance shows error`() {
        viewModel.onAccountNameChange("My Account")
        viewModel.onOpeningBalanceChange("invalid")
        viewModel.createAccount()

        assertEquals("Enter a valid opening balance.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createAccount with duplicate active account name shows error and skips repository`() {
        viewModel.apply {
            _uiState.value = _uiState.value.copy(accounts = mockAccounts)
        }

        viewModel.onAccountNameChange("cash")
        viewModel.onOpeningBalanceChange("5000.00")
        viewModel.createAccount()

        assertEquals("An account with this name already exists.", viewModel.uiState.value.errorMessage)
        coVerify(exactly = 0) { repository.createAccount(any(), any()) }
    }

    @Test
    fun `createAccount with existing different account succeeds and selects created account`() = runTest {
        val walletAccount = createdAccount.copy(name = "Wallet")
        coEvery { repository.createAccount(any(), any()) } returns walletAccount
        coEvery { repository.accounts() } returns mockAccounts + walletAccount
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.apply {
            _uiState.value = _uiState.value.copy(accounts = mockAccounts)
        }
        viewModel.onAccountNameChange("Wallet")
        viewModel.onOpeningBalanceChange("5000.00")
        viewModel.createAccount()

        coVerify { repository.createAccount("Wallet", "5000.00") }
        assertEquals("Account created.", viewModel.uiState.value.message)
        assertEquals("acc-new", viewModel.uiState.value.selectedExpenseAccountId)
        assertEquals(mockAccounts + walletAccount, viewModel.uiState.value.accounts)
    }

    @Test
    fun `createAccount with valid data succeeds`() = runTest {
        coEvery { repository.createAccount(any(), any()) } returns createdAccount
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.onAccountNameChange("New Account")
        viewModel.onOpeningBalanceChange("5000.00")
        viewModel.createAccount()

        coVerify { repository.createAccount("New Account", "5000.00") }
        assertEquals("Account created.", viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("", viewModel.uiState.value.accountName)
        assertEquals("", viewModel.uiState.value.openingBalance)
    }

    @Test
    fun `createAccount handles negative balance (allowed for opening)`() = runTest {
        coEvery { repository.createAccount(any(), any()) } returns createdAccount
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.onAccountNameChange("Loan Account")
        viewModel.onOpeningBalanceChange("-5000.00")
        viewModel.createAccount()

        coVerify { repository.createAccount("Loan Account", "-5000.00") }
    }

    @Test
    fun `createAccount handles zero balance (allowed)`() = runTest {
        coEvery { repository.createAccount(any(), any()) } returns createdAccount
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.onAccountNameChange("Empty Account")
        viewModel.onOpeningBalanceChange("0.00")
        viewModel.createAccount()

        coVerify { repository.createAccount("Empty Account", "0.00") }
    }

    @Test
    fun `createAccount rejects invalid decimal places`() {
        viewModel.onAccountNameChange("My Account")
        viewModel.onOpeningBalanceChange("100.123")
        viewModel.createAccount()

        assertEquals("Enter a valid opening balance.", viewModel.uiState.value.errorMessage)
    }

    // ============== Create Expense Tests ==============

    @Test
    fun `createExpense with no account shows error`() {
        // Empty state - no accounts
        viewModel.onTransactionAmountChange("100.00")
        viewModel.createExpense()

        assertEquals("Create an account first.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createExpense with no category shows error`() = runTest {
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns emptyList()
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.load()

        viewModel.onTransactionAmountChange("100.00")
        viewModel.createExpense()

        assertEquals("No expense category found for this user.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createExpense with invalid amount shows error`() = runTest {
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.load()

        viewModel.onTransactionAmountChange("invalid")
        viewModel.createExpense()

        assertEquals("Enter a valid expense amount.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createExpense with zero amount shows error`() = runTest {
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.load()

        viewModel.onTransactionAmountChange("0.00")
        viewModel.createExpense()

        assertEquals("Enter a valid expense amount.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createExpense with valid data succeeds`() = runTest {
        coEvery { repository.createExpense(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.onTransactionAmountChange("150.50")
        viewModel.onMerchantChange("Amazon")
        viewModel.onNoteChange("Books")

        // Manually set up the state with accounts and categories
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.createExpense()

        coVerify {
            repository.createExpense(
                accountId = "acc-1",
                categoryId = "cat-1",
                amount = "150.50",
                merchant = "Amazon",
                note = "Books",
                transactionDate = any()
            )
        }
        assertEquals("Expense added.", viewModel.uiState.value.message)
        assertEquals("", viewModel.uiState.value.transactionAmount)
        assertEquals("", viewModel.uiState.value.merchant)
        assertEquals("", viewModel.uiState.value.note)
    }

    @Test
    fun `createExpense uses selected account and selected category`() = runTest {
        coEvery { repository.createExpense(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }
        viewModel.onExpenseAccountSelected("acc-2")
        viewModel.onExpenseCategorySelected("cat-2")
        viewModel.onTransactionAmountChange("150.50")
        viewModel.createExpense()

        coVerify {
            repository.createExpense(
                accountId = "acc-2",
                categoryId = "cat-2",
                amount = "150.50",
                merchant = "",
                note = "",
                transactionDate = any()
            )
        }
    }

    // ============== Create Budget Tests ==============

    @Test
    fun `createBudget with no category shows error`() {
        viewModel.onBudgetAmountChange("5000.00")
        viewModel.createBudget()

        assertEquals("No expense category found for this user.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createBudget with invalid amount shows error`() = runTest {
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.load()

        viewModel.onBudgetAmountChange("invalid")
        viewModel.createBudget()

        assertEquals("Enter a valid budget amount.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createBudget with zero amount shows error`() = runTest {
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.load()

        viewModel.onBudgetAmountChange("0.00")
        viewModel.createBudget()

        assertEquals("Enter a valid budget amount.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createBudget with valid data succeeds`() = runTest {
        coEvery { repository.createBudget(any(), any(), any()) } returns Unit
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.onBudgetAmountChange("10000.00")

        // Manually set up the state with categories
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.createBudget()

        coVerify {
            repository.createBudget(
                categoryId = "cat-1",
                periodMonth = match { it.matches(Regex("\\d{4}-\\d{2}-01")) },
                amount = "10000.00"
            )
        }
        assertEquals("Budget created.", viewModel.uiState.value.message)
        assertEquals("", viewModel.uiState.value.budgetAmount)
    }

    @Test
    fun `createBudget uses selected budget category`() = runTest {
        coEvery { repository.createBudget(any(), any(), any()) } returns Unit
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }
        viewModel.onBudgetCategorySelected("cat-2")
        viewModel.onBudgetAmountChange("7500.00")
        viewModel.createBudget()

        coVerify {
            repository.createBudget(
                categoryId = "cat-2",
                periodMonth = match { it.matches(Regex("\\d{4}-\\d{2}-01")) },
                amount = "7500.00"
            )
        }
    }

    // ============== Logout Tests ==============

    @Test
    fun `logout clears tokens and sets logged out flag`() {
        viewModel.logout()

        verify { repository.logout() }
        assertTrue(viewModel.uiState.value.isLoggedOut)
    }

    // ============== Money Validation Tests ==============

    @Test
    fun `money validation accepts positive amount with 2 decimals`() = runTest {
        coEvery { repository.createExpense(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.onTransactionAmountChange("123.45")
        viewModel.createExpense()

        coVerify { repository.createExpense(any(), any(), "123.45", any(), any(), any()) }
    }

    @Test
    fun `money validation accepts integer amount`() = runTest {
        coEvery { repository.createExpense(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.onTransactionAmountChange("100")
        viewModel.createExpense()

        coVerify { repository.createExpense(any(), any(), "100", any(), any(), any()) }
    }

    @Test
    fun `money validation rejects amount with more than 2 decimals`() {
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.onTransactionAmountChange("100.123")
        viewModel.createExpense()

        assertEquals("Enter a valid expense amount.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `money validation rejects negative amount for expense`() {
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.onTransactionAmountChange("-50.00")
        viewModel.createExpense()

        assertEquals("Enter a valid expense amount.", viewModel.uiState.value.errorMessage)
    }

    // ============== Error Recovery Tests ==============

    @Test
    fun `successful action clears previous error message`() = runTest {
        coEvery { repository.createAccount(any(), any()) } returns createdAccount
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        // Set an error
        viewModel.onAccountNameChange("")
        viewModel.onOpeningBalanceChange("1000.00")
        viewModel.createAccount()

        assertEquals("Enter an account name.", viewModel.uiState.value.errorMessage)

        // Now do a successful action
        viewModel.onAccountNameChange("Valid Account")
        viewModel.createAccount()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("Account created.", viewModel.uiState.value.message)
    }

    @Test
    fun `input changes clear error messages`() {
        viewModel.onAccountNameChange("")
        viewModel.onOpeningBalanceChange("1000.00")
        viewModel.createAccount()

        assertEquals("Enter an account name.", viewModel.uiState.value.errorMessage)

        // Change input should clear error
        viewModel.onAccountNameChange("New Account")

        assertNull(viewModel.uiState.value.errorMessage)
    }

    // ============== Date Formatting Tests ==============

    @Test
    fun `load uses correct month format`() = runTest {
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.load()

        coVerify { repository.monthlySummary(match { it.matches(Regex("\\d{4}-\\d{2}")) }) }
    }

    @Test
    fun `createExpense uses correct date format`() = runTest {
        coEvery { repository.createExpense(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.onTransactionAmountChange("100.00")
        viewModel.createExpense()

        coVerify {
            repository.createExpense(
                any(),
                any(),
                any(),
                any(),
                any(),
                transactionDate = match { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            )
        }
    }

    @Test
    fun `createBudget uses correct date format`() = runTest {
        coEvery { repository.createBudget(any(), any(), any()) } returns Unit
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.onBudgetAmountChange("5000.00")
        viewModel.createBudget()

        coVerify {
            repository.createBudget(
                any(),
                periodMonth = match { it.matches(Regex("\\d{4}-\\d{2}-01")) },
                any()
            )
        }
    }

    // ============== Action Error Handling Tests ==============

    @Test
    fun `createAccount handles repository error`() = runTest {
        coEvery { repository.createAccount(any(), any()) } throws Exception("API Error")

        viewModel.onAccountNameChange("Test Account")
        viewModel.onOpeningBalanceChange("1000.00")
        viewModel.createAccount()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `createExpense handles repository error`() = runTest {
        coEvery { repository.createExpense(any(), any(), any(), any(), any(), any()) } throws Exception("API Error")
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.onTransactionAmountChange("100.00")
        viewModel.createExpense()

        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createBudget handles repository error`() = runTest {
        coEvery { repository.createBudget(any(), any(), any()) } throws Exception("API Error")
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                accounts = mockAccounts,
                categories = mockCategories
            )
        }

        viewModel.onBudgetAmountChange("5000.00")
        viewModel.createBudget()

        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    // ============== State Consistency Tests ==============

    @Test
    fun `successful action reloads dashboard data`() = runTest {
        coEvery { repository.createAccount(any(), any()) } returns createdAccount
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.onAccountNameChange("New Account")
        viewModel.onOpeningBalanceChange("5000.00")
        viewModel.createAccount()

        // Verify dashboard was reloaded after successful creation
        assertEquals(mockAccounts, viewModel.uiState.value.accounts)
        assertEquals(mockCategories, viewModel.uiState.value.categories)
        assertEquals(mockSummary, viewModel.uiState.value.summary)
    }

    @Test
    fun `trimming whitespace from account name`() = runTest {
        coEvery { repository.createAccount(any(), any()) } returns createdAccount
        coEvery { repository.accounts() } returns mockAccounts
        coEvery { repository.expenseCategories() } returns mockCategories
        coEvery { repository.monthlySummary(any()) } returns mockSummary

        viewModel.onAccountNameChange("  My Account  ")
        viewModel.onOpeningBalanceChange("5000.00")
        viewModel.createAccount()

        coVerify { repository.createAccount("My Account", "5000.00") }
    }
}

private suspend fun delay(timeMillis: Long) {
    kotlinx.coroutines.delay(timeMillis)
}
