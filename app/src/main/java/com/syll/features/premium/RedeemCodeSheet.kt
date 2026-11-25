package com.syll.features.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalLearnerStore
import com.syll.app.UserMessage
import com.syll.app.text
import com.syll.designsystem.components.FieldErrorText
import com.syll.designsystem.components.FieldInput
import com.syll.designsystem.components.LabeledField
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.SyllieView
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.network.asApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * iOS `RedeemCodeSheet`: a Syll redeem code (not a Google Play promo code), shown only while the server allows
 * redeeming (`features.redeemEnabled`). Success shows Syllie and the new end date. A short form, so a bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemCodeSheet(onDismiss: () -> Unit) {
    val learner = LocalLearnerStore.current
    val appScope = LocalAppScope.current
    val locale = LocalConfiguration.current.locales[0]
    var code by remember { mutableStateOf("") }
    var isRedeeming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UserMessage?>(null) }
    var didRedeem by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val canSubmit = !isRedeeming && code.isNotBlank()

    fun submit() {
        if (!canSubmit) return
        isRedeeming = true
        error = null
        appScope.launch {
            try {
                learner.redeem(code)
                didRedeem = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = UserMessage.Api(e.asApiError)
            } finally {
                isRedeeming = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        Column(Modifier.padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.m).imePadding()) {
            if (didRedeem) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SyllieView(SyllieMood.HAPPY, Modifier.size(186.dp, 154.dp))
                    Text(
                        stringResource(R.string.you_re_premium),
                        style = Typography.sheetTitle.copy(letterSpacing = Typography.sheetTracking.sp),
                        color = SyllColors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    learner.profile?.plan?.premiumUntil?.let {
                        Text(stringResource(R.string.premium_until_x, PremiumText.longDate(it, locale)), style = Typography.secondary, color = SyllColors.bodyInk)
                    }
                    PrimaryButton(stringResource(R.string.done), onClick = onDismiss, modifier = Modifier.padding(top = Spacing.l))
                }
            } else {
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Text(
                    stringResource(R.string.redeem_a_code),
                    style = Typography.sheetTitle.copy(letterSpacing = Typography.sheetTracking.sp),
                    color = SyllColors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.enter_the_code_you_received_to_unlock_syll_premi),
                    style = Typography.secondary,
                    color = SyllColors.bodyInk,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                LabeledField(stringResource(R.string.code), Modifier.padding(top = Spacing.l), isFocused = isFocused, isError = error != null) {
                    FieldInput(
                        value = code,
                        onValueChange = {
                            code = it
                            error = null
                        },
                        placeholder = "SYLL-XXXX",
                        focusRequester = focus,
                        onFocusChange = { isFocused = it },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        onSubmit = ::submit,
                    )
                }
                error?.let { FieldErrorText(it.text(), Modifier.padding(top = Spacing.s)) }
                Column(
                    Modifier.fillMaxWidth().padding(top = Spacing.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    if (isRedeeming) CircularProgressIndicator(Modifier.size(24.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
                    PrimaryButton(stringResource(R.string.redeem), onClick = ::submit, enabled = canSubmit)
                }
            }
        }
    }
}
