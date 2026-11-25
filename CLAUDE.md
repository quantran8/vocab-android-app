# CLAUDE.md

Guidance for Claude Code in this repository.

## Project

Syll (VocabFlow) for Android: Kotlin + Jetpack Compose port of the SwiftUI app in the sibling repo `ios_app`. Same backend (`backend`, contract in `backend/docs/FRONTEND_FLOW.md`), same screens, rules, copy and wire format. **The iOS app is the spec**; when unsure, read the Swift file of the same name. Port plan and phases: `docs/PORT_PLAN.md`.

## Commands

```sh
./gradlew test                            # JVM unit tests: core/domain, core/network, app
./gradlew :app:assembleDebug              # debug APK (backend: http://10.0.2.2:3000/ on the emulator)
./gradlew :app:assembleDebug -PvocabflowApiUrl=https://…/   # other backend (iOS VOCABFLOW_API_URL)
./gradlew :app:assembleDebug -PgoogleServerClientId=….apps.googleusercontent.com   # Google sign-in (backend Web client ID)
./gradlew :app:assembleDebug -PappleServiceId=…               # Sign in with Apple (Services ID; web flow via backend auth/apple/callback)
./gradlew :app:assembleRelease -PadmobAppId=… -PadmobInterstitialUnitId=… -PtermsUrl=… -PprivacyPolicyUrl=…   # Premium/ads (docs/PREMIUM_SETUP.md)
python3 tools/xcstrings2android/convert.py [--strict]        # regenerate strings from the iOS catalog
```

## Modules

- `core/domain`: pure Kotlin (JVM, no Android imports) so it stays testable and KMP-ready. Models and pure rules (`AppLanguage`, `practice/` tasks and results, `challenge/` Match Madness engine, `ranking/` LP and Tier, `customwords/`).
- `core/network`: pure Kotlin. `VocabApi` (= iOS `VocabAPI`, stores depend on it; `LiveVocabApi`), `ApiClient` (OkHttp + coroutines: bearer auth, one shared refresh on 401, refresh 60 s before expiry, backoff `500 ms << attempt` for retryable errors), `ApiError`, `ApiJson` (kotlinx.serialization: unknown keys ignored, missing optionals default), `MultipartForm`, `TokenStoring`.
- `core/data`: Android storage. `KeystoreTokenStore` (AES-GCM, Keystore key, excluded from backup) and `KeyValueStore` (SharedPreferences, the `UserDefaults` counterpart, with `InMemoryKeyValueStore` for tests/previews).
- `app`: Compose UI, stores, platform services.

## Architecture (mirrors iOS)

