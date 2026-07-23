package com.twango.lunexa.feature.auth.presentation

import com.twango.lunexa.core.network.dto.EmailVerificationPayloadDto
import com.twango.lunexa.core.network.dto.PasswordResetPayloadDto
import com.twango.lunexa.feature.auth.data.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var repository: AuthRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = AuthViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============== Input Field Tests ==============

    @Test
    fun `onFullNameChange updates fullName and clears errors`() {
        viewModel.onFullNameChange("John Doe")

        assertEquals("John Doe", viewModel.uiState.value.fullName)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onFullNameChange clears verification state`() {
        viewModel.onFullNameChange("John")

        assertNull(viewModel.uiState.value.emailVerificationId)
        assertNull(viewModel.uiState.value.verificationEmail)
    }

    @Test
    fun `onEmailChange updates email and clears errors`() {
        viewModel.onEmailChange("john@lunexa.app")

        assertEquals("john@lunexa.app", viewModel.uiState.value.email)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onPasswordChange updates password and clears errors`() {
        viewModel.onPasswordChange("Password123!")

        assertEquals("Password123!", viewModel.uiState.value.password)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onNewPasswordChange updates newPassword and clears errors`() {
        viewModel.onNewPasswordChange("NewPassword123!")

        assertEquals("NewPassword123!", viewModel.uiState.value.newPassword)
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.infoMessage)
    }

    @Test
    fun `onConfirmNewPasswordChange updates confirmNewPassword`() {
        viewModel.onConfirmNewPasswordChange("NewPassword123!")

        assertEquals("NewPassword123!", viewModel.uiState.value.confirmNewPassword)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onVerificationCodeChange filters to digits only and limits to 6`() {
        viewModel.onVerificationCodeChange("a1b2c3d4")

        assertEquals("1234", viewModel.uiState.value.verificationCode)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onVerificationCodeChange limits to 6 digits`() {
        viewModel.onVerificationCodeChange("1234567")

        assertEquals("123456", viewModel.uiState.value.verificationCode)
    }

    @Test
    fun `onPasswordResetCodeChange filters to digits only and limits to 6`() {
        viewModel.onPasswordResetCodeChange("x9y8z7w6")

        assertEquals("9876", viewModel.uiState.value.passwordResetCode)
    }

    // ============== Mode Toggle Tests ==============

    @Test
    fun `toggleMode switches between login and register mode`() {
        assertFalse(viewModel.uiState.value.isRegisterMode)

        viewModel.toggleMode()

        assertTrue(viewModel.uiState.value.isRegisterMode)

        viewModel.toggleMode()

        assertFalse(viewModel.uiState.value.isRegisterMode)
    }

    @Test
    fun `toggleMode clears all verification state`() {
        viewModel.apply {
            onFullNameChange("John")
            onEmailChange("john@lunexa.app")
        }

        viewModel.toggleMode()

        assertEquals("", viewModel.uiState.value.fullName)
        assertEquals("", viewModel.uiState.value.password)
        assertNull(viewModel.uiState.value.emailVerificationId)
    }

    @Test
    fun `showPasswordReset sets password reset mode`() {
        viewModel.showPasswordReset()

        assertTrue(viewModel.uiState.value.isPasswordResetMode)
        assertFalse(viewModel.uiState.value.isRegisterMode)
    }

    @Test
    fun `showSignIn clears all modes`() {
        viewModel.showPasswordReset()
        viewModel.toggleMode()

        viewModel.showSignIn()

        assertFalse(viewModel.uiState.value.isRegisterMode)
        assertFalse(viewModel.uiState.value.isPasswordResetMode)
    }

    // ============== Validation Tests ==============

    @Test
    fun `submit in login mode with invalid email shows error`() {
        viewModel.onEmailChange("invalid-email")
        viewModel.onPasswordChange("Password123!")
        viewModel.submit()

        assertEquals("Enter a valid email address.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in login mode with short password shows error`() {
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("short")
        viewModel.submit()

        assertEquals("Password must be at least 8 characters.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in register mode with short name shows error`() {
        viewModel.toggleMode()
        viewModel.onFullNameChange("J")
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("Password123!")
        viewModel.submit()

        assertEquals("Enter your real full name.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in register mode with blocked name shows error`() {
        viewModel.toggleMode()
        viewModel.onFullNameChange("admin")
        viewModel.onEmailChange("admin@lunexa.app")
        viewModel.onPasswordChange("Password123!")
        viewModel.submit()

        assertEquals("Enter your real full name.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in register mode with blocked email domain shows error`() {
        viewModel.toggleMode()
        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@mailinator.com")
        viewModel.onPasswordChange("Password123!")
        viewModel.submit()

        assertEquals("Use a real inbox that you can access.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in register mode with weak password shows error`() {
        viewModel.toggleMode()
        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("weak")
        viewModel.submit()

        assertEquals("Use at least 10 characters for your password.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in register mode with password missing lowercase shows error`() {
        viewModel.toggleMode()
        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("PASSWORD123!")
        viewModel.submit()

        assertEquals("Password needs a lowercase letter.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in register mode with password missing uppercase shows error`() {
        viewModel.toggleMode()
        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("password123!")
        viewModel.submit()

        assertEquals("Password needs an uppercase letter.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in register mode with password missing digit shows error`() {
        viewModel.toggleMode()
        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("Password!!")
        viewModel.submit()

        assertEquals("Password needs a number.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in register mode with password missing symbol shows error`() {
        viewModel.toggleMode()
        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("Password123")
        viewModel.submit()

        assertEquals("Password needs a symbol.", viewModel.uiState.value.errorMessage)
    }

    // ============== Email Verification Tests ==============

    @Test
    fun `submit in register mode with valid data starts verification`() = runTest {
        coEvery {
            repository.startEmailVerification(any(), any(), any())
        } returns EmailVerificationPayloadDto(
            verificationId = "verify-123",
            email = "john@lunexa.app",
            expiresInSeconds = 600
        )

        viewModel.toggleMode()
        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("Password123!")
        viewModel.submit()

        coVerify { repository.startEmailVerification("John Doe", "john@lunexa.app", "Password123!") }
        assertEquals("verify-123", viewModel.uiState.value.emailVerificationId)
        assertEquals("john@lunexa.app", viewModel.uiState.value.verificationEmail)
    }

    @Test
    fun `submit in register mode with verification code calls register`() = runTest {
        coEvery { repository.register(any(), any(), any(), any(), any()) } returns Unit

        viewModel.toggleMode()
        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("Password123!")

        // Set verification state manually
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                emailVerificationId = "verify-123",
                verificationEmail = "john@lunexa.app"
            )
        }

        viewModel.onVerificationCodeChange("123456")
        viewModel.submit()

        coVerify {
            repository.register(
                fullName = "John Doe",
                email = "john@lunexa.app",
                password = "Password123!",
                emailVerificationId = "verify-123",
                emailVerificationCode = "123456"
            )
        }
    }

    @Test
    fun `submit in register mode with invalid verification code shows error`() {
        viewModel.toggleMode()
        viewModel.onFullNameChange("John Doe")
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("Password123!")
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                emailVerificationId = "verify-123",
                verificationEmail = "john@lunexa.app"
            )
        }

        viewModel.onVerificationCodeChange("123")
        viewModel.submit()

        assertEquals("Enter the 6-digit code sent to your email.", viewModel.uiState.value.errorMessage)
    }

    // ============== Login Tests ==============

    @Test
    fun `submit in login mode with valid credentials calls login`() = runTest {
        coEvery { repository.login(any(), any()) } returns Unit

        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("Password123!")
        viewModel.submit()

        coVerify { repository.login("john@lunexa.app", "Password123!") }
    }

    // ============== Password Reset Tests ==============

    @Test
    fun `submit in password reset mode with invalid email shows error`() {
        viewModel.showPasswordReset()
        viewModel.onEmailChange("invalid-email")
        viewModel.submit()

        assertEquals("Enter the email on your Lunexa account.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `submit in password reset mode starts reset flow`() = runTest {
        coEvery {
            repository.startPasswordReset(any())
        } returns PasswordResetPayloadDto(
            resetId = "reset-123",
            email = "john@lunexa.app",
            expiresInSeconds = 600,
            message = "Reset code sent"
        )

        viewModel.showPasswordReset()
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.submit()

        coVerify { repository.startPasswordReset("john@lunexa.app") }
        assertEquals("reset-123", viewModel.uiState.value.passwordResetId)
    }

    @Test
    fun `submit in password reset mode with reset code confirms reset`() = runTest {
        coEvery { repository.confirmPasswordReset(any(), any(), any(), any()) } returns Unit

        viewModel.showPasswordReset()
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                passwordResetId = "reset-123",
                passwordResetEmail = "john@lunexa.app"
            )
        }

        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordResetCodeChange("123456")
        viewModel.onNewPasswordChange("NewPassword123!")
        viewModel.onConfirmNewPasswordChange("NewPassword123!")
        viewModel.submit()

        coVerify {
            repository.confirmPasswordReset(
                resetId = "reset-123",
                email = "john@lunexa.app",
                code = "123456",
                newPassword = "NewPassword123!"
            )
        }
    }

    @Test
    fun `submit in password reset mode with mismatched passwords shows error`() {
        viewModel.showPasswordReset()
        viewModel.onEmailChange("john@lunexa.app")
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                passwordResetId = "reset-123",
                passwordResetEmail = "john@lunexa.app"
            )
        }

        viewModel.onPasswordResetCodeChange("123456")
        viewModel.onNewPasswordChange("NewPassword123!")
        viewModel.onConfirmNewPasswordChange("DifferentPassword123!")
        viewModel.submit()

        assertEquals("New passwords do not match.", viewModel.uiState.value.errorMessage)
    }

    // ============== Resend Tests ==============

    @Test
    fun `resendVerificationCode resends verification email`() = runTest {
        coEvery {
            repository.startEmailVerification(any(), any(), any())
        } returns EmailVerificationPayloadDto(
            verificationId = "verify-456",
            email = "jane@lunexa.app",
            expiresInSeconds = 600
        )

        viewModel.toggleMode()
        viewModel.onFullNameChange("Jane Doe")
        viewModel.onEmailChange("jane@lunexa.app")
        viewModel.onPasswordChange("Password123!")
        viewModel.resendVerificationCode()

        coVerify { repository.startEmailVerification("Jane Doe", "jane@lunexa.app", "Password123!") }
    }

    @Test
    fun `resendPasswordResetCode resends reset email`() = runTest {
        coEvery {
            repository.startPasswordReset(any())
        } returns PasswordResetPayloadDto(
            resetId = "reset-456",
            email = "jane@lunexa.app",
            expiresInSeconds = 600,
            message = "New reset code sent"
        )

        viewModel.showPasswordReset()
        viewModel.onEmailChange("jane@lunexa.app")
        viewModel.resendPasswordResetCode()

        coVerify { repository.startPasswordReset("jane@lunexa.app") }
    }

    // ============== State Computed Properties Tests ==============

    @Test
    fun `isAwaitingVerification returns true when in register mode with verification id`() {
        viewModel.toggleMode()
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                emailVerificationId = "verify-123"
            )
        }

        assertTrue(viewModel.uiState.value.isAwaitingVerification)
    }

    @Test
    fun `isAwaitingVerification returns false when not in register mode`() {
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                emailVerificationId = "verify-123"
            )
        }

        assertFalse(viewModel.uiState.value.isAwaitingVerification)
    }

    @Test
    fun `isAwaitingPasswordReset returns true when in password reset mode with reset id`() {
        viewModel.showPasswordReset()
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                passwordResetId = "reset-123"
            )
        }

        assertTrue(viewModel.uiState.value.isAwaitingPasswordReset)
    }

    @Test
    fun `isAwaitingPasswordReset returns false when not in password reset mode`() {
        viewModel.apply {
            _uiState.value = _uiState.value.copy(
                passwordResetId = "reset-123"
            )
        }

        assertFalse(viewModel.uiState.value.isAwaitingPasswordReset)
    }

    // ============== Error Handling Tests ==============

    @Test
    fun `submit handles repository error gracefully`() = runTest {
        coEvery { repository.login(any(), any()) } throws Exception("Network error")

        viewModel.onEmailChange("john@lunexa.app")
        viewModel.onPasswordChange("Password123!")
        viewModel.submit()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
