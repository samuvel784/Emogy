# Emogy v2.2.1 - release maintenance

## Bug fixes & enhancements
* **Monthly missions UI**: Clarified in `strings.xml` that monthly progress and claims close on day 28 (%1$s) and unclaimed monthly rewards expire when the month ends.
* **Version bump**: Updated `versionCode = 5` and `versionName = "2.2.1"`.
* **Release signing hygiene**: Removed fallback default passwords ("android") from release `signingConfig` in `app/build.gradle.kts`.
* **ProgressRepository fixes**: Safe stars (`safeStars`) passed to `LevelRewardCalculator.calculate`; non-negative `moves` and `timeSec`; fixed two functions on one line; added unit test.
* **SoundManager init/release race**: `release()` resets `preparing = false` in synchronized block; late-finishing init cannot resurrect released pool.
* **Card tap DataStore write reduction**: Memory batching for flip counts during active gameplay, writing to DataStore once per pair, on pause, or on game end.
* **Store listing and legal docs**: Updated `store-listing/LISTING.md` Mystery Box wording and added OWNER TODO list in `PLAY_STORE_CHECKLIST.md`.
* **Stale-build guard**: Added clean build instruction to `RELEASE.md`.

---

# Emogy v2.2 - release readiness

## About & Legal (new)
* **More > About & Legal** shows the app version, and offline copies of the **Privacy Policy**,
  **Terms of Service** and **Credits & Licences**, rendered by a small pure-Kotlin Markdown reader
  (`LegalMarkdown`, unit-tested). Share, Rate on Google Play and Contact support hand off to other
  apps; none needs the INTERNET permission.
* `docs/` is the single source of truth for the legal text. `syncLegalDocs` copies it into
  `app/src/main/assets/legal` before every build; `LegalDocsTest` fails if the two differ.
* `docs/_config.yml` makes the same files publishable with GitHub Pages.
* The privacy policy now also covers rewards, hints, owned cosmetics, task and login state, the
  generated audio cache and the Reset behaviour exactly as implemented.
* New `Terms of Service` and `Credits` pages.

## Release safety
* `verifyReleaseContent` blocks `packageRelease` / `bundleRelease` while any `REPLACE_WITH_*`
  placeholder remains in the legal pages or the support address.
* `LegalDocsTest` also guards that the manifest requests only `VIBRATE` and that both DataStore
  files are covered by the backup rules, so the privacy policy cannot silently become untrue.
* Version bumped to `versionCode 4` / `versionName 2.2`.

## Cleanup and project hygiene
* Removed unused code: `Achievement`, `PlayerStats`, `GameResult` and the unused `Screen.LevelSelect` route.
* Removed leftover AI-Studio files (`metadata.json`, `.env.example`); moved the logo SVG to `store-listing/brand/`.
* Stronger `.gitignore` (signing material, APK/AAB, IDE files), new `.gitattributes`, optional
  `.github/workflows/android-ci.yml` (written, not executed).
* New `LegalScreenTest` (Compose UI on Robolectric): version line, the three documents open offline,
  Back returns to the list. Written but not executed in the authoring environment.

## Store pack
* `store-listing/`: 512x512 icon, 1024x500 feature graphic, listing text, content-rating and Data
  safety answers, screenshot shot list.
* `EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md`: end-to-end guide for building, testing, signing, publishing to Google Play and to GitHub.

---

# Emogy v2.1 – what changed

## Branding update
* The user-facing public name is **Emogy** everywhere: launcher label, Home wordmark, Welcome sheet,
  docs and store metadata.
* The application ID is now `com.vurka.emogy` and the Kotlin namespace matches it. This is a new
  install identity: a device with a `com.vurka.emogy` build does not receive this as an update and
  its local progress is not migrated. DataStore names and saved-data keys are unchanged, so progress
  still survives backup/restore within the new package.

### Copy lives in `strings.xml`
* Every piece of UI chrome and copy is now an English string resource in
  `app/src/main/res/values/strings.xml` (302 keys plus one plural): navigation labels, Home, World Map, Daily,
  Album, More, Statistics, Rewards, Store, Themes, Settings, How to Play, Welcome and every game
  dialog, HUD label and accessibility description.
* Catalog content stays in Kotlin data on purpose: emoji names, world names, theme names and
  descriptions, quest titles and store-item names are game data, not chrome.
* No locale folders were added yet; the default English resources are the only ones shipped.
* `TopLevelDestinations` tab labels are `@StringRes` ids, so the bottom bar cannot drift from the
  catalog.

