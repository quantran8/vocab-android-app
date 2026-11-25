# Port VocabFlow (Syll) to Android — Kotlin + Jetpack Compose

## Context
VocabFlow is a SwiftUI iOS app (~31k lines Swift, ~5.8k lines tests, 1,088 localized keys × 11 languages) backed by `syll/backend`. We want an Android client with the same behaviour, talking to the same backend contract (`backend/docs/FRONTEND_FLOW.md`). Decisions made:
- New sibling repo **`syll/android_app`** (own git repo).
- **Plain Android** (Kotlin + Compose), no KMP. Domain lives in a pure-Kotlin module (no Android imports) so KMP stays possible later.
- **Phased release**: core learning loop first; Challenge/Ranking/Custom words/Lab next; Premium (Play Billing) + Ads last because the backend has no Google Play endpoint yet.

The iOS app is the spec. It is a port, not a redesign: same screens, rules, copy and wire format. The iOS app and `docs/DESIGN_SYSTEM.md` / `docs/app-redesign-v1.html` decide when something is unclear.

## Status

### Phase 0 — Foundation (done)
- [x] Repo, Gradle modules (`app`, `core/domain`, `core/network`, `core/data`), version catalog, CI workflow
- [x] Design tokens (colors, spacing, radii, borders, typography, motion), `Motion` (= `MotionCoordinator`), theme
- [x] Core components: `ActionButton` kinds, `PrimaryButton`, `SecondaryButton` (+ `LocalActionContext`), `DestructiveButton`, `SmallButton`, `cardSurface`, `CardDivider`, `StatusScreen`
- [x] `ApiClient` + `ApiError` + `ApiJson` + `MultipartForm` + `KeystoreTokenStore`; iOS `APIClientTests` ported (MockWebServer)
- [x] `AppContainer`, `AppRoot` routing skeleton (splash → loading → auth / shell placeholders), `SplashView` on Rive
- [x] Localization converter + generated `strings_ios.xml` for 11 languages, per-app language via AppCompat
- [x] Debug design gallery: buttons, card, Rive timelines/state machines, Lottie confetti, reduce motion, languages
- [x] First build + `gradle wrapper` committed (AGP 8.10.1, compileSdk 36, Rive 11 for 16 KB page alignment)
- [x] Rive: no view models (deprecated in Rive); Syllie moods play timelines by name (`SyllieView`)
- [ ] Spike on device: on-device speech recognition (moved to Phase 1 step 5)
- [ ] Remaining shared services: `FeedbackProviding` (haptics), `SoundEffects`, `SpeechProviding` (TTS) (moved to Phase 1 step 4)

### Phase 1 — Core loop (in progress)
- [x] 1. Auth: `VocabApi`/`LiveVocabApi` (auth endpoints), `AuthStore`, `PasswordPolicy` + `PasswordRuleList`, log in / sign up + check email / reset password (2 steps), Google via Credential Manager (`-PgoogleServerClientId=…`). Change password + account deletion UI land with Profile (step 7). Apple: Phase 3.
- [x] 2. `LearnerStore` (load/profile/plan/Premium parts), `LearningPlanStore`, `TopicCatalog`, `OnboardingStore` + `OnboardingView` (welcome, level, collection, topics, routine incl. `DailySpeakingToggle`, ready), `PlacementTestStore` + `PlacementTestView`, haptics (`FeedbackProviding`). Open: reminders are saved but not scheduled yet (`NoReminders`, step 7); "Start my first lesson" opens the app until practice lands (step 4); past the free topic limit nothing offers Premium (Phase 4).
- [x] 3. Home: `MainShellView` (NavigationBar; My Words/Profile placeholders until step 7, Ranking until Phase 2), `HomeView` (Daily Lesson card + ⓘ popover, extra-review footer, Learning section, pull to refresh), `DailyLessonStatus`, `LessonMascot`/`HomeSyllieView` (timelines, no view model), `ReviewLoadChoiceSheet` + ack + new-word override, button shimmer. Open: Start/Review/Practise/Change are wired with practice (step 4) and Focus/Random practice (step 7); session follow-ups (`consumeFollowUp`) come with `PracticeSessionStore`; LP pill with Ranking.
- [ ] 4. Practice session

