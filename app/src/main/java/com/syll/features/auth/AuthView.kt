package com.syll.features.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.syll.BuildConfig
import com.syll.R
import com.syll.app.LocalAuthStore
import com.syll.app.text
import com.syll.designsystem.components.FieldErrorText
import com.syll.designsystem.components.LabeledField
import com.syll.designsystem.components.PasswordInput
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.motion.LocalMotion
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.PasswordPolicy
import kotlinx.coroutines.launch

/** The log-in screen and the two pages it pushes (iOS `NavigationStack` destinations). */
private enum class AuthPage { LOG_IN, SIGN_UP, RESET }

/**
 * iOS `AuthView`: log in with email and password or Google (docs/app-redesign-v1.html, 0.1–0.2). Creating an
 * account ([SignUpView]) and resetting a password ([ResetPasswordView]) are pushed pages with a back arrow.
 * The email typed on one page carries over to the others, like the iOS `@Binding`.
 */
@Composable
fun AuthView(onOpenGallery: () -> Unit = {}) {
    val motion = LocalMotion.current
    var page by rememberSaveable { mutableStateOf(AuthPage.LOG_IN) }
    var email by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = page != AuthPage.LOG_IN) { page = AuthPage.LOG_IN }

    AnimatedContent(
        targetState = page,
        transitionSpec = { motion.stepTransition(isMovingBack = targetState == AuthPage.LOG_IN) },
        label = "authPage",
        modifier = Modifier.fillMaxSize().background(SyllColors.canvas),
    ) { current ->
        when (current) {
            AuthPage.LOG_IN -> LogInPage(
                email = email,
                onEmailChange = { email = it },
                onOpen = { page = it },
                onOpenGallery = onOpenGallery,
            )
            AuthPage.SIGN_UP -> SignUpView(email, { email = it }, onBack = { page = AuthPage.LOG_IN })
            AuthPage.RESET -> ResetPasswordView(email, { email = it }, onBack = { page = AuthPage.LOG_IN })
        }
    }
}

@Composable
private fun LogInPage(
    email: String,
    onEmailChange: (String) -> Unit,
    onOpen: (AuthPage) -> Unit,
    onOpenGallery: () -> Unit,
) {
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
    val canSubmit = !auth.isSubmitting && trimmedEmail.isNotEmpty() && PasswordPolicy.hasAllowedLength(password)

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        scope.launch { auth.logIn(trimmedEmail, password) }
    }

    fun open(target: AuthPage) {
        auth.clearError()
        focusManager.clearFocus()
        // The reset's new password must not reuse what was typed to log in.
        password = ""
        onOpen(target)
    }

    AuthPageScaffold(
        footer = {
            AuthFooterLink(
                prompt = stringResource(R.string.new_here),
                link = stringResource(R.string.create_account),
                onClick = { open(AuthPage.SIGN_UP) },
            )
        },
    ) {
        AuthBrand(Modifier.padding(top = Spacing.l))

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
            LabeledField(stringResource(R.string.password), isFocused = passwordFocused, isError = error != null) {
                PasswordInput(
                    value = password,
                    onValueChange = {
                        password = it
                        if (error != null) auth.clearError()
                    },
                    placeholder = stringResource(R.string.at_least_8_characters),
                    focusRequester = passwordFocus,
                    onFocusChange = { passwordFocused = it },
                    onSubmit = ::submit,
                )
            }
        }

        auth.accountDeletedNotice?.let {
            Text(it.text(), style = Typography.secondary, color = SyllColors.ink, modifier = Modifier.padding(top = Spacing.s))
        }
        error?.let { FieldErrorText(it.text(), Modifier.padding(top = Spacing.s)) }

        Box(Modifier.fillMaxWidth().padding(top = Spacing.xs), contentAlignment = Alignment.CenterEnd) {
            TextLink(stringResource(R.string.forgot_password), onClick = { open(AuthPage.RESET) })
        }

        PrimaryButton(
            title = stringResource(R.string.log_in),
            onClick = ::submit,
            enabled = canSubmit,
            modifier = Modifier.padding(top = Spacing.m),
        )

        if (auth.isSubmitting) BusyIndicator()

        Box(Modifier.padding(top = Spacing.l)) {
            SocialSignInButtons(auth, scope)
        }

        if (BuildConfig.DEBUG) {
            TextLink(
                stringResource(R.string.debug_design_gallery),
                onClick = onOpenGallery,
                color = SyllColors.mutedInk,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.m),
            )
        }
    }
}

/**
 * The layout every auth page shares: a scrolling column with the practice gutter on the canvas, an optional
 * back bar on top and an optional footer pinned above the keyboard.
 */
@Composable
internal fun AuthPageScaffold(
    onBack: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(SyllColors.canvas).safeDrawingPadding().imePadding()) {
        if (onBack != null) AuthBackBar(onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.practiceGutter)
                .padding(bottom = Spacing.l),
            content = content,
        )
        footer?.invoke()
    }
}

@Composable
internal fun ColumnScope.BusyIndicator() {
    CircularProgressIndicator(
        color = SyllColors.mutedInk,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.s),
    )
}
