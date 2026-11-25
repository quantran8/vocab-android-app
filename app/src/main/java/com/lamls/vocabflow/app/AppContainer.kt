package com.lamls.vocabflow.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.lamls.vocabflow.BuildConfig
import com.lamls.vocabflow.data.KeystoreTokenStore
import com.lamls.vocabflow.data.SharedPreferencesKeyValueStore
import com.lamls.vocabflow.features.auth.AuthStore
import com.lamls.vocabflow.features.settings.AppSettingsStore
import com.lamls.vocabflow.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS `VocabFlowApp.init`: builds every store once, for the whole process, and wires them together.
 * Views read them through the `Local…` composition locals below (iOS `@Environment(StoreType.self)`).
 *
 * Previews and UI tests build stores with test doubles instead (in-memory storage, offline API), like the iOS
 * `#Preview` in `AppRootView`.
 */
class AppContainer(context: Context) {
    val apiClient = ApiClient(baseUrl = BuildConfig.API_URL, tokens = KeystoreTokenStore(context))
    val settings = AppSettingsStore(SharedPreferencesKeyValueStore(context))
    val auth = AuthStore(apiClient)

    init {
        apiClient.setSessionEndedHandler {
            withContext(Dispatchers.Main) { auth.sessionEnded() }
        }
        auth.onSignedOut = {
            // Phase 1+: reset learner, practice, onboarding, custom words, placement, challenge, ranking.
        }
    }
}

val LocalAuthStore = staticCompositionLocalOf<AuthStore> { error("AuthStore not provided") }
val LocalAppSettingsStore = staticCompositionLocalOf<AppSettingsStore> { error("AppSettingsStore not provided") }

@Composable
fun ProvideStores(container: AppContainer, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAuthStore provides container.auth,
        LocalAppSettingsStore provides container.settings,
        content = content,
    )
}
