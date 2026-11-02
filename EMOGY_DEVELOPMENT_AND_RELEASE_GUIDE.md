# Emogy: Development and Release Guide

This guide takes you from "I just received the project" to "Emogy is live on Google Play and on GitHub".
Read sections 1 and 2 first, then work through the rest in order. Tick the boxes as you go.

Commands are written for **Windows PowerShell**. macOS/Linux equivalents are noted where they differ
(use `./gradlew` instead of `.\gradlew.bat`, `export` instead of `$env:`).

**Contents**
1. What was done, what was verified, what was not
2. What only you can provide
3. Set up your computer
4. Open and configure the project
5. Gradle sync and common problems
6. Fill in your personal details (placeholders)
7. Run the app (emulator or phone)
8. How to test every feature
9. Automated tests (unit and UI)
10. Manual test checklist
11. Finding and fixing bugs
12. Build a debug APK
13. Release signing, securely
14. Build the release APK and the signed AAB
15. Prepare the Play Store assets
16. Host the Privacy Policy and Terms (GitHub Pages)
17. Play Console: account, listing, declarations, upload
18. After you publish
19. Publish the project on GitHub
20. Project map (for maintenance)
21. Troubleshooting table
22. Final checklist before you press "Send for review"

---

## 1. What was done, what was verified, what was not

### Project state
Emogy is a fully offline Android game (Kotlin, Jetpack Compose, Material 3, DataStore):
1,000 levels in 20 worlds, boss levels, Daily Challenge, Emoji Album, daily/weekly/monthly missions,
weekly login rewards and a 30-day streak, Coin Store and cosmetics, Zen and Assist modes, original synthesised
sound effects and music, light/dark themes, TalkBack support, progress saving and 24-hour run restoration.
It has **no internet permission, no ads, no analytics, no accounts, and one permission: `VIBRATE`**.

### Work completed in this release (version 2.2, `versionCode 4`)
| Area | Change |
|---|---|
| About & Legal | New screen (More > About & Legal): version, offline **Privacy Policy**, **Terms of Service**, **Credits & Licences**, plus Share, Rate on Google Play and Contact support |
| Legal documents | `docs/privacy-policy.md`, `terms-of-service.md`, `credits.md` written to match the app's real data practices (checked against the code: what is stored, what Reset clears, backup behaviour, permissions) |
| Single source of truth | `docs/` is published with GitHub Pages and copied into `app/src/main/assets/legal/` by the `syncLegalDocs` Gradle task; a test fails if they differ |
| Release safety | `verifyReleaseContent` blocks release packaging while `REPLACE_WITH_*` placeholders remain; `verifyReleaseSigning` blocks it without your upload key |
| Policy guard tests | Tests fail if the manifest ever requests more than `VIBRATE`, requests INTERNET, or if backup rules stop covering the saved-game files, so the Privacy Policy cannot quietly become untrue |
| Cleanup | Removed unused classes (`Achievement`, `PlayerStats`, `GameResult`), an unused navigation route, and leftover AI-Studio files (`metadata.json`, `.env.example`); moved the 440 KB logo SVG into `store-listing/brand/` |
| Git hygiene | Stronger `.gitignore` (keys, APK/AAB, IDE), `.gitattributes` (line endings), optional GitHub Actions workflow |
| Store pack | `store-listing/`: 512x512 icon, 1024x500 feature graphic, listing text, content-rating and Data-safety answers |
| Tests added | `LegalMarkdownTest` (6), `LegalDocsTest` (6), `LegalScreenTest` (4, Compose UI) |

### What was actually verified (honest account)
| Check | Result |
|---|---|
| 135 existing pure-logic unit tests (catalogue, engine, snapshots, tasks, login rules, streaks, calendar, audio maths, album, rewards...) compiled and run with a standalone Kotlin 2.2 compiler and a small JUnit shim | **All passed** |
| 12 new tests (`LegalMarkdownTest`, `LegalDocsTest`) in the same harness | **All passed** |
| Syntax of the new Compose files checked by the Kotlin parser | No syntax errors |
| Full Gradle build, Android resource compilation, lint, R8 release build | **NOT run** (no Android SDK in my environment) |
| Robolectric/Compose UI tests (including the new `LegalScreenTest`) | **NOT run** |
| Running on an emulator or phone, listening to audio, TalkBack, haptics | **NOT done** |
| Version numbers of AGP 9.4.1, Kotlin 2.4.21, Gradle 9.8.2, Compose BOM 2026.09.00, SDK 37 | Taken from your project; **not independently checked** |