### Privacy policy refreshed
* `PRIVACY_POLICY.md` now spells out every category of data stored locally (progress, album, daily
  results, settings, first-run flag, 24-hour resume snapshot, statistics), the Android backup
  behaviour, the single `VIBRATE` permission, the absence of ads/IAP/SDKs, the General / 13+
  audience, and exactly how players delete their data.
* The only unresolved item in the policy is the contact email; the page is otherwise ready to host.

### Tests
* Text assertions were updated for the new name (`ExampleRobolectricTest`, `WelcomeSheetTest`) and
  `TopLevelDestinationsTest` now checks that each tab has a real string resource.
* `:app:compileDebugKotlin` was kept green after every screen.

## Phase 4 – Navigation shell

### Five tabs instead of a long Home menu
* A persistent bottom bar now has **Home**, **Map**, **Daily**, **Album** and **More**.
* Home stays the play/continue hub. Rewards, Statistics, Themes, Coin Store, Settings and How to
  Play moved into the new **More** tab, which also shows a compact levels/stars/coins overview.
* The bar appears only on those five roots. Boards, the daily board and every secondary screen hide
  it and keep their own back arrow instead.
* Tab switches save and restore each tab's scroll position; re-tapping the current tab does nothing.

### One owner for layout and insets
* A single app-level `Scaffold` owns the bar, so screens no longer add their own navigation-bar
  padding on top of it.
* Album and More no longer apply the system-bar inset a second time inside their own scaffolds.
* The world map no longer reserves a second navigation-bar inset at the end of its scroll content.
* Home, Daily and the world map no longer show a back arrow on a root destination.

### Tests
* `TopLevelDestinationsTest` proves there are exactly five roots, that board and secondary routes
  hide the bar, and that routes and test tags are unique.
* `AppBottomBarTest` checks the five tabs, the selected state, that taps report routes, and that
  tapping the current tab does not navigate.
* `MainActivityNavigationTest` launches the real activity and walks Home → Daily → Album → Map →
  More → Settings, checking that the bar disappears and the back arrow appears; it repeats the tab
  layout at 320×480.
* `WelcomeSheetTest` covers the one-time onboarding content and all three exits.

---

## Phase 2 – Core-loop polish

### First run now explains itself
* A one-time **Welcome** sheet appears on a fresh install after DataStore has answered. It covers
  matching, star scoring and the Daily/Album loop, then gets out of the way.
* It can be skipped by swiping/back, the close button, or **Start Playing**; **Read the full rules**
  opens the existing How-to-Play sheet.
* The welcome flag is stored in DataStore and preserved by **Reset All Game Progress**, so a reset
  does not replay the introduction.
* Returning players are not shown the welcome just because the flag did not exist in an older build:
  the sheet only appears when there is no other evidence of play.

### First launch no longer says CONTINUE
* A fresh install stores level 1 as the Continue target, which previously made the main button say
  **CONTINUE** before the player had touched a card.
* The button now says **PLAY** until a card has been flipped or a level other than 1 has been chosen.
* The rule lives in pure `HomeProgressRules` and is unit-tested.

### Daily date is the board's date
* `DailyChallenge.dateLabel(epochDay)` formats the stored board day instead of reading the current
  clock, so an app left open across midnight still shows the date of the board being played.
* The helper has explicit timezone tests (UTC, GMT-11, GMT+14).

### Tests
* `HomeProgressTest` covers fresh install, loading state, first flip, another level and catalogue
  bounds.
* `DailyChallengeTest` adds stored-day formatting and extreme-timezone cases.

---

## Phase 3 – Emoji Album

### Collect what you match
* Home has a new **Album** entry. Every successful pair match adds that emoji to an offline
  collection, including the final pair of a level and every Daily Challenge run.
* The album is grouped by the catalogue's 11 categories with a per-category counter, an overall
  counter, a percentage and a progress bar.
* Uncollected tiles are dimmed and locked; tapping any tile opens its emoji name, category and how
  to unlock it. Nothing is purchasable and no duplicates or packs are involved.
* Collected ids are stored in DataStore as a `StringSet`. Unknown or retired ids are ignored when
  the album is calculated, so a catalogue change cannot inflate the count.
* **Reset All Game Progress** clears the album with the rest of the progress.

### Tests
* `AlbumProgressTest` covers empty/full collections, per-category maths, unknown ids, catalogue
  order, duplicate-safe counting and the empty-catalogue divide-by-zero guard.