Deviation from the table below: settings use SharedPreferences behind `KeyValueStore` (synchronous like `UserDefaults`, which the ported stores assume) instead of DataStore.

## Stack
| iOS | Android |
|---|---|
| SwiftUI, `@Observable @MainActor` stores, `.environment` | Compose; store classes holding `StateFlow`/`mutableStateOf`, app-scoped, built once in `AppContainer` (manual DI, mirrors `VocabFlowApp.swift`), given to the UI through `CompositionLocal`s |
| `AppRootView` derives `RouteID`, swaps screens via `.id(route)` | `AppRoot` composable derives the same `RouteID` from store state, `AnimatedContent(targetState = route)`. No Navigation for root routing. Shell tabs = `Scaffold` + `NavigationBar`; pushed pages (`ShellPage`) = a small back stack in a `ShellStore` + `BackHandler` |
| `Codable` | kotlinx.serialization with custom serializers for: unknown template/variant fails the plan, unknown enum → `UNKNOWN`, optional new fields, `steps` decoded twice (`SessionPlan`) |
| `APIClient` actor (bearer, one refresh+retry on 401, backoff) | OkHttp + coroutines: `Authenticator` for the single refresh, `Mutex` around refresh, the same backoff; commit body encoded once to `ByteArray` so retries are byte-identical |
| Keychain | Android Keystore-backed AES key + DataStore (`TokenStore`) |
| `UserDefaults` | DataStore (Preferences) behind the same interfaces (`PreferencesStoring`, settings, `PendingRun`, ad counter) with in-memory fakes |
| `MotionCoordinator` | `Motion` object in a CompositionLocal: respects system "remove animations" (`Settings.Global.ANIMATOR_DURATION_SCALE` / `AccessibilityManager`) + in-app setting; `motion.perform/travel/stepTransition` equivalents |
| RiveRuntime (`syllie.riv`) | `app.rive:rive-android` — same `.riv`, same artboard / state machine / timeline names (Syllie, Loading, CelebrateV2, HomeSyllie). No view models: moods/variants are timelines or state machine inputs |
| Lottie | `lottie-compose` (same `Confetti.lottie`) |
| AVAudioRecorder 16 kHz mono PCM | `AudioRecord` → WAV 16 kHz/16-bit mono (same upload, multipart) + level metering |
| SFSpeechRecognizer on-device (free plan) | `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31+) fed the recorded file via `EXTRA_AUDIO_SOURCE` (API 33+); otherwise result `unavailable` (same path as `onDeviceRecognitionUnavailable`) |
| AVSpeechSynthesizer (`SpeechProviding`) | `TextToSpeech` en-US |
| CoreHaptics (`FeedbackProviding`) | `Vibrator`/`HapticFeedback` behind the same interface, wrapped by the settings-aware decorator |
| UserNotifications (`ReminderScheduling`) | WorkManager / AlarmManager + `POST_NOTIFICATIONS` permission |
| Sign in with Apple / Google (PKCE web) | Google: Credential Manager (`GetGoogleIdOption` with nonce) → `POST /v1/auth/google` (idToken + raw nonce). Apple: web OAuth in Custom Tab (phase 3, see risks) |
| StoreKit 2 | Play Billing Library (phase 4, needs backend) |
| GoogleMobileAds + UMP + ATT | Google Mobile Ads SDK + UMP (no ATT on Android) |
| `Localizable.xcstrings` | `res/values-*/strings.xml` generated by a script (see Localization) |

minSdk 26 (recognizer features gated at 31/33), targetSdk latest, Kotlin 2.x, Compose BOM, Gradle version catalog.

## Repo layout (`syll/android_app`)
```
app/                      Android app: AppContainer, AppRoot, features/*, designsystem/*, platform/* (audio, speech, haptics, tokens, billing, ads)
core/domain/              pure Kotlin (JVM): models, PracticeTask/TaskAttempt, MatchMadnessEngine, ChallengeBoardOrdering, ResultTiles,
                          DailyLessonStatus, PronunciationBand/Coloring, PasswordPolicy, TranscriptMatcher, AnswerEvaluator…
core/network/             pure Kotlin: ApiClient, VocabApi (+ OfflineVocabApi fake), DTOs/serializers
core/data/                Android: DataStore/Keystore implementations
tools/xcstrings2android/  localization converter (Kotlin script or Python)
CLAUDE.md, docs/
```
Shared assets copied from `ios_app/VocabFlow/Resources/`: `Animations/syllie.riv`, `Confetti.lottie`, `Sounds/*.wav` → `res/raw`, `practice_lab.json`, `results_lab.json`, image assets (export from `Assets.xcassets` to vector/WebP).

## Porting rules
- Port file by file from the iOS source, keeping type and function names (`PracticeSessionStore`, `TaskScaffold`, `DailyLessonStatus`…) so the two apps can be diffed by name.
- Pure logic goes to `core/domain` first, with its XCTest suite ported to JUnit in the same step (`VocabFlowTests/*`: TaskAttempt, PlanDecoding, PracticeSessionStore, ResultTile, ScopeReviewLoad, TodayStatus, PronunciationScore, TranscriptMatcher, MatchMadnessEngine, ChallengeBoardSlots, ChallengeRunStore, Premium, Ranking, CustomWord, AppLanguage…). Fixtures (`PracticeFixtures.swift`, parity fixture of `MatchMadnessBackendParityTests`) become JSON files under `core/domain/src/test/resources`.
- Behavioural invariants to keep exactly (commit 422s otherwise): plan decode strictness, skipped/known tasks left out of commit, `knownLearnSteps`, daily auto-continue only when `withheldReason == null`, Match Madness refill/elapsedMs/goal rules + SHA256 column ordering, frozen commit body, `PendingRun` settle on next launch.
- Design tokens: port `DesignSystem/Tokens/*` (colors ramps ink/blue/green/coral/sun/grape, spacing, radii, typography, motion durations) 1:1 into Kotlin objects; build custom components (`PrimaryButton`, `SecondaryButton` with `actionContext`, `CardSurface` with `Borders.card`, `CardDivider`, `SelectChip`, `StatusPill`, `IconButton`, `ConfirmSheet`, `FeedbackActionPanel`…) on Compose foundation — do not use Material defaults visually. Forced light theme. Platform controls stay platform: Material3 `Switch`, `NavigationBar`, top-app-bar back arrow, `ModalBottomSheet` for sheets.
- RTL (Arabic): Compose handles start/end; never use `left/right` / absolute padding.

## Localization
- Script `tools/xcstrings2android` reads `ios_app/VocabFlow/Resources/Localizable.xcstrings` and writes `values-<locale>/strings.xml`: key → snake_case resource name (keep a stable mapping file), `%@`→`%s`, `%lld`→`%d`, positional kept, plural variations → `<plurals>` with CLDR categories, escape `'`/`@`/`?`. Locales: `vi`, `en`, `hi`, `es`, `ja`, `pt-rBR`, `ko`, `b+zh+Hant`, `th`, `ar`, `in` (Indonesian). Same rule as iOS: every key in every language; a lint/unit test fails on a missing one.
- iOS remains the source of truth for strings in phase 1–2; Android-only strings go in a separate `strings_android.xml` per locale.
- In-app language picker → `AppCompatDelegate.setApplicationLocales` (per-app language, `locales_config.xml`); default = device language like `AppLanguage.device`.
- `InfoPlist.xcstrings` permission texts → Android rationale strings.

## Phases

### Phase 0 — Foundation (~2 wk)
Gradle project + modules, CI (build + unit tests + lint), `AppContainer`, `AppRoot` routing skeleton, design tokens + core components, `Motion`, localization converter, `ApiClient` + `TokenStore` + `APIConfiguration` (emulator → `http://10.0.2.2:<port>`, device → production URL, override via BuildConfig), Rive/Lottie smoke screen (Syllie artboards play), Splash.

### Phase 1 — Core loop, first release (~6–8 wk)
1. Auth: email login/sign-up/password reset (2 steps), `PasswordPolicy` + `PasswordRuleList`, Google via Credential Manager, change password, account deletion. (Backend/Supabase: add the Android OAuth client ID as allowed audience — `docs/AUTH_SETUP.md`.)
2. `LearnerStore` (`GET /me`), onboarding incl. Placement test, Routine step with `DailySpeakingToggle`.
3. Home: `DailyLessonStatus`, Daily Lesson card + ⓘ popover, `LessonMascot`/HomeSyllie, `ReviewLoadChoiceSheet` + ack, follow-ups (`close(followUp:)` / `consumeFollowUp()`).
4. Practice session: `PracticeSessionStore` (plan → steps → commit, daily chain, review-only, known check / "I know this word"), `TaskScaffold` + all 7 task views (select, clozeSelect, typing, flyReorder, match, surgery, recording), `ReviewPreviewView`, `ReviewFinishedView`, Syllie reactions.
5. Speaking: recorder, upload, scored read-aloud (score ring, `PronunciationColoring`, `PronunciationDetail`), on-device fallback, Speaking Practice from My Words.
6. Results: `SessionFinishView` (CelebrateV2 + sounds), `PracticeResultsView`, `ResultTiles`, `SessionResultDetailSheet`, "Practice again".
7. My Words, word page, Profile (routine card, review pauses, auto-balance, override), Settings (language, haptics, motion, reminders), topic progress / Focus editor.
Free-plan limits are read and enforced from `plan.limits` already in phase 1 (topic limit, `free_limit` words, on-device-only speech), but the paywall shows "Premium coming soon" / is hidden until phase 4 (`offersPremium` false on Android).

Release: internal testing → closed testing on Play Console.

### Phase 2 — Engagement features (~4 wk)
Challenge (Match Madness: map, level sheet, run store, board slots, results with `SyllieCelebrationView`), Ranking/LP/Tier, Custom words (lookup, add, edit, delete), Practice Lab + results lab (debug builds).

### Phase 3 — Sign in with Apple on Android (~1 wk)
Web flow: Apple "Services ID" + redirect via Custom Tab, returning an ID token sent to `POST /v1/auth/apple` with the raw nonce. Until then, Apple-only users are told to use "Forgot password" to create a password (already supported by backend).

### Phase 4 — Premium + Ads (~3 wk app + backend work)
- **Backend (separate change in `syll/backend`)**: `POST /v1/me/subscriptions/playstore` `{purchaseToken, productId}` verified via Google Play Developer API, Real-time Developer Notifications (Pub/Sub push) endpoint, `source: playstore`, product ids config, `obfuscatedAccountId` = user id; update `FRONTEND_FLOW.md` §16.
- App: `PurchaseStore` on Play Billing (listen to purchase updates from launch, sync entitlements after profile loads, send to backend, acknowledge only after the server answers / on owned-invalid), `PaywallView` (2 steps), `ProfileUpgradeCard`/`ProfilePremiumCard`/`PremiumPlanSheet` (manage → Play subscription center), `RedeemCodeSheet`, Grape identity.
- Ads: `AdStore` + interstitial via Google Mobile Ads + UMP consent, same rules (`features.adEverySessions`, only for limited learners, never at launch).

## Risks / open items
- On-device speech recognition on Android varies by device; without API 33 file input or an offline en-US model, free takes show `unavailable`. Acceptable per the iOS design, but measure on target devices early (Phase 0 spike).
- Rive without view models: `CelebrateV2` variants and `HomeSyllie` scenes must be reachable by timeline or state machine input; check when porting those screens.
- Play Store policy: subscriptions must be bought via Play Billing; redeem codes are allowed only if not sold outside Play — check before phase 4.
- Keeping two clients in sync: add a "parity checklist" to both CLAUDE.md files; any contract change in `FRONTEND_FLOW.md` lists iOS + Android tasks.

## Verification
- `./gradlew test` — ported domain/network suites green, including the Match Madness backend parity fixture and plan decoding fixtures; localization completeness test.
- `./gradlew connectedAndroidTest` — Compose UI tests for routing (onboarding → Home → session → results) against `OfflineVocabApi`/MockWebServer.
- Manual end-to-end on emulator + one low-end and one Pixel device against the local backend: sign up, onboarding + placement test, daily session with known check and recording, results sheet, Arabic (RTL) and Vietnamese UI, reduced motion, killed-app challenge run settle.
- Side-by-side screenshot review vs iOS for each phase's screens before release.