- `app/AppContainer` = iOS `VocabFlowApp.init`: builds every store once (held by `VocabFlowApplication`, so stores survive activity recreation) and wires callbacks. Views read stores via `Local…Store` composition locals (= `@Environment(StoreType.self)`), provided by `ProvideStores`.
- A coroutine started from `rememberCoroutineScope()` dies with its composable. When the work changes state that swaps that screen out (submitting the last answer, finishing a step), launch it from a scope that outlives the swap (the host's, or a store's app scope), or it is cancelled mid-request (iOS `Task {}` isn't).
- `OfflineVocabApi` (= iOS `OfflineVocabAPI`) answers every call without a backend; the debug Design gallery's "Flows (offline API)" runs screens on it with in-memory stores.
- Stores hold user-facing text as `UserMessage` (string resource or `ApiError`), resolved in the view with `.text()`; they have no Context.
- Stores are plain classes with Compose snapshot state (`mutableStateOf`), mutated on the main thread only (= `@Observable @MainActor`). Keep iOS names (`AuthStore`, `LearnerStore`, `PracticeSessionStore`…) and method names so the two apps can be compared by name.
- No navigation stack at the root: `app/AppRoot` derives a `RouteID` from store state and swaps whole screens with `AnimatedContent` (= `.id(route)` + transition). Changing a screen means changing store state.
- Contract invariants that cause 422s if broken must be ported exactly and tested: plan decode strictness (unknown template/variant fails the plan), skipped/known tasks left out of the commit, commit body encoded once (`ByteArray`) so retries are byte-identical, Match Madness rules + `ChallengeBoardOrdering`. Port the iOS XCTest suite for each piece of pure logic in the same change (JUnit, fixtures as JSON under `src/test/resources`).

## Design system

- `designsystem/tokens` ports iOS `DesignSystem/Tokens` 1:1 (`SyllColors`, `Spacing`, `Radii`, `Borders`, `Typography`); iOS pt = dp, text in sp. Use tokens, never literals. Light mode only.
- `designsystem/motion`: `MotionTokens` (iOS springs converted by `MotionSpec.Spring`: stiffness = (2π/response)², dampingRatio = dampingFraction) and `Motion` (= `MotionCoordinator`, via `LocalMotion`): honours the system "Remove animations" and the in-app setting. Animate with `motion.animation(…)`/`motion.travel(…)`, screen swaps with `motion.routeTransition`/`stepTransition`.
- Brand components follow iOS: `PrimaryButton` (Ink, depth edge), `SecondaryButton` (kind from `LocalActionContext`: outline 3D in practice, flat elsewhere), `ActionButtonKind`, `cardSurface()`, `CardDivider`, `StatusScreen`.
- Platform chrome is Android's, never a copy of iOS's (`designsystem/components/PlatformChrome.kt`): iOS `.large` sheets/editors → `FullScreenDialog` (title in the app bar, ✕ at the top right, the confirming button pinned at the bottom); ✕ always sits at the top right, flow headers included; short pickers → M3 `ModalBottomSheet` with its drag handle and no ✕; pushed pages → `PageScaffold` (large app bar + back arrow); confirmations → `AlertDialog` (`ConfirmSheet`); toasts → `Snackbar`; popovers → rich tooltip; plus Switch, NavigationBar, pull-to-refresh, time picker.
- Design source: `ios_app/docs/DESIGN_SYSTEM.md` and `ios_app/docs/app-redesign-v1.html`. Design changes need approval first, as on iOS.
- Rive: `res/raw/syllie.riv` is a copy of iOS `Resources/Animations/syllie.riv`; artboard/timeline/state machine names must match iOS. No Rive view models (deprecated): `SyllieView(mood)` plays the mood's timeline. Re-copy it whenever iOS re-exports. `RiveArtboard` wraps `RiveAnimationView`. Lottie: `res/raw/confetti.lottie`.
- Debug builds have a design gallery (Auth placeholder → "Design gallery") for checking tokens, buttons, Rive, Lottie, reduce motion and languages on a device.

## Localization

- Shared strings come from iOS: `tools/xcstrings2android/convert.py` reads `ios_app/VocabFlow/Resources/Localizable.xcstrings` (stale keys skipped) and writes `res/values*/strings_ios.xml` (generated, never edit) with names kept stable in `tools/xcstrings2android/string_names.json` (commit it). `%@`→`%s`, `%lld`→`%d`, always positional; plural keys → `<plurals>`.
- Android-only strings go in `res/values*/strings.xml`; every one needs all 11 languages (`AppLanguage.entries`: vi, en, hi, es, ja, pt-BR, ko, zh-Hant, th, ar, id), like iOS. Debug-only text may be `translatable="false"`.
- Display language = Android per-app language (`AppSettingsStore.setLanguage` → `AppCompatDelegate.setApplicationLocales`; the activity is recreated and Arabic flips to RTL). Use start/end, never left/right. Resource folders: `values-pt-rBR`, `values-b+zh+Hant`, `values-in` (Indonesian).

## Backend

Debug: `http://10.0.2.2:3000/` (emulator → host; cleartext allowed in debug only). Release: `https://moneytogether.dpdns.org/service/vocab/`. Override with `-PvocabflowApiUrl`.
