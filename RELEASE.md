# Releasing Emogy

This is the short, accurate path from a clean checkout to a Play Store upload. It replaces the
older instructions that told you release builds fall back to the debug keystore: they do not.

## 1. Prerequisites

* JDK 17+ (Android Studio's bundled JBR works).
* Android SDK for `compileSdk 37`.
* An upload key. Keep it and its passwords backed up; losing them means you cannot update the app.

```powershell
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000
```

## 2. Environment

Set these in the shell that runs Gradle (never commit them):

| Variable | Required | Meaning |
|---|---|---|
| `KEYSTORE_PATH` | no | Defaults to `my-upload-key.jks` in the repo root. |
| `STORE_PASSWORD` | yes | Keystore password. |
| `KEY_PASSWORD` | yes | Key password. |
| `KEY_ALIAS` | no | Defaults to `upload`. |

```powershell
$env:KEYSTORE_PATH = "K:\keys\emogy-upload.jks"
$env:STORE_PASSWORD = "..."
$env:KEY_PASSWORD   = "..."
```

## 3. Build

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease
.\gradlew.bat clean :app:bundleRelease
```

Always run `.\gradlew.bat clean :app:bundleRelease` after the last commit and check the `.aab` timestamp is newer than the latest git commit before uploading.

Output: `app/build/outputs/bundle/release/app-release.aab`.

`bundleRelease` and `packageRelease` depend on `verifyReleaseSigning`. If the keystore or any
password is missing, the build fails with an explicit message instead of silently producing a
debug-signed artifact. Debug builds are unaffected and keep using `debug.keystore`.

## 4. Verify before uploading

```powershell
# The AAB must not be debug-signed.
jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab
```

* `testDebugUnitTest` must report 0 failures and `lintRelease` 0 issues.
* Install the release build on a phone and check: first launch, level 1, level 1000, boss level 25,
  a loss by time, a loss by moves, a win, replaying a cleared level (no second coin payout),
  background/resume, rotation, and TalkBack on the board.

## 5. Release blockers (must be resolved by a human)

Nothing in the code blocks a release. The remaining work is account, signing, asset and device work.
The full walkthrough is in `EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md`.

1. **Placeholders.** Search for `REPLACE_WITH_` and fill in developer name, support email and
   governing-law country/state in `docs/*.md` and `app/src/main/res/values/strings.xml`, then run
   `./gradlew syncLegalDocs`. `verifyReleaseContent` blocks `bundleRelease` until none are left.
2. **Public legal URL.** Publish `docs/` with GitHub Pages and paste the privacy-policy URL into Play
   Console. It must load without logging in.
3. **Upload key.** If `my-upload-key.jks` does not exist and the environment variables above are
   not set, there is no release artifact to upload. This is intentional.
4. **Store assets.** `store-listing/` has the 512x512 icon and the 1024x500 feature graphic. You still
   need 2-8 real phone screenshots from your device.
5. **Real-device QA.** Install the signed build on a phone and run the table in `EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md`.
6. **Play Console setup.** Create the app entry and complete the listing, Data safety, Ads,
   Content rating and Target audience (13+) sections using `store-listing/LISTING.md`; category
   Games > Puzzle.
7. **Testing track.** New personal developer accounts must run closed testing with 12 testers for
   14 days before production (check the current rule in Play Console).
8. **Name check.** Confirm that "Emogy" is available as a store name and does not conflict with
   an existing trademark; this cannot be checked offline.

## 6. Versioning

Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every upload. `versionCode` must
increase monotonically. The application ID `com.vurka.emogy` can never change after the first
published release.