**Therefore your first Android Studio build is the real compile check.** If anything fails, copy the full error
text and ask for a fix. Do not publish before sections 9 and 10 pass on your own machine and phone.

---

## 2. What only you can provide

| Item | Why | Where it goes |
|---|---|---|
| Your **developer name** | Legal pages and Play account | `REPLACE_WITH_DEVELOPER_NAME` in `docs/*.md` |
| A **support email** you read | Privacy policy, Terms, Play listing, Contact button | `REPLACE_WITH_SUPPORT_EMAIL` in `docs/*.md` **and** `app/src/main/res/values/strings.xml` |
| **Governing-law** country/state | Terms of Service | `REPLACE_WITH_COUNTRY_OR_STATE` in `docs/terms-of-service.md` |
| A **Google Play developer account** (one-time fee, currently US$25) and identity verification | Needed to publish | play.google.com/console |
| Your **upload keystore** and passwords | Signing releases | You create it in section 13. Never share it |
| A **GitHub account** and repository | Hosting code and the policy page | Section 16 and 19 |
| **Real screenshots** (2 to 8) | Play requires them | Section 15 |
| **Name check** for "Emogy" | Trademark/store-name availability cannot be checked offline | Search Google Play, your trademark office, EUIPO, USPTO |
| **Source-code licence decision** (if the repo is public) | Without a licence, others have no right to reuse the code | Section 19 |
| **Target audience decision** | Docs assume 13+; choosing under-13 triggers Google's Families policy | Section 17 |
| A **legal review** if you want certainty | The legal pages are carefully drafted for this app but I am not a lawyer | Optional but recommended for a commercial release |

---

## 3. Set up your computer

- [ ] Install **Android Studio** (latest stable): developer.android.com/studio
- [ ] Open **Settings > Languages & Frameworks > Android SDK**: install the **Android 37** platform (`compileSdk 37`), the latest **Build-Tools**, **Platform-Tools** and **Emulator**.
- [ ] Set **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK** to the embedded JDK (`jbr`).
- [ ] Install **Git** (git-scm.com). Check with `git --version`.
- [ ] Have a stable internet connection for the first sync (Gradle and libraries download once).
- [ ] At least 10 GB free disk space and 8 GB RAM recommended.

---

## 4. Open and configure the project

1. Unzip the project somewhere without special characters in the path (for example `D:\Projects\Emogy`).
2. Android Studio > **Open** > select the folder that contains `settings.gradle.kts`.
3. Trust the project when asked. Wait for **Gradle sync** (bottom status bar) to finish.
4. If asked to create `local.properties`, accept (it stores your SDK path and is ignored by Git).
5. Select the `app` run configuration and a device at the top.

---

## 5. Gradle sync and common problems

| Symptom | Fix |
|---|---|
| "Unsupported class file major version", Java toolchain or JDK 25 download errors | Set Gradle JDK to the embedded `jbr`. `gradle/gradle-daemon-jvm.properties` requests a JDK 25 toolchain; if your network blocks the download, delete that file and sync again |
| "SDK location not found" | Create `local.properties` with `sdk.dir=C\:\\Users\\YOU\\AppData\\Local\\Android\\Sdk` |
| "Failed to find Platform 37" / "Failed to install the following Android SDK packages" | Install the Android 37 platform in the SDK Manager |
| "Could not resolve ..." | Check internet/proxy; **File > Invalidate Caches > Invalidate and Restart**; run `.\gradlew.bat --refresh-dependencies` |
| Plugin or library version not found | Versions are pinned in `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`. Update Android Studio to the latest stable, or ask for help with the exact error |
| Out of memory during build | In `gradle.properties` raise `org.gradle.jvmargs=-Xmx4g` to `-Xmx6g` |
| "Configuration cache" errors | Run once with `--no-configuration-cache` and send the first error message |
| Build is slow | First build is slow; later builds use the cache |

