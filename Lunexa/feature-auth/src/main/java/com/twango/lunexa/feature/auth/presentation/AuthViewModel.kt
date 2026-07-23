package com.twango.lunexa.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twango.lunexa.core.network.toApiMessage
import com.twango.lunexa.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isRegisterMode: Boolean = false,
    val isPasswordResetMode: Boolean = false,
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val emailVerificationId: String? = null,
    val verificationEmail: String? = null,
    val verificationCode: String = "",
    val passwordResetId: String? = null,
    val passwordResetEmail: String? = null,
    val passwordResetCode: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val infoMessage: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false
) {
    val isAwaitingVerification: Boolean
        get() = isRegisterMode && emailVerificationId != null

    val isAwaitingPasswordReset: Boolean
        get() = isPasswordResetMode && passwordResetId != null
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    internal val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun onFullNameChange(value: String) {
        _uiState.update {
            it.copy(fullName = value, errorMessage = null).resetVerification()
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(email = value, errorMessage = null).resetVerification()
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(password = value, errorMessage = null).resetVerification()
        }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, errorMessage = null, infoMessage = null) }
    }

    fun onConfirmNewPasswordChange(value: String) {
        _uiState.update { it.copy(confirmNewPassword = value, errorMessage = null, infoMessage = null) }
    }

    fun onVerificationCodeChange(value: String) {
        _uiState.update {
            it.copy(
                verificationCode = value.filter(Char::isDigit).take(6),
                errorMessage = null
            )
        }
    }

    fun onPasswordResetCodeChange(value: String) {
        _uiState.update {
            it.copy(
                passwordResetCode = value.filter(Char::isDigit).take(6),
                errorMessage = null
            )
        }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                isRegisterMode = !it.isRegisterMode,
                isPasswordResetMode = false,
                fullName = "",
                password = "",
                emailVerificationId = null,
                verificationEmail = null,
                verificationCode = "",
                passwordResetId = null,
                passwordResetEmail = null,
                passwordResetCode = "",
                newPassword = "",
                confirmNewPassword = "",
                infoMessage = null,
                errorMessage = null
            )
        }
    }

    fun showPasswordReset() {
        _uiState.update {
            it.copy(
                isRegisterMode = false,
                isPasswordResetMode = true,
                password = "",
                emailVerificationId = null,
                verificationEmail = null,
                verificationCode = "",
                passwordResetId = null,
                passwordResetEmail = null,
                passwordResetCode = "",
                newPassword = "",
                confirmNewPassword = "",
                infoMessage = null,
                errorMessage = null
            )
        }
    }

    fun showSignIn() {
        _uiState.update {
            it.copy(
                isRegisterMode = false,
                isPasswordResetMode = false,
                password = "",
                emailVerificationId = null,
                verificationEmail = null,
                verificationCode = "",
                passwordResetId = null,
                passwordResetEmail = null,
                passwordResetCode = "",
                newPassword = "",
                confirmNewPassword = "",
                infoMessage = null,
                errorMessage = null
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isPasswordResetMode) {
            submitPasswordReset(state)
            return
        }

        val validationError = if (state.isRegisterMode) {
            validateRegistration(state)
        } else {
            validateLogin(state)
        }

        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError, infoMessage = null) }
            return
        }

        if (state.isRegisterMode && state.emailVerificationId == null) {
            startVerification(state)
            return
        }

        if (state.isRegisterMode && !state.verificationCode.matches(VERIFICATION_CODE_REGEX)) {
            _uiState.update { it.copy(errorMessage = "Enter the 6-digit code sent to your email.") }
            return
        }

        authenticate(state)
    }

    private fun submitPasswordReset(state: AuthUiState) {
        if (!state.email.trim().matches(EMAIL_REGEX)) {
            _uiState.update { it.copy(errorMessage = "Enter the email on your Lunexa account.", infoMessage = null) }
            return
        }

        if (state.passwordResetId == null) {
            startPasswordReset(state)
            return
        }

        if (!state.passwordResetCode.matches(VERIFICATION_CODE_REGEX)) {
            _uiState.update { it.copy(errorMessage = "Enter the 6-digit reset code sent to your email.") }
            return
        }

        val passwordError = validateNewPassword(state.newPassword, state.confirmNewPassword)
        if (passwordError != null) {
            _uiState.update { it.copy(errorMessage = passwordError, infoMessage = null) }
            return
        }

        confirmPasswordReset(state)
    }

    fun resendVerificationCode() {
        val state = _uiState.value
        val validationError = validateRegistration(state)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError, infoMessage = null) }
            return
        }

        startVerification(state)
    }

    fun resendPasswordResetCode() {
        val state = _uiState.value
        if (!state.email.trim().matches(EMAIL_REGEX)) {
            _uiState.update { it.copy(errorMessage = "Enter the email on your Lunexa account.", infoMessage = null) }
            return
        }

        startPasswordReset(state)
    }

    private fun startVerification(state: AuthUiState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                repository.startEmailVerification(
                    fullName = state.fullName.trim(),
                    email = state.email.trim(),
                    password = state.password
                )
            }.onSuccess { payload ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        emailVerificationId = payload.verificationId,
                        verificationEmail = payload.email,
                        verificationCode = "",
                        infoMessage = "We sent a 6-digit code to ${payload.email}.",
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.toApiMessage("Unable to send verification code."),
                        infoMessage = null
                    )
                }
            }
        }
    }

    private fun authenticate(state: AuthUiState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                if (state.isRegisterMode) {
                    repository.register(
                        fullName = state.fullName.trim(),
                        email = state.email.trim(),
                        password = state.password,
                        emailVerificationId = requireNotNull(state.emailVerificationId),
                        emailVerificationCode = state.verificationCode
                    )
                } else {
                    repository.login(
                        email = state.email.trim(),
                        password = state.password
                    )
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.toApiMessage("Unable to authenticate."),
                        infoMessage = null
                    )
                }
            }
        }
    }

    private fun startPasswordReset(state: AuthUiState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }

            runCatching {
                repository.startPasswordReset(email = state.email.trim())
            }.onSuccess { payload ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        passwordResetId = payload.resetId,
                        passwordResetEmail = payload.email,
                        passwordResetCode = "",
                        newPassword = "",
                        confirmNewPassword = "",
                        infoMessage = payload.message,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.toApiMessage("Unable to send reset code."),
                        infoMessage = null
                    )
                }
            }
        }
    }

    private fun confirmPasswordReset(state: AuthUiState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }

            runCatching {
                repository.confirmPasswordReset(
                    resetId = requireNotNull(state.passwordResetId),
                    email = state.email.trim(),
                    code = state.passwordResetCode,
                    newPassword = state.newPassword
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.toApiMessage("Unable to reset password."),
                        infoMessage = null
                    )
                }
            }
        }
    }

    private fun validateLogin(state: AuthUiState): String? {
        if (!state.email.trim().matches(EMAIL_REGEX)) return "Enter a valid email address."
        if (state.password.length < 8) return "Password must be at least 8 characters."
        return null
    }

    private fun validateRegistration(state: AuthUiState): String? {
        val fullName = state.fullName.trim()
        val email = state.email.trim()
        val domain = email.substringAfterLast("@", missingDelimiterValue = "").lowercase()

        if (fullName.length < 2 || fullName.count { it.isLetter() } < 2) {
            return "Enter your real full name."
        }
        if (BLOCKED_NAMES.contains(fullName.lowercase())) return "Enter your real full name."
        if (!email.matches(EMAIL_REGEX)) return "Enter a valid email address."
        if (BLOCKED_EMAIL_DOMAINS.contains(domain) || domain.endsWith(".test")) {
            return "Use a real inbox that you can access."
        }
        validateStrongPassword(state.password)?.let { return it }
        return null
    }

    private fun validateNewPassword(password: String, confirmation: String): String? {
        validateStrongPassword(password)?.let { return it }
        if (password != confirmation) return "New passwords do not match."
        return null
    }

    private fun validateStrongPassword(password: String): String? {
        if (password.length < 10) return "Use at least 10 characters for your password."
        if (!password.any(Char::isLowerCase)) return "Password needs a lowercase letter."
        if (!password.any(Char::isUpperCase)) return "Password needs an uppercase letter."
        if (!password.any(Char::isDigit)) return "Password needs a number."
        if (!password.any { !it.isLetterOrDigit() }) return "Password needs a symbol."
        return null
    }

    private fun AuthUiState.resetVerification(): AuthUiState =
        copy(
            emailVerificationId = null,
            verificationEmail = null,
            verificationCode = "",
            infoMessage = null
        )

    private companion object {
        val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        val VERIFICATION_CODE_REGEX = Regex("^\\d{6}$")
        val BLOCKED_EMAIL_DOMAINS = setOf(
            "example.com",
            "example.net",
            "example.org",
            "test.com",
            "mailinator.com",
            "yopmail.com",
            "tempmail.com",
            "temp-mail.org",
            "guerrillamail.com",
            "10minutemail.com",
            "trashmail.com",
            "sharklasers.com"
        )
        val BLOCKED_NAMES = setOf(
            "admin",
            "demo",
            "fake",
            "name",
            "null",
            "qwerty",
            "test",
            "undefined",
            "user"
        )
    }
}
