package com.syll.features.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.syll.app.LocalLearnerStore
import com.syll.app.LocalLearningPlanStore
import com.syll.app.LocalOnboardingStore
import com.syll.app.LocalPlacementTestStore
import com.syll.app.OfflineVocabApi
import com.syll.domain.AppLanguage
import com.syll.features.main.LearnerStore
import com.syll.features.onboarding.OnboardingStore
import com.syll.features.onboarding.OnboardingView
import com.syll.features.placement.PlacementTestStore
import com.syll.features.plan.InMemoryPreferencesStore
import com.syll.features.plan.LearningPlanStore
import com.syll.features.plan.NoReminders
import com.syll.platform.NoOpFeedback

/**
 * Debug only: onboarding with its own stores on [OfflineVocabApi] and in-memory storage, so the whole flow (and the
 * placement test) can be walked on a device without a backend or an account. Finishing closes it.
 */
@Composable
fun OfflineOnboarding(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val api = remember { OfflineVocabApi() }
    val plan = remember { LearningPlanStore(InMemoryPreferencesStore(), NoReminders) }
    val learner = remember { LearnerStore(api, plan, language = { AppLanguage.ENGLISH }) }
    val onboarding = remember { OnboardingStore(plan, api, NoOpFeedback) }
    val placement = remember { PlacementTestStore(api, NoOpFeedback, scope) }

    LaunchedEffect(plan.hasCompletedOnboarding) {
        if (plan.hasCompletedOnboarding) onClose()
    }
    CompositionLocalProvider(
        LocalLearningPlanStore provides plan,
        LocalLearnerStore provides learner,
        LocalOnboardingStore provides onboarding,
        LocalPlacementTestStore provides placement,
    ) {
        OnboardingView()
    }
}