---

## 6. Fill in your personal details (placeholders)

1. In Android Studio press **Ctrl+Shift+F** (Find in Files) and search `REPLACE_WITH_`.
2. Replace the three values listed in section 2 in these files only (the search will also hit this guide, `app/build.gradle.kts`, the tests and `store-listing/LISTING.md`; those mention the token on purpose, leave them alone):
   - `docs/privacy-policy.md`, `docs/terms-of-service.md`, `docs/index.md`
   - `app/src/main/res/values/strings.xml` (the `support_email` string)
3. Copy the docs into the app:
   ```powershell
   .\gradlew.bat syncLegalDocs
   ```
   (It also runs automatically before each build.)
4. Search again: **zero** `REPLACE_WITH_` results must remain.
5. Read both legal documents once. If you add features later (ads, analytics, accounts, notifications, cloud save), update them **first**.

Debug builds and tests work with placeholders in place; **release builds refuse to package** until they are gone.

---

## 7. Run the app

### Emulator
**Tools > Device Manager > Create Device** (for example Pixel 8, recent system image) > press the green **Run** button.

### Physical phone
1. Phone: **Settings > About phone**, tap **Build number** seven times.
2. **Developer options > USB debugging** on. Connect by USB, accept the prompt.
3. Select the phone in Android Studio and press **Run**.

Expected: Home screen with a big PLAY button and a bottom bar with Home, Map, Daily, Album, More.

---

## 8. How to test every feature

| Feature | How to test | Expected |
|---|---|---|
| First run | Fresh install | Welcome sheet once; main button says **PLAY** until you flip a card |
| Levels | Map tab > any level > Play | All 1,000 levels open; boss levels every 25th; preview card shows rules |
| Gameplay | Play level 1 | Flip two cards; matches stay; mismatches flip back; stars at the end |
| Time/move limits | Try a high level and wait | Correct "Time's up" / "Out of moves" dialog; Retry works |
| Hints & Assist | Lightbulb button; enable Assist in Settings and use the heart | Hint peeks at cards; Assist reveals a pair without penalty |
| Pause/resume | Press Home during a level, then return | Game is paused, music silent in the background |
| Run restore | Swipe the app away mid-level, reopen | Level restores (valid for 24 hours) |
| Daily Challenge | Daily tab > Play | Same 8-pair board all day; reward once; replays improve best time only |
| Emoji Album | Match pairs, open Album tab | Matched emoji unlock; counters update |
| Tasks & Rewards | More > Tasks & Rewards | Daily/weekly/monthly missions progress; Claim adds coins/hints; weekly login claim once per day |
| Coin Store & Cosmetics | More > Coin Store / Cosmetics | Purchases reduce coins, cannot go negative, items can be equipped; locked items show requirements |
| Statistics | More > Statistics | Levels completed, stars, streak, games played |
| Settings | More > Settings | Master/Music/Effects sliders, haptics, Zen, Assist, theme, preview sounds; Reset asks for confirmation |
| About & Legal | More > About & Legal | Version shown; Privacy Policy, Terms, Credits open and scroll; Back returns to list |
| Share / Rate / Contact | Buttons on About & Legal | Share sheet opens; Play Store (or its web page) opens; mail app opens addressed to your support email |
| Offline | Turn on Airplane mode | Everything works |
| Accessibility | TalkBack on; Remove animations on | Labels read sensibly; hidden cards say "Hidden card"; motion calms down |

---

## 9. Automated tests

### What exists
| Kind | Where | Needs |
|---|---|---|
| Unit tests (pure logic, repositories on fake DataStore, game engine, view model) | `app/src/test/...` | Computer only |
| Compose UI tests on Robolectric (screens, navigation, bottom bar, welcome sheet, About & Legal) | `app/src/test/...` | Computer only |
| Instrumented test (package name check) | `app/src/androidTest/...` | Emulator/phone |