* `GameViewModelTest` adds three cases: a match unlocks exactly its own emoji, the final pair and
  a daily run are collected, and a mismatch unlocks nothing.

---

## Phase 3 – Daily Challenge

### One board a day, entirely offline
* Home has a **Daily Challenge** card, and a dedicated lobby shows today's date, the streak,
  all-time daily bests and whether today is already done.
* The board is seeded from the local calendar day: 8 pairs, 120 seconds, 3 hints, no move limit.
  Everyone on the same date gets the same cards; no server, account or clock sync is involved.
* The first completion each day pays **25 coins**. Replays only improve the all-time daily best
  time and moves, so the reward cannot be farmed.
* Daily runs never touch level stars, level bests, the Continue button or the resumable level
  snapshot. Wins and losses still count in play stats and the daily streak.
* The challenge resets at local midnight, and moving the device clock backwards cannot claim a
  second reward for the same day.
* Zen mode still applies, so the daily board can be played without a timer.

### Boundaries
* Daily runs are one sitting: they are deliberately not written into the level-snapshot store, so
  a daily run cannot overwrite a suspended story level. Pausing still stops the clock.

### Tests
* `DailyChallengeTest` covers same-day/different-day seeds, the exact deck for a day, the lobby
  constants, first-completion coins, best-only-improves rules, the next-day reset, clock rollback
  and local-midnight boundaries.
* `GameViewModelTest` adds five virtual-time cases: deterministic board, day-to-day variation,
  one payout with no level record, replay without a second payout, and snapshot isolation.

---

## Phase 3 – Assist mode

### What it is
* Settings has a new **Assist Mode** toggle (off by default). When it is on, every level shows a
  heart button next to the hint.
* Tapping the heart reveals **one matching pair** for 2.6 seconds, with a gold outline. It never
  shows the whole board, so it is a nudge rather than a solve button.
* Assist works on **every** level, including boss levels that have zero hints. It costs no hints,
  moves or coins and does not change star scoring.
* A 20-second cooldown prevents repeated reveals. The heart badge counts down, and the cooldown
  pauses with the game.

### Behaviour details
* The pair is picked from face-down, unmatched cards only, so Assist never fights an active hint
  or a half-finished selection.
* Revealed cards can still be tapped normally. Resolving the pair immediately clears the glow;
  otherwise it fades after 2.6 seconds.
* Pausing, restarting or leaving the level cancels the reveal. Assist state is visual only and is
  not written into the resumable snapshot.
* The setting is persisted in DataStore and preserved by **Reset All Game Progress**, like Zen mode.

### Tests
* `AssistRulesTest` covers pair selection: same emoji, exactly two cards, never matched or already
  face-up cards, and the empty cases.
* `GameViewModelTest` adds six virtual-time tests: reveal without spending a hint or move, cooldown
  gating, boss levels with zero hints, mode-off, preview/pause behaviour, and playing the revealed
  pair normally.

---

## Phase 1 – correctness pass (towards v2.2)

### Interrupted runs survive process death
* An unfinished level is now saved to DataStore: board, matched pairs, moves, mistakes, timer,
  hints, combo, shuffle state and whether it was paused.
* On reopening the same level, the board is restored and a **Run restored** snackbar offers
  **Restart**. A run saved while paused comes back into the pause dialog.
* Snapshots expire after 24 hours, are cleared on win/loss/quit/reset, and are only accepted when
  the level, card count, pairing, limits and time all validate. Corrupt or old-version data is
  ignored and a fresh board is dealt.
* Saves are debounced after moves, hints and shuffles, and written immediately on pause/`onStop`.
* The clock gets a one-second grace period after an unpaused restore.

### Rules are testable and no longer race the settings
* `GameViewModel` is a plain `ViewModel` with injected clock, settings, feedback, progress and
  snapshot store. Production adapters keep the existing SoundPool/Haptic/DataStore engines.
* Zen mode is seeded from the first persisted value before the board is dealt, so a Zen run can no
  longer start with finite hints or a shuffle countdown.
* New virtual-time test suite covers preview/timer, time and move losses, idle-only shuffle,
  auto-hide, hint limits/cooldown, boss hints, Zen, pause/resume, victory once, replay coins and
  restore/expiry/malformed snapshots.

### Audio and layout hardening
* Unplugging headphones sends `ACTION_AUDIO_BECOMING_NOISY`; music fades out instead of blasting
  from the speaker and resumes on the next foreground/screen entry.
