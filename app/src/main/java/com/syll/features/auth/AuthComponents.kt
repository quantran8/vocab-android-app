package com.syll.features.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.designsystem.components.ActionButton
import com.syll.designsystem.components.ActionButtonKind
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.FieldInput
import com.syll.designsystem.components.LabeledField
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.SyllieView
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.app.UserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Pieces shared by the log-in, sign-up and reset screens (docs/app-redesign-v1.html, flow 0).

/** iOS `AuthBrand`: Syllie over the "Syll" wordmark, opening the log-in screen. */
@Composable
fun AuthBrand(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Syll"
            heading()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // The `Syllie` artboard (520×430) leaves room around him; at this frame his body is about 96 dp wide.
        SyllieView(SyllieMood.IDLE, Modifier.size(160.dp, 132.dp).padding(vertical = 0.dp))
        Text("Syll", style = Typography.wordmark.copy(letterSpacing = Typography.tightTracking.sp), color = SyllColors.ink)
    }
}

/** iOS `AuthFooterLink`: "New here? Create account", the whole line is the button, pinned to the bottom. */
@Composable
fun AuthFooterLink(prompt: String, link: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = SyllColors.bodyInk)) { append(prompt) }
        append(" ")
        withStyle(SpanStyle(color = SyllColors.link, fontWeight = FontWeight.SemiBold)) { append(link) }
    }
    Text(
        text,
        style = Typography.secondary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(SyllColors.canvas)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = Spacing.minTouchTarget)
            .padding(horizontal = Spacing.practiceGutter, vertical = Spacing.s)
            .padding(bottom = Spacing.xs),
    )
}

/** iOS `AuthMessage`: a 16 sp Ink 600 explanation, with [address] (if any) bold in Ink 950. */
@Composable
fun AuthMessage(text: String, modifier: Modifier = Modifier, address: String? = null, textAlign: TextAlign? = null) {
    val annotated = buildAnnotatedString {
        append(text)
        val start = address?.let { text.indexOf(it) } ?: -1
        if (address != null && start >= 0) {
            addStyle(SpanStyle(color = SyllColors.ink, fontWeight = FontWeight.Bold), start, start + address.length)
        }
    }
    Text(
        annotated,
        style = Typography.body.copy(lineHeight = 22.sp),
        color = SyllColors.bodyInk,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** iOS `AuthEmailField`: the email input shared by every auth form. */
@Composable
fun AuthEmailField(
    email: String,
    onEmailChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    LabeledField(stringResource(R.string.email), isFocused = isFocused, isError = isError) {
        FieldInput(
            value = email,
            onValueChange = onEmailChange,
            placeholder = stringResource(R.string.you_example_com),
            focusRequester = focusRequester,
            onFocusChange = onFocusChange,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            onSubmit = onSubmit,
        )
    }
}

/** The pushed pages' bar: the platform back arrow only (iOS `iconOnlyBackButton`). */
@Composable
fun AuthBackBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = SyllColors.ink)
        }
    }
}

/**
 * iOS `SocialSignInButtons`: "or", then the providers. Either provider creates the account on first use, so sign
 * up offers only email and password. Sign in with Apple arrives in Phase 3 (web flow), so only Google shows.
 */
@Composable
fun SocialSignInButtons(auth: AuthStore, scope: CoroutineScope) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(
            Modifier.padding(bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardDivider(Modifier.weight(1f))
            Text(stringResource(R.string.or), style = Typography.micro, color = SyllColors.mutedInk)
            CardDivider(Modifier.weight(1f))
        }

        // The flat outline button.
        ActionButton(
            kind = ActionButtonKind.OUTLINE,
            onClick = {
                auth.clearError()
                scope.launch {
                    try {
                        val result = GoogleSignIn.signIn(context)
                        auth.logInWithGoogle(result.idToken, result.nonce)
                    } catch (e: GoogleSignIn.Failure.Cancelled) {
                        return@launch
                    } catch (e: GoogleSignIn.Failure.NotConfigured) {
                        auth.providerSignInFailed(UserMessage.Resource(R.string.google_sign_in_isn_t_set_up_yet))
                    } catch (e: GoogleSignIn.Failure) {
                        auth.providerSignInFailed()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(Spacing.socialButtonHeight + 0.dp),
            enabled = !auth.isSubmitting,
        ) {
            GoogleMark(Modifier.size(18.dp))
            Text(stringResource(R.string.continue_with_google))
        }
    }
}

/** Google's four-colour "G", drawn so the button needs no image asset. */
@Composable
private fun GoogleMark(modifier: Modifier = Modifier) {
    val blue = Color(0xFF4285F4)
    val green = Color(0xFF34A853)
    val yellow = Color(0xFFFBBC05)
    val red = Color(0xFFEA4335)
    Canvas(modifier) {
        val line = size.minDimension * 0.2f
        val inset = line / 2
        val arcSize = Size(size.width - line, size.height - line)
        // Fractions start at 3 o'clock and run clockwise; the gap is the G's mouth.
        fun arc(from: Float, to: Float, color: Color) = drawArc(
            color, startAngle = from * 360f, sweepAngle = (to - from) * 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = line),
        )
        arc(0f, 0.13f, blue)
        arc(0.13f, 0.38f, green)
        arc(0.38f, 0.56f, yellow)
        arc(0.56f, 0.87f, red)
        drawRect(
            blue,
            topLeft = Offset(size.width / 2 - line / 4, size.height / 2 - line / 2),
            size = Size(size.width * 0.48f, line),
        )
    }
}
