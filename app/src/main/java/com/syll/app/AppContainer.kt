package com.syll.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.syll.BuildConfig
import com.syll.data.KeystoreTokenStore
import com.syll.data.SharedPreferencesKeyValueStore
import com.syll.features.auth.AuthStore
import com.syll.features.settings.AppSettingsStore
import com.syll.network.ApiClient
import com.syll.network.LiveVocabApi
import com.syll.network.VocabApi
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
    val api: VocabApi = LiveVocabApi(apiClient)
    val auth = AuthStore(api)

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