* Emoji the device font cannot draw are filtered once at startup. Deck building falls back to the
  raw catalogue only when the supported pool is too small, so no level deals a broken board.
* Card glyphs are capped against the card width, so 200% font scale no longer overflows six-column
  cards. The HUD wraps instead of clipping, and the top bar ellipsises long world names.
* Verified layouts at 360×640 dp with 200% font and at 640×360 dp landscape, plus Back → pause →
  Back → resume.

### Rewards, shop and migration tests
* First clear still pays 15 + 5 per star; replays pay nothing and can never downgrade stars, best
  moves or best time. A 0-star result is not treated as a clear.
* Buying an already-owned theme is idempotent: it succeeds without charging again.
* Legacy `sound_enabled` migration is covered (muted stays muted), and reset preserves master,
  music and effects volumes, haptics and theme mode. Purchased themes are wiped with the rest of the
  progress, so the selected theme returns to the free default rather than staying selected but
  unowned.

### Debug signing hygiene
* `debug.keystore` is no longer tracked. Existing checkouts keep updating in place with their local
  key; a fresh clone falls back to AGP's standard `~/.android/debug.keystore`.

---

## Phase 1 – audio (towards v2.2)

### Separate, persistent volume controls
* Settings now has **Master**, **Music** and **Effects** sliders, stored in DataStore. The old
  `sound_enabled` boolean is migrated (on → 100%, off → muted) so existing players keep their choice.
* `soundEnabled` remains a derived value (`master > 0`), so every existing call site still works.
* Muting stops both engines; the music slider never bleeds into the effect channel.

### Ambient music per world family
* Four original 64-second loops (Organic, Festive, Airy, Cosmic) cover worlds 1–5, 6–10, 11–15 and
  16–20. The map cross-fades as you scroll into a new region; levels select their family directly.
* Music is synthesised deterministically on the device, cached as WAV, looped with `MediaPlayer`,
  faded in/out, and paused on `ON_STOP`. Audio focus is requested with `USAGE_GAME`; transient loss
  pauses, short interruptions duck, and permanent loss stops playback.
* No audio assets are bundled, no encoder is needed and no permission was added.

### Richer, musical effects
* Five new cues: **star**, **timer tick**, **whoosh**, **boss drum** and **chest/unlock chime**.
* The match chime climbs a major pentatonic ladder with the combo (combo 5 is one octave up, capped
  so SoundPool playback rates stay legal). The timer ticks for the final ten seconds and sharpens in
  the last three.
* Effects use shared master × effects gain staging; the engine refuses to play while the app is in
  the background or while music focus is lost.
* Cache version bumped so devices regenerate the expanded cue set.

### Settings and docs
* Volume card with live percentages and effect previews for every new cue.
* `LICENSES.md` documents every cue and loop: all original, procedurally synthesised, no third-party
  samples.
* New tests: gain staging, mute threshold, combo ladder and cap, world-to-music mapping, ambient
  audibility/length/determinism.

---

## v2.1 – release pass

### Every level is open, progress stays honest
* All 1,000 levels are selectable from the first launch, in any order. Level *availability* is no
  longer tied to progress at all.
* Removed `highestUnlockedLevel`. Real progress is derived from recorded stars:
  `completedLevels`, `totalLevelsCompleted`, `highestCompletedLevel`.
* Cosmetic themes still require real completions or a coin purchase (`ThemeUnlock`); having every
  level open does not unlock them. Locked cards show "🔒 12/50 levels".
* Reset now says exactly what it clears, keeps settings, and confirms when done. Levels stay open.
* The map has a validated **Jump to level** dialog (1–1000), a 20-world selector, and remembers your
  scroll position when you come back from a game.

### Rewards that make sense
* First clear pays **15 + 5 per star** coins; replays pay nothing, so level 1 cannot be farmed.
  Stars and personal bests only ever improve.
* Victory screen offers Next, Replay, World Map and Home, shows personal bests and first-clear coins,
  and turns into "Summit reached" at level 1000.
* Losses now count toward games played and the daily streak, and tell you whether time or moves ran out.

### Sound and accessibility
* Replaced the DTMF keypad tones with 12 synthesised cues (tap, flip, match, mismatch, hint, shuffle,
  combo, coin, achievement, victory, defeat, pause). They are rendered deterministically to WAVs in
  the cache and played with `SoundPool` — no third-party audio assets, no downloads, no permissions.
