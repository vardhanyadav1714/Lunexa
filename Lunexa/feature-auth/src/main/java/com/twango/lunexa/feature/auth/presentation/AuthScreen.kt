package com.twango.lunexa.feature.auth.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@Composable
fun AuthRoute(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            onAuthenticated()
        }
    }

    AuthScreen(
        state = state,
        onFullNameChange = viewModel::onFullNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onVerificationCodeChange = viewModel::onVerificationCodeChange,
        onPasswordResetCodeChange = viewModel::onPasswordResetCodeChange,
        onNewPasswordChange = viewModel::onNewPasswordChange,
        onConfirmNewPasswordChange = viewModel::onConfirmNewPasswordChange,
        onToggleMode = viewModel::toggleMode,
        onShowPasswordReset = viewModel::showPasswordReset,
        onShowSignIn = viewModel::showSignIn,
        onSubmit = viewModel::submit,
        onResendCode = viewModel::resendVerificationCode,
        onResendPasswordResetCode = viewModel::resendPasswordResetCode
    )
}

@Composable
private fun AuthScreen(
    state: AuthUiState,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onVerificationCodeChange: (String) -> Unit,
    onPasswordResetCodeChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmNewPasswordChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onShowPasswordReset: () -> Unit,
    onShowSignIn: () -> Unit,
    onSubmit: () -> Unit,
    onResendCode: () -> Unit,
    onResendPasswordResetCode: () -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f)
                    )
                )
            )
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            BrandHeader(state)

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (state.isPasswordResetMode) {
                        ResetModeHeader(onShowSignIn = onShowSignIn, enabled = !state.isLoading)
                    } else {
                        ModeSwitch(
                            isRegisterMode = state.isRegisterMode,
                            onToggleMode = onToggleMode,
                            enabled = !state.isLoading
                        )
                    }

                    AnimatedVisibility(visible = state.isRegisterMode && !state.isPasswordResetMode) {
                        KeyboardAwareTextField(
                            value = state.fullName,
                            onValueChange = onFullNameChange,
                            label = "Full name",
                            enabled = !state.isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            )
                        )
                    }

                    KeyboardAwareTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = if (state.isPasswordResetMode) "Account email" else "Email",
                        enabled = !state.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )

                    if (!state.isPasswordResetMode) {
                        PasswordField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            label = "Password",
                            visible = passwordVisible,
                            onToggleVisible = { passwordVisible = !passwordVisible },
                            enabled = !state.isLoading,
                            imeAction = if (state.isRegisterMode) ImeAction.Next else ImeAction.Done,
                            onDone = { focusManager.clearFocus() }
                        )
                    }

                    if (!state.isPasswordResetMode && !state.isRegisterMode) {
                        TextButton(
                            onClick = onShowPasswordReset,
                            enabled = !state.isLoading,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Forgot password?")
                        }
                    }

                    if (state.isRegisterMode && !state.isPasswordResetMode) {
                        SecurityChecklist()
                    }

                    if (state.isAwaitingVerification) {
                        VerificationStep(
                            email = state.verificationEmail ?: state.email,
                            code = state.verificationCode,
                            isLoading = state.isLoading,
                            onVerificationCodeChange = onVerificationCodeChange,
                            onResendCode = onResendCode
                        )
                    }

                    if (state.isPasswordResetMode) {
                        PasswordResetStep(
                            state = state,
                            isLoading = state.isLoading,
                            newPasswordVisible = newPasswordVisible,
                            confirmPasswordVisible = confirmPasswordVisible,
                            onPasswordResetCodeChange = onPasswordResetCodeChange,
                            onNewPasswordChange = onNewPasswordChange,
                            onConfirmNewPasswordChange = onConfirmNewPasswordChange,
                            onToggleNewPasswordVisible = { newPasswordVisible = !newPasswordVisible },
                            onToggleConfirmPasswordVisible = {
                                confirmPasswordVisible = !confirmPasswordVisible
                            },
                            onResendPasswordResetCode = onResendPasswordResetCode
                        )
                    }

                    state.infoMessage?.let {
                        MessagePanel(
                            text = it,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    state.errorMessage?.let {
                        MessagePanel(
                            text = it,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit()
                        },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = primaryButtonText(state),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    TextButton(
                        onClick = if (state.isPasswordResetMode) onShowSignIn else onToggleMode,
                        enabled = !state.isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            when {
                                state.isPasswordResetMode -> "Back to sign in"
                                state.isRegisterMode -> "I already have an account"
                                else -> "Create a verified account"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BrandHeader(state: AuthUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "L",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lunexa",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = authSubtitle(state),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = when {
                state.isPasswordResetMode -> "Reset your password securely with a one-time email code."
                state.isRegisterMode -> "Create a verified workspace for accounts, budgets, and spending intelligence."
                else -> "Welcome back. Your polished finance command center is ready."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SecurityPill("Verified email")
            SecurityPill("Private session")
            SecurityPill("Budget insights")
        }
    }
}

@Composable
private fun ResetModeHeader(
    onShowSignIn: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.74f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Password reset",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Email code plus a new strong password.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
            )
        }
        TextButton(onClick = onShowSignIn, enabled = enabled) {
            Text("Sign in")
        }
    }
}

