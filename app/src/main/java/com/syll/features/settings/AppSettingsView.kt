package com.syll.features.settings

import com.syll.designsystem.components.PageScaffold
import com.syll.designsystem.components.FullScreenDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syll.R
import com.syll.app.LocalAppScope
import com.syll.app.LocalAppSettingsStore
import com.syll.app.LocalAuthStore
import com.syll.app.LocalLearnerStore
import com.syll.app.text
import com.syll.designsystem.components.CardDivider
import com.syll.designsystem.components.ConfirmSheet
import com.syll.designsystem.components.ConfirmSheetStyle
import com.syll.designsystem.components.Eyebrow
import com.syll.designsystem.components.FieldErrorText
import com.syll.designsystem.components.LabeledField
import com.syll.designsystem.components.PasswordInput
import com.syll.designsystem.components.PrimaryButton
import com.syll.designsystem.components.SecondaryButton
import com.syll.designsystem.components.SyllieMood
import com.syll.designsystem.components.SyllieView
import com.syll.designsystem.components.TextLink
import com.syll.designsystem.components.cardSurface
import com.syll.designsystem.components.pressScaleClickable
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Radii
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.AppLanguage
import com.syll.domain.PasswordPolicy
import com.syll.features.auth.PasswordRuleList
import com.syll.features.plan.SyllSwitch
import kotlinx.coroutines.launch

/**
 * iOS `AppSettingsView`: interaction, app and account settings, log out and account deletion. The leaderboard switch
 * arrives with Ranking (Phase 2), the Practice Lab with Phase 2; Syllie's animations are in the debug gallery.
 */
@Composable
fun AppSettingsView(onBack: () -> Unit) {
    val settings = LocalAppSettingsStore.current
    val auth = LocalAuthStore.current
    val learner = LocalLearnerStore.current
    val appScope = LocalAppScope.current
    val context = LocalContext.current
    var isChoosingLanguage by remember { mutableStateOf(false) }
    var isChangingPassword by remember { mutableStateOf(false) }
    var isConfirmingDeletion by remember { mutableStateOf(false) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0" }

    PageScaffold(stringResource(R.string.settings), onBack) { padding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenGutter)
            .padding(bottom = Spacing.l),
    ) {

        Group(stringResource(R.string.interaction)) {
            ToggleRow(Icons.Outlined.TouchApp, stringResource(R.string.haptics), settings.hapticsEnabled) { settings.hapticsEnabled = it }
            CardDivider()
            ToggleRow(Icons.Outlined.Animation, stringResource(R.string.reduce_motion), settings.reduceMotion) { settings.reduceMotion = it }
        }

        Group(stringResource(R.string.app)) {
            SettingRow(Icons.Outlined.Language, stringResource(R.string.language), Modifier.pressScaleClickable { isChoosingLanguage = true }) {
                Chevron(settings.language.nativeName)
            }
            CardDivider()
            SettingRow(Icons.Outlined.Info, stringResource(R.string.about_syll), Modifier.semantics(mergeDescendants = true) {}) {
                Text("v$version", style = Typography.secondary, color = SyllColors.mutedInk)
            }
        }

        Group(stringResource(R.string.account)) {
            SettingRow(Icons.Outlined.Key, stringResource(R.string.change_password), Modifier.pressScaleClickable { isChangingPassword = true }) {
                Chevron(null)
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(top = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.screenGutter),
        ) {
            SecondaryButton(stringResource(R.string.log_out), onClick = { appScope.launch { auth.signOut() } })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                TextLink(
                    stringResource(R.string.delete_account),
                    onClick = { isConfirmingDeletion = true },
                    color = SyllColors.destructiveInk,
                    enabled = !auth.isDeletingAccount,
                )
                if (auth.isDeletingAccount) CircularProgressIndicator(Modifier.size(18.dp), color = SyllColors.mutedInk, strokeWidth = 2.dp)
            }
        }
        auth.accountDeletionError?.let {
            Text(
                it.text(),
                style = Typography.secondary,
                color = SyllColors.errorInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
            )
        }
    }
    }

    if (isChoosingLanguage) {
        LanguageSheet(
            current = settings.language,
            onSelect = { language ->
                isChoosingLanguage = false
                // Applying recreates the activity (and this screen); the profile's locale follows from the app scope.
                if (language != settings.language) {
                    settings.setLanguage(language)
                    appScope.launch { learner.languageDidChange() }
                }
            },
            onDismiss = { isChoosingLanguage = false },
        )
    }
    if (isChangingPassword) ChangePasswordSheet(onDismiss = { isChangingPassword = false })
    if (isConfirmingDeletion) {
        ConfirmSheet(
            style = ConfirmSheetStyle.Destructive(Icons.Outlined.DeleteOutline),
            title = stringResource(R.string.delete_your_account),
            message = stringResource(R.string.your_progress_words_and_settings_will_be_permane),
            cancelTitle = stringResource(R.string.cancel),
            confirmTitle = stringResource(R.string.delete_account),
            onDismiss = { if (!auth.isDeletingAccount) isConfirmingDeletion = false },
            onConfirm = {
                appScope.launch {
                    auth.deleteAccount()
                    isConfirmingDeletion = false
                }
            },
        )
    }
}

