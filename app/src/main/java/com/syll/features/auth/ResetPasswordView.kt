package com.syll.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.syll.R
import com.syll.app.LocalAuthStore
import com.syll.app.text
import com.syll.designsystem.components.CodeInputStyle
import com.syll.designsystem.components.FieldErrorText
import com.syll.designsystem.components.FieldInput
import com.syll.designsystem.components.LabeledField
import com.syll.designsystem.components.PasswordInput
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.ScreenTitle
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.tokens.Spacing
import com.syll.domain.PasswordPolicy
import kotlinx.coroutines.launch

/**
 * iOS `ResetPasswordView`: reset a forgotten password in two steps (0.5 enter the email, 0.6 enter the code and a
 * new password). The store knows whether the code went out.
 */
@Composable
fun ResetPasswordView(email: String, onEmailChange: (String) -> Unit, onBack: () -> Unit) {
    val auth = LocalAuthStore.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf<String?>(null) }
    val emailFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    val sentTo = auth.passwordResetEmail
    val error = auth.errorMessage
    val trimmedEmail = email.trim()
    val canSubmit = when {
        auth.isSubmitting -> false
        sentTo == null -> trimmedEmail.isNotEmpty()
        else -> code.trim().isNotEmpty() && PasswordPolicy.accepts(password)
    }

    DisposableEffect(Unit) {
        auth.clearError()
        onDispose {
            auth.cancelPasswordReset()
            auth.clearError()
        }
    }
    LaunchedEffect(sentTo != null) {
        runCatching { (if (sentTo == null) emailFocus else codeFocus).requestFocus() }
    }

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        scope.launch {
            if (sentTo == null) {
                auth.requestPasswordReset(trimmedEmail)
            } else {
                auth.resetPassword(code.trim(), password)
            }
        }
    }

    AuthPageScaffold(onBack = onBack) {
        ScreenTitle(stringResource(R.string.reset_your_password), Modifier.padding(top = Spacing.xs))

        if (sentTo == null) {
            AuthMessage(
                stringResource(R.string.enter_the_email_you_signed_up_with_we_ll_send_yo),
                Modifier.padding(top = Spacing.s),
            )
            Column(Modifier.padding(top = Spacing.l + Spacing.xxs)) {
                AuthEmailField(
                    email = email,
                    onEmailChange = onEmailChange,
                    isFocused = focused == "email",
                    onFocusChange = { if (it) focused = "email" else if (focused == "email") focused = null },
                    focusRequester = emailFocus,
                    onSubmit = ::submit,
                    isError = error != null,
                    imeAction = ImeAction.Send,
                )
            }
        } else {
            AuthMessage(
                stringResource(R.string.we_sent_a_code_to_x_enter_it_and_choose_a_new_pa, sentTo),
                address = sentTo,
                modifier = Modifier.padding(top = Spacing.s),
            )
            Column(Modifier.padding(top = Spacing.l + Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Column(horizontalAlignment = Alignment.End) {
                    LabeledField(stringResource(R.string.code), isFocused = focused == "code") {
                        FieldInput(
                            value = code,
                            onValueChange = { code = it },
                            placeholder = stringResource(R.string.s_6_digit_code),
                            focusRequester = codeFocus,
                            onFocusChange = { if (it) focused = "code" else if (focused == "code") focused = null },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                            onSubmit = { passwordFocus.requestFocus() },
                            textStyle = CodeInputStyle,
                        )
                    }
                    TextLink(
                        stringResource(R.string.send_a_new_code),
                        onClick = { scope.launch { auth.requestPasswordReset(sentTo) } },
                        enabled = !auth.isSubmitting,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    LabeledField(stringResource(R.string.new_password), isFocused = focused == "password") {
                        PasswordInput(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = stringResource(R.string.create_a_password),
                            focusRequester = passwordFocus,
                            onFocusChange = { if (it) focused = "password" else if (focused == "password") focused = null },
                            onSubmit = ::submit,
                        )
                    }
                    PasswordRuleList(password)
                }
            }
        }

        error?.let { FieldErrorText(it.text(), Modifier.padding(top = Spacing.s)) }

        PrimaryButton(
            title = stringResource(if (sentTo == null) R.string.send_code else R.string.set_new_password),
            onClick = ::submit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.l + Spacing.xxs),
        )

        if (auth.isSubmitting) BusyIndicator()
    }
}