@Composable
private fun ModeSwitch(
    isRegisterMode: Boolean,
    enabled: Boolean,
    onToggleMode: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(8.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModeButton(
            text = "Sign in",
            selected = !isRegisterMode,
            enabled = enabled,
            onClick = { if (isRegisterMode) onToggleMode() },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = "Create",
            selected = isRegisterMode,
            enabled = enabled,
            onClick = { if (!isRegisterMode) onToggleMode() },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.dp, Color.Transparent)
        ) {
            Text(text)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SecurityChecklist() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SecurityPill("10+ characters")
        SecurityPill("Upper and lower")
        SecurityPill("Number plus symbol")
    }
}

@Composable
private fun SecurityPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    enabled: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit
) {
    KeyboardAwareTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            TextButton(onClick = onToggleVisible, enabled = enabled) {
                Text(if (visible) "Hide" else "Show")
            }
        }
    )
}

@Composable
private fun VerificationStep(
    email: String,
    code: String,
    isLoading: Boolean,
    onVerificationCodeChange: (String) -> Unit,
    onResendCode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Verify $email",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        KeyboardAwareTextField(
            value = code,
            onValueChange = onVerificationCodeChange,
            label = "6-digit code",
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            )
        )
        TextButton(
            onClick = onResendCode,
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Send a new code")
        }
    }
}

@Composable
private fun PasswordResetStep(
    state: AuthUiState,
    isLoading: Boolean,
    newPasswordVisible: Boolean,
    confirmPasswordVisible: Boolean,
    onPasswordResetCodeChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmNewPasswordChange: (String) -> Unit,
    onToggleNewPasswordVisible: () -> Unit,
    onToggleConfirmPasswordVisible: () -> Unit,
    onResendPasswordResetCode: () -> Unit
) {
    AnimatedVisibility(visible = state.isAwaitingPasswordReset) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Reset ${state.passwordResetEmail ?: state.email}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            KeyboardAwareTextField(
                value = state.passwordResetCode,
                onValueChange = onPasswordResetCodeChange,
                label = "6-digit reset code",
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                )
            )
            PasswordField(
                value = state.newPassword,
                onValueChange = onNewPasswordChange,
                label = "New password",
                visible = newPasswordVisible,
                onToggleVisible = onToggleNewPasswordVisible,
                enabled = !isLoading,
                imeAction = ImeAction.Next,
                onDone = {}
            )
            PasswordField(
                value = state.confirmNewPassword,
                onValueChange = onConfirmNewPasswordChange,
                label = "Confirm new password",
                visible = confirmPasswordVisible,
                onToggleVisible = onToggleConfirmPasswordVisible,
                enabled = !isLoading,
                imeAction = ImeAction.Done,
                onDone = {}
            )
            TextButton(
                onClick = onResendPasswordResetCode,
                enabled = !isLoading,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Send a new reset code")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun KeyboardAwareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    scope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            },
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun MessagePanel(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun primaryButtonText(state: AuthUiState): String =
    when {
        state.isPasswordResetMode && state.isAwaitingPasswordReset -> "Reset password"
        state.isPasswordResetMode -> "Send reset code"
        !state.isRegisterMode -> "Sign in"
        state.isAwaitingVerification -> "Verify and create account"
        else -> "Send verification code"
    }

private fun authSubtitle(state: AuthUiState): String =
    when {
        state.isPasswordResetMode -> "Secure account recovery"
        state.isRegisterMode -> "Verified money workspace"
        else -> "Private money control"
    }
