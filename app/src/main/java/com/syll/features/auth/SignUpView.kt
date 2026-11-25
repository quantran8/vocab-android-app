package com.syll.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.app.LocalAuthStore
import com.syll.app.text
import com.syll.designsystem.components.FieldErrorText
import com.syll.designsystem.components.LabeledField
import com.syll.designsystem.components.PasswordInput
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.ScreenTitle
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.SyllieView
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.PasswordPolicy
import kotlinx.coroutines.launch

/**
 * iOS `SignUpView`: creating an account with email and password (docs/app-redesign-v1.html, 0.3), pushed from the
 * log-in screen. Once sent, the page turns into "Check your email" (0.4).
 */
@Composable
fun SignUpView(email: String, onEmailChange: (String) -> Unit, onBack: () -> Unit) {
    val auth = LocalAuthStore.current

    DisposableEffect(Unit) {
        auth.clearError()
        onDispose {
            auth.clearError()
            auth.dismissConfirmation()
        }
    }

    val confirmation = auth.pendingConfirmationEmail
    if (confirmation != null) {
        CheckEmail(confirmation, onBack)
    } else {
        SignUpForm(email, onEmailChange, onBack)
    }
}

@Composable
private fun SignUpForm(email: String, onEmailChange: (String) -> Unit, onBack: () -> Unit) {
    val auth = LocalAuthStore.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var password by rememberSaveable { mutableStateOf("") }
    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    val error = auth.errorMessage
    val trimmedEmail = email.trim()
    val canSubmit = !auth.isSubmitting && trimmedEmail.isNotEmpty() && PasswordPolicy.accepts(password)

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        scope.launch { auth.signUp(trimmedEmail, password) }
    }

    AuthPageScaffold(
        onBack = onBack,
        footer = {
            AuthFooterLink(
                prompt = stringResource(R.string.already_have_an_account),
                link = stringResource(R.string.log_in),
                onClick = onBack,
            )
        },
    ) {
        ScreenTitle(stringResource(R.string.create_your_account), Modifier.padding(top = Spacing.xs))

        Column(Modifier.padding(top = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            AuthEmailField(
                email = email,
                onEmailChange = {
                    onEmailChange(it)
                    if (error != null) auth.clearError()
                },
                isFocused = emailFocused,
                onFocusChange = { emailFocused = it },
                focusRequester = emailFocus,
                onSubmit = { passwordFocus.requestFocus() },
                isError = error != null,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                LabeledField(stringResource(R.string.password), isFocused = passwordFocused) {
                    PasswordInput(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = stringResource(R.string.create_a_password),
                        focusRequester = passwordFocus,
                        onFocusChange = { passwordFocused = it },
                        onSubmit = ::submit,
                    )
                }
                PasswordRuleList(password)
            }
        }

        error?.let { FieldErrorText(it.text(), Modifier.padding(top = Spacing.s)) }

        // Faded until every rule is met.
        PrimaryButton(
            title = stringResource(R.string.sign_up),
            onClick = ::submit,
            enabled = canSubmit,
            modifier = Modifier.padding(top = Spacing.l + Spacing.xxs),
        )

        if (auth.isSubmitting) BusyIndicator()
    }
}

/** Syllie with a mail badge, the address in bold so it can be checked, and the way back to log in. */
@Composable
private fun CheckEmail(address: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(SyllColors.canvas).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.BottomEnd) {
            SyllieView(SyllieMood.HAPPY, Modifier.size(186.dp, 154.dp))
            Box(
                Modifier
                    .offset(x = -Spacing.l, y = -Spacing.xxs)
                    .size(Spacing.minTouchTarget)
                    .background(SyllColors.interactive, CircleShape)
                    .border(Borders.focusHalo, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Email, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.padding(horizontal = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.check_your_email),
                style = Typography.sheetTitle,
                color = SyllColors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.l + Spacing.xxs).semantics { heading() },
            )
            AuthMessage(
                text = stringResource(R.string.we_sent_a_confirmation_link_to_x_open_it_then_lo, address),
                address = address,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            title = stringResource(R.string.back_to_log_in),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.practiceGutter).padding(bottom = Spacing.s),
        )
    }
}