### Run from Android Studio
Right-click `app/src/test` > **Run Tests in 'test'**. Or right-click a single test class > Run.

### Run from the command line
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest             # all unit + Robolectric tests
.\gradlew.bat :app:testDebugUnitTest --tests "com.vurka.emogy.LegalDocsTest"   # one class
.\gradlew.bat :app:lintDebug                      # static analysis
.\gradlew.bat :app:connectedDebugAndroidTest      # needs a running emulator or connected phone
```
Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html`.

### What "good" looks like
- 0 failed tests. If one fails, open the HTML report, read the first failure message and stack trace.
- Lint: no errors. Warnings should be reviewed but do not block.

### Tests added in this release
- `LegalMarkdownTest`: the document renderer (headings, bullets, bold, code, links, Windows line endings).
- `LegalDocsTest`: docs equal the in-app copies; privacy policy and terms contain the sections Play reviewers look for; only the three known placeholders exist; **manifest requests only VIBRATE and no INTERNET**; backup rules cover both DataStore files.
- `LegalScreenTest`: version line, three documents open offline, Back returns to the list.

If `LegalDocsTest.inAppCopiesMatchThePublishedDocs` fails, run `.\gradlew.bat syncLegalDocs`.

---

## 10. Manual test checklist (30 to 45 minutes, on a real phone, release build)

Automated tests cannot judge sound, feel or accessibility. Do this on the **release** build (section 14), not only on debug.

| # | Check | OK |
|---|---|---|
| 1 | Fresh install: Welcome sheet appears once; button says PLAY | [ ] |
| 2 | Win level 1 with 3 stars; victory screen offers Next, Replay, World Map, Home | [ ] |
| 3 | Lose by time and by moves; the right message shows; Retry works | [ ] |
| 4 | Play boss level 25 and level 1000 | [ ] |
| 5 | Replay a cleared level: no second coin payout | [ ] |
| 6 | Daily Challenge: complete, reward once, replay allowed | [ ] |
| 7 | Album fills in; a mission can be claimed once only | [ ] |
| 8 | Buy and equip a cosmetic; coins never negative | [ ] |
| 9 | Settings: sliders change loudness; mute silences everything; Reset asks for confirmation and keeps your audio/haptics settings | [ ] |
| 10 | About & Legal: all three documents open; Share, Rate, Contact work; your real email appears | [ ] |
| 11 | Home button mid-level, wait 1 minute, return: paused, no music in background | [ ] |
| 12 | Swipe the app away mid-level, reopen: run restored | [ ] |
| 13 | Rotate the phone; switch light/dark; pull the notification shade | [ ] |
| 14 | TalkBack on: menus readable; face-down cards say "Hidden card" | [ ] |
| 15 | System **Remove animations** on: confetti/motion calm | [ ] |
| 16 | Unplug headphones or receive a call: music pauses | [ ] |
| 17 | Largest system font size: no clipped text | [ ] |
| 18 | Airplane mode: everything still works | [ ] |
| 19 | Change the phone date to **yesterday** and reopen: no free rewards (clock protection); set the date back to automatic afterwards | [ ] |
| 20 | Install the release build **over** an older build: progress is kept | [ ] |

Record every bug with steps to reproduce, then fix it (section 11) and re-run section 9.

---

## 11. Finding and fixing bugs

