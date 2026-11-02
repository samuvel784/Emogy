# Google Play listing pack for Emogy

Copy and paste these into Play Console > Grow > Store presence > Main store listing.
Limits shown are Google's current limits; Play Console will tell you if any changed.

## Basics

| Field | Value |
|---|---|
| App name (max 30) | `Emogy: The Memory Game` (22 characters) |
| Default language | English (United States) |
| App or game | Game |
| Category | Puzzle (alternative: Casual) |
| Tags | Puzzle, Brain games, Memory, Casual, Offline |
| Contact email | the same address you put in the legal pages |
| Privacy policy URL | your GitHub Pages link to `privacy-policy` (see EMOGY_DEVELOPMENT_AND_RELEASE_GUIDE.md) |

## Short description (max 80)

```
Relaxing emoji memory game: 1,000 levels, daily puzzles. Works completely offline.
```

(78 characters.)

## Full description (max 4,000)

```
Emogy is a calm, colourful emoji memory game for everyone who likes a little brain exercise.

Flip two cards at a time, remember where each emoji hides and match every pair. Start with easy boards and work up to boss levels that test your focus.

WHAT YOU GET
• 1,000 levels across 20 themed worlds, from friendly animal boards to cosmic challenges
• Every level is open from the start: play in order, or jump to any level you like
• A winding world map with boss levels, stars and personal bests
• A Daily Challenge: one fresh board every day, the same for everyone
• An Emoji Album that fills in as you match pairs
• Daily, weekly and monthly missions with coin rewards
• A Coin Store for card backs, card themes, board backgrounds and effects (coins are earned by playing, never bought)
• Zen mode with no timer or move limits for fully relaxed play
• Assist mode that can reveal a matching pair when you are stuck
• Original sound effects and ambient music for each group of worlds, with separate volume sliders
• Light and dark themes

PRIVATE AND PEACEFUL
• No account or sign-in required
• No internet needed: Emogy works completely offline
• Real-money purchases do not exist: coins are only an in-game reward

BUILT TO BE COMFORTABLE
• Large tap targets and clear contrast
• TalkBack support for menus and cards
• Respects Android's "Remove animations" setting
• Haptic feedback you can switch off

Whether you have two minutes on the bus or an evening to unwind, Emogy gives you a gentle challenge that gets a little harder as you grow. How far can your memory take you?
```

## What's new (max 500) - first release

```
Welcome to Emogy! 1,000 levels in 20 worlds, a Daily Challenge, an Emoji Album, missions, a coin store and original music. Completely offline.
```

## Graphics

| Asset | File | Requirement |
|---|---|---|
| App icon | `store-listing/play-icon-512.png` | 512 x 512 PNG, up to 1 MB |
| Feature graphic | `store-listing/feature-graphic-1024x500.png` | 1024 x 500 PNG/JPG |
| Phone screenshots | **you must capture these** | 2 to 8, PNG/JPG, 16:9 or 9:16, each side 320-3840 px |
| Tablet screenshots | optional | 7-inch and 10-inch, recommended if you want tablet visibility |

### Screenshot shot list (take them on a real phone or the emulator)

1. Home screen with the PLAY button.
2. World map showing the winding path and a boss node.
3. A level in progress with several matched cards.
4. Victory screen with three stars.
5. Daily Challenge screen.
6. Emoji Album with some emoji collected.
7. Coin Store or Cosmetics.
8. Settings with the volume sliders.

Tips: use the same device size for all shots, turn on dark mode for half of them if you like, and make
sure no personal notification or clock from a real account is visible. Use Android Studio's emulator
"Take screenshot" button or `adb exec-out screencap -p > shot1.png`.
Do not add fake device frames that hide the real UI, and do not use screenshots of features the app
does not have.

## Content rating questionnaire (IARC) - suggested answers

Answer truthfully in Play Console; these are the answers that match the app as built.

| Question | Answer |
|---|---|
| Category | Game (puzzle) |
| Violence, blood, gore | No |
| Sexual content or nudity | No |
| Profanity or crude humour | No |
| Controlled substances (drugs, alcohol, tobacco) | No |
| Gambling or simulated gambling | No (no betting, no random paid rewards, no real-money stakes) |
| User-to-user communication or user-generated content | No |
| Shares user location | No |
| Digital purchases of any kind | No |
| Expected result | Everyone / PEGI 3 / similar lowest rating |

## Data safety form - suggested answers

| Section | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (no data collected) |
| Do you provide a way for users to request that their data is deleted? | Not applicable (no account, no server) |
| Privacy policy | Your public URL |

Why this is accurate: the app has no internet permission, no analytics or ads SDKs and stores
progress only in app-private storage on the device. Android's own device backup is performed by the
operating system and is not counted as data collected by the app. If you ever add networking, ads,
analytics, crash reporting, accounts or notifications, redo this form and update the privacy policy
first.

## App content declarations

| Declaration | Answer |
|---|---|
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access |
| Target audience | 13 and over (do not select "under 13"; that would enrol the app in the Families policy) |
| News app | No |
| Government app | No |
| Financial features | None |
| Health features | None |
| COVID-19 contact tracing | No |
| Advertising ID | Not used (the app does not declare the permission) |
| Data deletion | Not applicable |

## Pricing and distribution

* Free app. Note: Play does not allow a free app to be switched to paid later; that is fine for Emogy.
* Countries: all available countries, or pick your own.
* Contains ads: No.
* Check the export-law and US-law declarations in the Console.