/** An eyebrow header above one card of rows. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = Spacing.l + Spacing.xxs), verticalArrangement = Arrangement.spacedBy(Spacing.s - 2.dp)) {
        Eyebrow(title, Modifier.semantics { heading() })
        Column(Modifier.fillMaxWidth().cardSurface(), content = content)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.rowMinHeight)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SyllColors.ink700, modifier = Modifier.width(Spacing.screenGutter + Spacing.xxs).size(20.dp))
        Text(title, style = Typography.body.copy(fontWeight = FontWeight.SemiBold), color = SyllColors.ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title) { SyllSwitch(isOn, onChange, title) }
}

/** A value (optional) and a chevron, trailing a row that opens something. */
@Composable
private fun Chevron(value: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp), verticalAlignment = Alignment.CenterVertically) {
        value?.let { Text(it, style = Typography.secondary, color = SyllColors.mutedInk) }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = SyllColors.ink400, modifier = Modifier.size(18.dp))
    }
}

/**
 * iOS `LanguageSheet`: picking a language closes the sheet; the host applies it, because applying recreates the
 * screen, sheet included. Selection is colour and border, never a checkmark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet),
        containerColor = SyllColors.canvas,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenGutter)
                .padding(bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                stringResource(R.string.choose_your_language),
                style = Typography.sheetTitle.copy(letterSpacing = Typography.tightTracking.sp),
                color = SyllColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AppLanguage.entries.forEach { language ->
                    val isSelected = language == current
                    val shape = RoundedCornerShape(Radii.tile)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Spacing.minTouchTarget + 12.dp)
                            .pressScaleClickable { onSelect(language) }
                            .background(if (isSelected) SyllColors.selectedSurface else SyllColors.surface, shape)
                            .border(Borders.card, if (isSelected) SyllColors.interactive else SyllColors.stroke, shape)
                            .semantics { selected = isSelected }
                            .padding(horizontal = Spacing.m),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            language.nativeName,
                            style = Typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) SyllColors.selectedInk else SyllColors.ink,
                        )
                    }
                }
            }
        }
    }
}

/**
 * iOS `ChangePasswordSheet`: the backend re-checks the current password. Success shows Syllie with a green check.
 */
@Composable
private fun ChangePasswordSheet(onDismiss: () -> Unit) {
    val auth = LocalAuthStore.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var didChange by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf<Int?>(0) }
    val currentFocus = remember { FocusRequester() }
    val newFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        auth.clearPasswordChangeError()
        runCatching { currentFocus.requestFocus() }
    }
    val canSubmit = !auth.isChangingPassword && current.length in PasswordPolicy.length && PasswordPolicy.accepts(new)
    fun submit() {
        if (!canSubmit) return
        scope.launch {
            if (auth.changePassword(current, new)) {
                current = ""
                new = ""
                didChange = true
            }
        }
    }

    FullScreenDialog(
        title = stringResource(R.string.change_password),
        onDismiss = onDismiss,
        actionTitle = if (didChange) null else stringResource(R.string.save),
        actionEnabled = canSubmit,
        onAction = ::submit,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            if (didChange) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        SyllieView(SyllieMood.HAPPY, Modifier.size(186.dp, 154.dp))
                        Box(
                            Modifier
                                .offset(x = -Spacing.l, y = -Spacing.xxs)
                                .size(Spacing.iconBadge)
                                .background(SyllColors.success, CircleShape)
                                .border(Borders.focusHalo, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                    Text(
                        stringResource(R.string.your_password_has_been_changed),
                        style = Typography.screenTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = Typography.tightTracking.sp),
                        color = SyllColors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.l).semantics { heading() },
                    )
                }
                Box(Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.xs)) {
                    PrimaryButton(stringResource(R.string.done), onClick = onDismiss)
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screenGutter)
                        .padding(bottom = Spacing.l),
                ) {
                    Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        LabeledField(stringResource(R.string.current_password), isFocused = focus == 0, isError = auth.passwordChangeError != null) {
                            PasswordInput(
                                current,
                                {
                                    current = it
                                    if (auth.passwordChangeError != null) auth.clearPasswordChangeError()
                                },
                                stringResource(R.string.current_password),
                                focusRequester = currentFocus,
                                onFocusChange = { if (it) focus = 0 },
                                imeAction = ImeAction.Next,
                                onSubmit = { runCatching { newFocus.requestFocus() } },
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            LabeledField(stringResource(R.string.new_password), isFocused = focus == 1) {
                                PasswordInput(
                                    new, { new = it }, stringResource(R.string.create_a_password),
                                    focusRequester = newFocus,
                                    onFocusChange = { if (it) focus = 1 },
                                    imeAction = ImeAction.Done,
                                    onSubmit = ::submit,
                                )
                            }
                            PasswordRuleList(new)
                        }
                    }
                    auth.passwordChangeError?.let { FieldErrorText(it.text(), Modifier.padding(top = Spacing.s)) }
                    Text(
                        stringResource(R.string.signed_up_with_google_or_apple_log_out_and_use_f),
                        style = Typography.micro,
                        color = SyllColors.mutedInk,
                        modifier = Modifier.padding(top = Spacing.l),
                    )
                }
                if (auth.isChangingPassword) {
                    Box(Modifier.fillMaxWidth().padding(bottom = Spacing.m), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = SyllColors.mutedInk, strokeWidth = 2.5.dp)
                    }
                }
            }
        }
    }
}
