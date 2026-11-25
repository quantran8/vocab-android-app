package com.syll.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.syll.R
import com.syll.designsystem.tokens.Borders
import com.syll.designsystem.tokens.Spacing
import com.syll.designsystem.tokens.SyllColors
import com.syll.designsystem.tokens.Typography
import com.syll.domain.PasswordPolicy

private val PasswordPolicy.Rule.title: Int
    get() = when (this) {
        PasswordPolicy.Rule.LENGTH -> R.string.at_least_8_characters
        PasswordPolicy.Rule.LETTER -> R.string.at_least_one_letter
        PasswordPolicy.Rule.DIGIT -> R.string.at_least_one_number
    }

/**
 * iOS `PasswordRuleList`: the rules under a new-password field, ticked live as the learner types: a Green check
 * when met, an Ink 300 ring when not.
 */
@Composable
fun PasswordRuleList(password: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs - 2.dp)) {
        PasswordPolicy.Rule.entries.forEach { rule ->
            RuleRow(stringResource(rule.title), rule.isMetBy(password))
        }
        RuleRow(stringResource(R.string.at_most_72_characters), !PasswordPolicy.isTooLong(password))
    }
}

@Composable
private fun RuleRow(title: String, isMet: Boolean) {
    val state = stringResource(if (isMet) R.string.done else R.string.not_yet)
    Row(
        Modifier.semantics(mergeDescendants = true) { stateDescription = state },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMet) {
            Box(Modifier.size(18.dp).background(SyllColors.success, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        } else {
            Box(Modifier.size(18.dp).border(Borders.card, SyllColors.ink300, CircleShape))
        }
        Text(title, style = Typography.secondary, color = if (isMet) SyllColors.ink else SyllColors.mutedInk)
    }
}
