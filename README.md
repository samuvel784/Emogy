# Emogy - The Memory Game

Offline emoji memory-matching game: **1,000 levels, 20 themed worlds, boss levels, a winding world
map, a Daily Challenge and an Emoji Album**. Every level is playable from the first launch, in any
order; progress, stars and coins are tracked separately.
Jetpack Compose • DataStore • no network, no ads, no tracking.

## Run
1. Open the folder in Android Studio and let Gradle sync.
2. Run the `app` configuration on a device/emulator (minSdk 24).
3. Unit tests: `./gradlew :app:testDebugUnitTest`
4. Lint: `./gradlew :app:lintDebug`

## Publish
**Start with `EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md`**: setup, placeholders, tests, signing, GitHub Pages, Play Console.

| File / folder | Purpose |
|---|---|
| `EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md` | Complete setup, testing, signing, Play Store and GitHub guide |
| `RELEASE.md` | Short signing and build reference |
| `PLAY_STORE_CHECKLIST.md` | Play Console answers at a glance |
| `docs/` | Privacy Policy, Terms of Service, Credits (published with GitHub Pages) |
| `store-listing/` | Listing text, icon, feature graphic, rating and Data safety answers |
| `CHANGES.md`, `COMPETITOR_ANALYSIS.md`, `LICENSES.md` | History, market notes, audio licences |