1. **Reproduce** it and write down exact steps.
2. **Read the log**: Android Studio > **Logcat**; choose your device and the `com.vurka.emogy` process; filter `level:error`. A crash shows `FATAL EXCEPTION` and a stack trace. Click the first line that mentions `com.vurka.emogy` to jump to the code.
3. **Write a failing test first** when it is logic (most rules live in `data/`, `game/` and `viewmodel/` and are easy to test).
4. **Fix**, then run `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug`.
5. **Release-only bugs** (work in debug, break in release) usually come from R8 shrinking: run `.\gradlew.bat :app:assembleRelease` and test that APK. The project has no reflection libraries, so these should be rare.
6. **Crashes from real users**: Play Console > **Quality > Android vitals > Crashes and ANRs**. Stack traces are de-obfuscated automatically because the AAB includes the R8 mapping file.
7. Typical areas to watch in this project:
   - Clock-dependent rewards (`data/time/GameCalendar`, `data/login`, `data/tasks`): test with the phone date changed.
   - Audio (`util/SoundManager`, `MusicManager`): first launch generates sounds in the background; test cold start on a low-end phone.
   - Saved data (`data/ProgressRepository`, `GameSnapshot`): the DataStore file is `memory_match_prefs`; **never rename keys without a migration**.
   - Navigation (`MainActivity`, `ui/navigation`): five tab roots; everything else is a secondary screen.

Useful commands: `adb logcat -s AndroidRuntime` (crashes), `adb shell pm clear com.vurka.emogy` (wipe app data for a fresh-install test).

---

## 12. Build a debug APK

Good for quick tests and sharing with a few friends (not for Play Store).

```powershell
.\gradlew.bat :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`. Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk` or copy to the phone and open it (allow "install unknown apps").
Android Studio alternative: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

---

## 13. Release signing, securely

### How signing works here
`app/build.gradle.kts` reads the keystore **from environment variables, never from source files**:

| Variable | Required | Meaning |
|---|---|---|
| `STORE_PASSWORD` | yes | Keystore password |
| `KEY_PASSWORD` | yes | Key password |
| `KEYSTORE_PATH` | no | Defaults to `my-upload-key.jks` in the project root |
| `KEY_ALIAS` | no | Defaults to `upload` |

A release build **can never fall back to the public debug key**: `verifyReleaseSigning` stops it.

### Create your upload key (once)
```powershell
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
(`keytool.exe` is in `C:\Program Files\Android\Android Studio\jbr\bin`.) Choose strong passwords. Enter your name when asked.

