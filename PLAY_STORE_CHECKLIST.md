# Google Play checklist for Emogy

The detailed walkthrough is in `EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md`; copy-paste texts and form answers are in
`store-listing/LISTING.md`. This page is the one-screen summary.

## 0. Launch blockers at a glance

Nothing in the code blocks a release. What remains is account, signing, asset and device work.

| # | Item | Status / what to do |
|---|---|---|
| 1 | **Placeholders** | Search for `REPLACE_WITH_` and fill in developer name, support email and governing-law country/state (`docs/*.md`, `strings.xml`). Run `./gradlew syncLegalDocs`. The release build refuses to package until this is done. |
| 2 | **Public privacy policy URL** | Publish `docs/` with GitHub Pages and paste `https://<user>.github.io/<repo>/privacy-policy` into Play Console. Must load without login. |
| 3 | **Upload key** | Create `my-upload-key.jks`, set `STORE_PASSWORD` and `KEY_PASSWORD`, build `app-release.aab`. See `RELEASE.md`. |
| 4 | **Store graphics** | Icon and feature graphic are in `store-listing/`. You still need 2-8 real phone screenshots. |
| 5 | **Real-device QA** | Run the manual table in `EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md` on a phone with the signed release build. |
| 6 | **Play Console setup** | Listing, Data safety, Ads, Content rating, Target audience (13+), category Games > Puzzle. |
| 7 | **Closed testing** | New personal accounts usually need 12 testers for 14 days before production. Check the current rule in Play Console. |
| 8 | **Name/trademark check** | Confirm "Emogy" is free to use as a store name. Cannot be checked offline. |

## 1. Signing

Release builds **never** fall back to `debug.keystore`. `packageRelease` / `bundleRelease` depend on
`verifyReleaseSigning` (upload key and passwords present) and `verifyReleaseContent` (no placeholders
left). Set `STORE_PASSWORD` and `KEY_PASSWORD` (optionally `KEYSTORE_PATH`, `KEY_ALIAS`) and run
`./gradlew :app:bundleRelease` -> `app/build/outputs/bundle/release/app-release.aab`.
Keep the .jks and passwords backed up and **never commit them**. Enrol in Play App Signing when asked.

## 2. App facts

* Package: `com.vurka.emogy` (cannot change after first publish).
* Version: `versionCode 4`, `versionName 2.2`. Raise `versionCode` for every upload.
* `targetSdk 37`, `compileSdk 37`, `minSdk 24`; pure Kotlin/Compose, no native code.
* Release has R8 and resource shrinking enabled.
* Only permission: `VIBRATE`. No INTERNET. Guarded by `LegalDocsTest`.
* All 1,000 levels are playable from first launch; progress never gates level access. Do not describe
  levels as "unlockable" in the listing.

## 3. Play Console answers

* **Data safety:** "No data collected", "No data shared".
* **Ads:** No. **In-app purchases:** none (coins are earned in game and cannot be bought).
* **Account creation / user-generated content:** none.
* **App access:** everything available without login.
* **Content rating (IARC):** no violence, gambling or user interaction -> lowest rating expected.
* **Target audience:** 13 and over; do not enrol in the Families programme.
* **Category:** Games > Puzzle.
* **Data deletion:** not applicable (no account, no server). Players clear data via Settings > Reset
  All Game Progress or Android > Clear storage.

## 4. Before every upload

* `./gradlew :app:testDebugUnitTest :app:lintRelease` with 0 failures.
* `jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab` shows your key.
* Release build tested on a real phone, including TalkBack and "Remove animations".
* Pre-launch report in Play Console reviewed.

## OWNER TODO
1. Replace "United States" governing law in `docs/terms-of-service.md` with a specific country/state.
2. Confirm `support@vurka.com` is a monitored inbox and matches the Play developer profile.
3. Confirm GitHub Pages serves `docs/privacy-policy` publicly and paste that URL in Play Console.
4. Capture 2-8 phone screenshots.
5. New personal developer accounts must run a closed test (12+ testers, 14 days) before production — verify the current requirement in Play Console.
6. Never share the project folder/ZIP because it contains the upload keystores; back up the key securely.