* Combo milestones get a rising accent instead of stacking on the match chime; the victory fanfare
  replaces the final match chime.
* Settings has a **Preview Sounds** row.
* Face-down cards announce "Hidden card" — the emoji is never leaked to TalkBack. Card glyphs are
  marked decorative, and matched pairs announce as matched.
* Decorative motion (drifting backdrop, confetti, screen shake, popping cards, pulsing nodes) is
  skipped when the system's "Remove animations" setting is on.
* Shared `AppTopBar` and design tokens (`Spacing`, `Radii`, `Chrome`) replace repeated literals.

### Docs and build
* `RELEASE.md` documents signing, verification and the remaining release blockers.
* `metadata.json` / `.env.example` no longer claim a Gemini capability the app does not have.
* Release builds fail loudly when the upload key is missing; they never fall back to `debug.keystore`.

---

# v2.0 – what changed

## Harder as you climb (`LevelCatalog`, `GameViewModel`, `MemoryGameEngine`)
| Mechanic | Early (1-20) | Mid (100-300) | Late (600-1000) |
|---|---|---|---|
| Pairs | 4-7 | 9-14 | 17-20 |
| Memorise time | 5s | 3s | 2-3s |
| Time per pair | none | 8-6s | 4.6-3.8s |
| Move budget | none | ~2.7-2.3x pairs | ~2.1-2.0x pairs |
| Wrong pair stays visible | 1000ms | 850-700ms | 560-460ms |
| Hints | unlimited | 5 -> 3 | 2 -> 1 |
| Mid-game reshuffle of unmatched cards | - | every ~45-34s (from L100) | every ~28-18s |
| Lone flipped card hides again | - | 4.5s (from L200) | 3.7-2.5s |

* **Boss level every 25 levels** (40 total): +2 pairs, 10% less time, fewer moves, faster shuffles, **no hints**, gold crown on the map.
* Zen mode (Settings) still removes all pressure (timer, move limit, shuffles, auto-hide, hint limits).
* New: combo counter + combo popup, "Shuffled!" popup, separate loss messages (time vs moves).

## Smoother
* Card flip angle is read only in the draw phase (`graphicsLayer`) -> **zero recompositions while a card flips**.
* Card grid is its own composable, so the 1-second timer tick no longer recomposes every card.
* Cards glide to their new slot on reshuffle (`animateItem`).
* Shake effect moved from layout (`offset`) to draw phase (`translationX`).
* Card back brush is `remember`ed per theme instead of rebuilt per card.
* Dark window background removes the white flash at launch.
* Game auto-pauses when the app goes to the background; Back opens the pause menu instead of losing the run.
* Fixed: restarting during the memorise phase could start two preview timers.
* Fixed: the timer could start while the game was paused during the preview.
* Responsive grid: 3/4/5/6 columns by deck size, emoji size scales with columns (big decks fit without scrolling).

## Map-style levels (`LevelSelectScreen`)
* Winding S-curve path that climbs upward (like Candy Crush): 20 world banners, 1000 nodes, summit at the top.
* Pulsing "current" node with a hopping wizard marker, stars on completed nodes, gold trail on the part you've travelled, dashed trail ahead. (v2.1 removed the lock state: every node is playable.)
* Boss nodes (square, crown). Scenery emoji per world. Background colour cross-fades as you scroll between worlds.
* World chip that updates while scrolling, "📍 Level N" jump button, auto-scroll to your level.
* Tap a node -> level preview (pairs, time, moves, shuffles, hints, best stars) -> Play.

## Visuals / animation
* New physics confetti (two cannons, drag, gravity, 3D flutter, fade-out).
* Card backs have a woven texture; matched cards glow emerald; HUD with move-limit counter, time bar that turns amber/red, rule chips, "Memorize!" banner.

## Play Store readiness
* Removed unused/risky dependencies: Firebase (AI, App Check), Retrofit, OkHttp, Moshi, Room, KSP, Google Services and Secrets plugins. No INTERNET permission -> Data safety = "no data collected".
* Backup rules fixed: they pointed at `sharedpref`, but DataStore lives in `files/datastore/`, so progress was never actually backed up. Now it is (Android 11- and 12+ rules).
* Predictive back enabled, content descriptions on cards and map nodes (TalkBack).
* `versionCode 2 / versionName 2.0`, R8 + resource shrinking already on for release, trimmed ProGuard file.
* Added `PRIVACY_POLICY.md` and `PLAY_STORE_CHECKLIST.md`.
