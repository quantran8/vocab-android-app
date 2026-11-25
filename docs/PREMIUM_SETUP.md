# Premium and ads setup (Google Play)

The Android app sells Syll Premium only through Google Play Billing; the backend verifies every purchase and listens to Real-time Developer Notifications. Contract: `backend/docs/FRONTEND_FLOW.md` §16.4. iOS counterpart: `ios_app/docs/PREMIUM_SETUP.md` and `ADS_SETUP.md`.

## Play Console

1. **Payments profile** active (merchant account), or products never load.
2. **Subscriptions** (Monetize → Subscriptions), each with one auto-renewing base plan:
   - `syll.premium.yearly` (base plan 1 year); an optional free-trial offer on it
   - `syll.premium.monthly` (base plan 1 month)
   The ids must match `PremiumProduct` (`core/domain/premium`) and the backend's `PLAYSTORE_PRODUCT_IDS`. The paywall sells a free-trial offer when Play lists one for the account, else the base plan.
3. **Real-time developer notifications** (Monetize → Monetization setup): a Pub/Sub topic with a **push** subscription to `https://<backend host>/v1/playstore/notifications`, authentication enabled (a service account; its audience → `PLAYSTORE_PUSH_AUDIENCE`, its email → `PLAYSTORE_PUSH_SERVICE_ACCOUNT`). "Send test notification" should answer 200.
4. **API access:** a service account with "View financial data" and "Manage orders and subscriptions"; its JSON key → `PLAYSTORE_SERVICE_ACCOUNT_JSON`.
5. **License testing:** add tester accounts; test purchases renew every few minutes.

## App build

| Gradle property | Meaning |
| --- | --- |
| `-PadmobAppId=…` | AdMob app ID (manifest). Default: Google's **test** app ID |
| `-PadmobInterstitialUnitId=…` | Interstitial unit. Default: Google's **test** unit |
| `-PtermsUrl=…`, `-PprivacyPolicyUrl=…` | Links under the plans; empty leaves the link out |

Replace both AdMob test IDs before release. In AdMob, create a GDPR message under Privacy & messaging (the app shows it through UMP before any ad request) and publish `app-ads.txt`. Play Console → App content: declare ads and the advertising ID (the SDK adds `AD_ID`).

## Backend

`FREEMIUM_ENABLED`, `REDEEM_ENABLED`, `AD_EVERY_SESSIONS` as on iOS, plus `PLAYSTORE_PACKAGE_NAME` (`com.syll`), `PLAYSTORE_PRODUCT_IDS`, `PLAYSTORE_SERVICE_ACCOUNT_JSON`, `PLAYSTORE_PUSH_AUDIENCE`, `PLAYSTORE_PUSH_SERVICE_ACCOUNT` (backend `DEPLOYMENT.md`).