### Protect it
- [ ] Keep `my-upload-key.jks` **outside** any public folder (or at least ignored by Git: `*.jks` is already in `.gitignore`).
- [ ] Back it up in **two** places (for example an encrypted USB drive and a password manager's secure file storage).
- [ ] Store both passwords in a password manager. Never put them in code, README, chat or a commit.
- [ ] Use Play App Signing (default). Google holds the final app-signing key; your keystore is only the *upload* key, which Google can reset if you lose it.

### Provide passwords without saving them in your shell history
PowerShell:
```powershell
$p = Read-Host "Keystore password" -AsSecureString
$env:STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))
$p = Read-Host "Key password" -AsSecureString
$env:KEY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))
$env:KEYSTORE_PATH = "D:\keys\emogy-upload.jks"   # optional
```
macOS/Linux: `read -s -p "Keystore password: " STORE_PASSWORD; export STORE_PASSWORD` (and the same for `KEY_PASSWORD`).

These variables live only in that terminal window. Use that same window to run Gradle (do not use Android Studio's "Generate Signed Bundle" wizard: this project's checks read the environment variables).

---

## 14. Build the release APK and the signed AAB

Before building: placeholders filled (section 6), tests green (section 9), `versionCode` raised if you uploaded before.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease
.\gradlew.bat :app:assembleRelease      # release APK (signed with your upload key)
.\gradlew.bat :app:bundleRelease        # release AAB for Google Play
```
Outputs:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab` (this is what you upload to Play)

### Verify
```powershell
jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab
```
Expected: `jar verified` and your certificate details (not "Android Debug"). A signature warning about timestamps is normal.

### Test the release build
```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```
Run the manual checklist (section 10) on this build.

### If the build stops with a message
| Message | Meaning |
|---|---|
| "Release signing is not configured. Missing: ..." | Keystore file or one of the passwords is missing (section 13) |
| "Placeholders are still present in: ..." | Do section 6 |
| Lint errors | Open `app/build/reports/lint-results-release.html`, fix the listed errors |

### Version numbers
In `app/build.gradle.kts`: `versionCode` must increase with **every** upload (4, 5, 6...). `versionName` is the text users see (2.2, 2.3...). Note changes in `CHANGES.md`. The application ID `com.vurka.emogy` can **never** change after the first publish.

---

## 15. Prepare the Play Store assets

Everything is in `store-listing/`.

| Asset | File / action | Requirement |
|---|---|---|
| App icon | `store-listing/play-icon-512.png` | 512x512 PNG, up to 1 MB |
| Feature graphic | `store-listing/feature-graphic-1024x500.png` | 1024x500 |
| Phone screenshots | **You capture them** | 2 to 8, 16:9 or 9:16, each side 320 to 3,840 px |
| Texts | `store-listing/LISTING.md` | Title <= 30, short description <= 80, full description <= 4,000 characters |
| Brand source | `store-listing/brand/app_logo.svg` | For future re-exports |

### Capture screenshots
- Emulator: the camera icon in the emulator toolbar, or `adb exec-out screencap -p > shot1.png`.
- Phone: press power + volume down, then copy the images to your computer.
- Use the **release** build, a fully charged-looking status bar, English language, no personal notifications.
- Suggested set: Home, World Map, a level in progress, Victory, Daily Challenge, Emoji Album, Coin Store, Settings.
- Only show features that exist. Do not add misleading text overlays.

---

## 16. Host the Privacy Policy and Terms (GitHub Pages)

Google needs a **public web link** to your privacy policy.

1. Finish section 6 (real email, name, country) and commit.
2. Push the project to GitHub (section 19).
3. GitHub repo > **Settings > Pages > Build and deployment > Source: Deploy from a branch > Branch `main`, folder `/docs` > Save**.
4. After 1 to 3 minutes the pages are live at:
   - `https://YOUR_USERNAME.github.io/YOUR_REPO/privacy-policy`
   - `https://YOUR_USERNAME.github.io/YOUR_REPO/terms-of-service`
5. Open the privacy link in a **private/incognito window**: it must load without login and show your real email.

(If your repo is private, GitHub Pages needs a paid plan: keep the repo public or use another host such as Google Sites.)

---

## 17. Play Console: account, listing, declarations, upload

### 17.1 Account
1. play.google.com/console > sign up as a developer, pay the one-time registration fee, verify identity as asked.
2. **Create app**: name `Emogy: The Memory Game`, language English, **Game**, **Free**, accept declarations.

### 17.2 Dashboard tasks (use `store-listing/LISTING.md`)
- [ ] **Privacy policy**: paste your public URL.
- [ ] **App access**: "All functionality is available without special access."
- [ ] **Ads**: "No, my app does not contain ads."
- [ ] **Content rating**: run the questionnaire with the answers in `LISTING.md` (no violence, no gambling, no user interaction, no purchases). Expect the lowest age rating.
- [ ] **Target audience**: **13 and over**. (Choosing under 13 enrols the app in the Families policy: more review and requirements.)
- [ ] **Data safety**: "No data collected, no data shared" (answers and reasoning in `LISTING.md`). The app has no internet permission and no SDKs, and Android's own device backup is not "collection" by the app.
- [ ] **Government / financial / health / news / COVID**: not applicable.
- [ ] **Store listing**: texts, icon, feature graphic, screenshots, category **Games > Puzzle**, contact email.

### 17.3 Upload and test
1. **Testing > Internal testing > Create release**: accept Play App Signing, upload `app-release.aab`, add release notes, add your own Gmail as tester, roll out. Install via the opt-in link and run section 10 again.
2. **Closed testing**: new personal developer accounts typically must run a closed test with **at least 12 testers for 14 days in a row** before they may apply for production (check the exact current rule in Play Console). Ask friends/family for Gmail addresses, add them, share the opt-in link, keep the test running and fix reported issues with new builds (higher `versionCode`).
3. **Production > Create release**: choose the tested build, paste release notes, set countries, **Start rollout** (a staged rollout such as 20% first is a safe choice).
4. Review takes hours to several days. Watch your email and the **Inbox** in Play Console.

### 17.4 Policy checks that apply to Emogy
- Declared permissions match behaviour (only `VIBRATE`).
- No misleading listing: do not call levels "unlockable" (all are open); do not imply real-money purchases (coins cannot be bought).
- No ads, trackers, or personal data collection: if you add any, update the policy and Data safety first.
- Target API level is current (`targetSdk 37`). Google raises the minimum every year: raise `targetSdk` and re-test when asked.
- Sounds are original (see `LICENSES.md`); emoji come from the device font; the logo is your own artwork (confirm you own all rights).

---

## 18. After you publish

- [ ] Check **Android vitals** (crashes, ANRs) weekly in the first month.
- [ ] Reply to reviews.
- [ ] Keep the upload key and passwords backed up.
- [ ] For each update: raise `versionCode`, update `CHANGES.md`, re-run sections 9, 10, 14.
- [ ] Never add ads, analytics, accounts, notifications or any internet feature without updating the Privacy Policy, Terms and the Data safety form **before** shipping.
- [ ] Review Google Play policy emails promptly.

---

## 19. Publish the project on GitHub

### 19.1 What gets committed
```
Emogy/
  app/                      Android module (src/main, src/test, src/androidTest, build.gradle.kts)
  docs/                     Privacy Policy, Terms, Credits, GitHub Pages config  (published)
  gradle/                   Version catalogue and wrapper
  store-listing/            Listing texts, icon, feature graphic, brand source
  .github/workflows/        Optional CI (untested template)
  EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md   This guide
  README.md  CHANGES.md  RELEASE.md  PLAY_STORE_CHECKLIST.md  LICENSES.md  COMPETITOR_ANALYSIS.md
  .gitignore  .gitattributes  build.gradle.kts  settings.gradle.kts  gradle.properties
  gradlew  gradlew.bat
```
**Never committed** (blocked by `.gitignore`): `*.jks`, `*.keystore`, `local.properties`, `*.apk`, `*.aab`, `build/`, `.idea/`, `.gradle/`.

### 19.2 Before the first push: safety check
```powershell
cd D:\Projects\Emogy
git status
git ls-files | Select-String -Pattern "jks|keystore|local.properties|\.apk|\.aab"    # must print nothing
Select-String -Path docs\*.md,app\src\main\res\values\strings.xml -Pattern "REPLACE_WITH_"   # must print nothing
```
Note: the old public **debug** keystore appears in this repository's early history. It is Android's generic debug key (password `android`) and gives nobody access to your Play account, but never use it for releases (the build forbids it). Your **upload** key was never in the repo.

### 19.3 Create the repository on GitHub
GitHub > **New repository** > name `emogy` > **Public** > do **not** add README/.gitignore/licence (they exist) > Create.

### 19.4 Connect and push
This project already has Git history that points at the old "Memoji" repository. Re-point it:
```powershell
git config user.name  "Your Name"
git config user.email "your-github-noreply-or-email"
git remote set-url origin https://github.com/YOUR_USERNAME/emogy.git     # or: git remote add origin ...
git add --renormalize .          # applies the new .gitattributes line-ending rules once
git add -A
git status                       # review the list one last time
git commit -m "Emogy 2.2: legal pages, About screen, release checks, store pack"
git branch -M main
git push -u origin main
```
Authentication: GitHub does not accept account passwords for `git push`. Sign in through the browser prompt of **Git Credential Manager** (installed with Git for Windows), or use the GitHub CLI (`gh auth login`), or a Personal Access Token.

Optional: tag the release you uploaded to Play:
```powershell
git tag -a v2.2 -m "Emogy 2.2 (versionCode 4)"
git push origin v2.2
```

### 19.5 README
`README.md` is included (summary, how to run, links to this guide). Add screenshots under `docs/images/` and reference them if you like.

### 19.6 Licence decision (yours)
If the repository is public and has no licence file, others may read it but have **no right** to reuse it. If you want that, do nothing. If you want to allow reuse, add a `LICENSE` file (for example MIT or Apache-2.0 from choosealicense.com). The Terms of Service say the app may not be copied "except as allowed by an open-source licence that applies to the source code", so they stay consistent either way. Your artwork and logo can be excluded from the licence by stating so in the README.

### 19.7 GitHub Pages and CI
Pages: section 16. CI: `.github/workflows/android-ci.yml` runs unit tests and lint on each push. **It was written but never executed**; if a run fails, read the log, adjust the Java version (the project's own daemon file asks for JDK 25), and push again. Delete the file if you do not want CI.

### 19.8 Day-to-day workflow
```powershell
git checkout -b fix/short-description
# edit, test (section 9)
git add -A ; git commit -m "Fix: describe what and why"
git push -u origin fix/short-description     # open a Pull Request on GitHub, merge, then:
git checkout main ; git pull
```

---

## 20. Project map (for maintenance)

| Package (`app/src/main/java/com/vurka/emogy/`) | Responsibility |
|---|---|
| `MainActivity.kt` | Navigation host, audio lifecycle, daily check-in |
| `ui/home`, `ui/levels`, `ui/daily`, `ui/album`, `ui/more` | The five bottom-bar tabs |
| `ui/game` | Board, HUD, confetti, victory/defeat dialogs |
| `ui/store`, `ui/themes`, `ui/tasks`, `ui/stats`, `ui/settings` | Secondary screens |
| `ui/legal` | About & Legal (new) |
| `ui/theme`, `ui/components` | Design tokens, colours, shared bars and sheets |
| `viewmodel` | `GameViewModel` (run rules, timer, snapshot), `LevelSelectionViewModel` (app-wide state) |
| `game` | `MemoryGameEngine`, assist rules (pure logic) |
| `data` | Repositories on DataStore, level/emoji catalogues, daily board, snapshot codec |
| `data/tasks`, `data/login`, `data/store`, `data/time` | Missions, login rewards/streak, shop transactions, calendar maths |
| `util` | Sound, music, haptics, glyph filter, `LegalMarkdown` |
| `app/src/main/assets/legal` | Offline copies of the legal text (generated from `docs/`) |
| `app/src/main/res/values/strings.xml` | All UI text (English) |

Rules worth keeping: use the single `gameDataStore` (two DataStore delegates on one file crash); never rename a stored key without a migration; keep catalogue content in Kotlin and UI chrome in `strings.xml`; keep the Privacy Policy and manifest in sync (tests enforce part of this).

---

## 21. Troubleshooting table

| Problem | Likely cause / fix |
|---|---|
| App installs but is "not installed" on update | Signed with a different key than the installed copy: uninstall first (debug vs release) |
| "INSTALL_FAILED_VERSION_DOWNGRADE" | Installed build has a higher `versionCode`: uninstall or raise yours |
| Play Console: "Version code already used" | Raise `versionCode` and rebuild |
| Play Console: "You uploaded a debug-signed bundle" | You built without release signing; follow sections 13 and 14 |
| Play Console: privacy policy URL not reachable | Pages not yet deployed or repo private; test in an incognito window |
| No sound on a device | Check Settings > Sound and the three sliders; sounds generate in the background on first launch |
| Share/Rate/Contact does nothing | No handling app installed (for example no mail app); the app ignores this safely |
| Tests fail only on your computer | Check the JDK (use `jbr`), then re-run with `--stacktrace` and read the first failure |
| Gradle task `syncLegalDocs` fails | `docs/` folder missing or a file renamed; restore the three files |

---

## 22. Final checklist before "Send for review"

- [ ] Zero `REPLACE_WITH_` left; legal pages live on a public URL
- [ ] Unit/UI tests: 0 failures. `lintRelease`: no errors
- [ ] Signed `app-release.aab` verified with `jarsigner` (not debug-signed)
- [ ] Release APK tested on a real phone with section 10
- [ ] 2 to 8 real screenshots, icon, feature graphic uploaded
- [ ] Data safety, content rating, target audience, ads and app access completed and consistent with the Privacy Policy
- [ ] Name "Emogy" checked for availability and trademarks
- [ ] Closed test requirement completed (if your account type needs it)
- [ ] Upload key and passwords backed up in two places
- [ ] Source pushed to GitHub, no secrets in `git ls-files`
