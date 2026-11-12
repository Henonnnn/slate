# Slate — Android launcher

Single-module Kotlin + Jetpack Compose (Material 3) home-screen app. minSdk 30, targetSdk/compileSdk 36.
Personal sideload build: no analytics, crash reporting, Firebase or ads; the only network calls allowed are the
optional Open-Meteo weather fetch and its city search (`widgets/WeatherRepository`, plain HttpURLConnection).
On GrapheneOS INTERNET is a user toggle that may be off — check `hasNetworkPermission()` and explain.

Setup on a new machine and how releases are signed: `README.md`.

## Build

```
./gradlew assembleDebug lint      # must pass before every commit, with 0 warnings too
./gradlew testDebugUnitTest       # pure-logic unit tests
./gradlew installDebug            # sideload to a connected device
```

Gradle must run on JDK 17–21 (set `org.gradle.java.home` in `~/.gradle/gradle.properties` if the system JDK is newer).
Android SDK path goes in `local.properties` (`sdk.dir=...`), which is git-ignored.
Lint fails the build only on errors, so read warnings from `app/build/reports/lint-results-debug.xml` with an XML
parser (`<issue` and its attributes sit on separate lines, so `grep -c "<issue "` always says 0): `python3 tools/lint_issues.py`.

## Package layout (`app/src/main/java/dev/slate/launcher/`)

- `PinShortcutActivity` — confirms apps' "add to home screen" shortcut requests (only sent to the home app).
- `SlateApp`, `AppContainer` — Application + manual DI graph (no Hilt: zero codegen, nothing on the cold-start path).
  Reach it with `context.container`.
- `apps/` — `AppRepository` (LauncherApps, all profiles incl. paused work profile, change callbacks → Flow; private space
  via `ACCESS_HIDDEN_PROFILES` and the home role: only the parent user's launcher sees it, it gets its own list section,
  and while locked its apps are not loaded at all, so they can't appear in search;
  its lock, unlock and settings rows are full list rows (`PrivateSpaceRow`), clear of the alphabet strip),
  `AppEntry`, `ShortcutRepository` (LauncherApps shortcuts; only answers while Slate holds the home role, so
  callers check `hasAccess()` and the UI explains; `syncPins()` keeps the platform's pinned set equal to
  `LauncherSettings.wantedPins()` so pop-up shortcuts survive the app dropping them), and pure logic: `AlphabetIndex`, `HomeListBuilder`.
- `data/` — `SettingsRepository`: DataStore with a kotlinx.serialization JSON serializer over `LauncherSettings`
  (chosen over Proto DataStore: no protoc in the build, and the same JSON feeds backup/export). Favourites, hidden
  apps, label overrides and pop-ups live there, keyed by `AppEntry.key`; keys of missing apps are kept on purpose.
  `Popups` holds the pure pop-up edits (add/remove/reorder/resolve). No Room: the data is small, and one JSON
  file keeps backup/restore trivial. `Backup` (pure, tested) wraps settings in a versioned envelope, validates and clamps
  on read, and leaves app widgets out (ids belong to this install); `BackupRepository` does the file IO via the system
  picker. `BehaviourSettings` holds gestures and list options; add new behaviour toggles there.
- `icons/` — `IconCache`: memory LRU + PNG disk tier in `cacheDir/icons/look-<tag hash>/` (`<pkg>@<hash>.png`, hash
  covers key/version stamp/size/density/override). `AppEntry.versionStamp` invalidates on update. `IconRenderer` draws
  override → pack icon → pack back/mask/upon treatment → themed monochrome → own icon, then reshapes adaptive icons and
  badges work apps. `IconAppearance` (set on the cache and provided as `LocalIconAppearance` from MainActivity) keys it
  all; `AppIcon` reloads when its tag changes. `IconPackRepository` + pure `AppFilter` parse ADW/Nova packs.
- `search/` — pure, unit-tested logic: `Calculator` (recursive descent, degrees for trig), `UnitConverter`
  (offline units only — no currency, it would need the network), `FuzzyMatcher`, `SearchRanker`; plus sources:
  `SettingsCatalog` (system panels/pages — apps can't toggle Wi-Fi/Bluetooth themselves), `Torch` (the one real
  toggle), `ContactsSource` (optional READ_CONTACTS, asked inline).
- `ui/theme` — `SlateTheme(theme, wallpaperColor)` builds everything from `ThemeSettings`: `LocalSlatePalette`
  (accent incl. a custom hue, scrim from dimming, `panel` in `PanelTone` dark/light/system/black, text on the wallpaper light/dark/auto
  from the wallpaper's dark-text hint; screens with their own dark ground read `palette.onDark()`), `LocalSlateText` (font + scale; read as `SlateText.appLabel` via a composable getter),
  `LocalAppearance` (non-colour options like showIcons). `Accent` is pure colour maths (tested);
  `wallpaperPrimaryColor()` reads WallpaperColors (no permission, no pixels).
- `ui/appearance` — `AppearanceScreen` (live preview over the wallpaper; home fades out underneath),
  `IconPickerSheet` (Icon Assistant). Theme data: `data/ThemeSettings.kt` (+ `Presets`, `IconOverride`).
- `ui/home` — `HomeRoute`/`HomeScreen` (normal + favourites edit mode), `HomeViewModel`, `HomeHeader`, `AppRow`,
  `AppActionsSheet` (shortcuts, info, uninstall), `RenameDialog`,
  `RowSwipe` (horizontal row gestures, mirrored as accessibility actions), `PullToSearch` (nested-scroll pull at
  list top), pop-ups: `PopupPanel` in `PopupCard.kt` is a card floating over the home screen (no dim, as in Niagara; only Message Hub veils it), centred on the swipe point,
  40 dp from the edges, in `palette.panel` (the accent's hue, nearly black; content inside reads `palette.onPanel()`). Its header opens the app.
  It waits for the shortcuts so it opens at full size, grows from that point in a circle (`RevealShape` on a `graphicsLayer`), falls
  back into it on close (`popupExit`), and any leftover scroll becomes an elastic pull that shrinks and dismisses it. Every app has one: its
  notifications (top one expanded, one open at a time; swipe sideways to clear; the card closes when clearing leaves nothing), shortcuts, then added items. Long-press starts edit mode (drag-reorder); there's no visible edit row, as in Niagara. `PopupPicker` (toggle apps/shortcuts in, "Add to pop-up" dialog).
  Pop-up folders (`data/Popups.kt`: `PopupFolder`, `Folders`) are `AppEntry.folder` entries, so rows, favourites, rename and swipes work unchanged; their items sit in the
  pop-up map under `folder:<id>`, and they gather the notifications of the apps inside. Message Hub is `PopupPanel(hub = true)`: wider, every app's notifications, nothing to edit.
  `RowSwipe.Action.immediate` fires mid-swipe, finger still down, so pop-ups don't wait for release.
  `HomeHeader` draws `ClockFace` (every style, also used small in `ClockScreen`'s gallery) from `ClockSettings` (apart from
  `ThemeSettings`, so presets leave it alone). Styles use Niagara's sizes and draw digits as native paths so Overlap, Flip and Minimal can cut shapes out; the default font is
  the bundled Google Sans Flex (see `third_party/fonts`), then the at-a-glance block in `AtAGlance.kt`: date with weather and battery, next event
  and alarm under it, each hidden when off or empty. It takes the home offset as a lambda read during layout. `BehaviourSettings.homeOffset` is a share of the screen height above the clock, set by dragging in
  `MoveHomeOverlay`; dragging relayouts without recomposing. Home and all apps are separate screens, as in Niagara: `topListState` holds the clock, widgets and favourites, `listState` all apps, and `allApps`
  picks which shows. Only the alphabet opens all apps; it closes after 5 s untouched. Both lists stay composed so the swap is instant, and the hidden one is inert (`hiddenAtRest`).
  `RubberBand` replaces the platform overscroll with Niagara's curve and springs; pulling home up past 72 dp opens search. List edges fade via a `DstIn` mask on the rows, never an overlay: nothing may
  darken the wallpaper except the optional flat `palette.scrim`. The same app can appear in
  favourites and all apps, so LazyColumn keys are prefixed (`fav:`, `all:`, `hid:`, `pri:`). Windows 600 dp or wider use
  two panes (home left in `topListState`, all apps right in `listState`); edit mode stays one pane.
- `widgets/` — `WidgetRepository` (one `SlateWidgetHost`, provider list, bind helpers, orphan cleanup),
  `SlateWidgetHostView` (steals long-press from widget content), `WidgetBinder` (allocate → bind/permission dialog →
  configure → place; configure results arrive via `MainActivity.onActivityResult`), `CalendarSource` +
  pure `CalendarFormat`, `WeatherRepository` + `Weather.kt` models/WMO codes.
- `ui/widgets` — `WidgetStackView` (single or HorizontalPager stack), built-in `CalendarWidget`/`WeatherWidget`
  (typographic, on the wallpaper), sheets: home menu (long-press clock), picker, widget menu, place dialog.
  Widget stacks live in `LauncherSettings.widgets`; pure edits in `data/Widgets.kt`.
- `notifications/` — `NotificationService` (listener; re-snapshots active notifications on a background thread),
  `NotificationStore` (in-memory StateFlow per "pkg/userSerial" = `AppEntry.notificationKey`; pure `index()` applies
  the system's dot rules; `dismiss` and `reply`), pure `NotificationRules` (Niagara's ranks, order, wording, group clearing; tested), `NotificationDetail`
  (full content, read only when expanded), `NotificationAccess` (deep link to the listener switch; `reconnect`, since Android won't rebind after a
  crash or force stop). Filter on the channel's `canShowBadge`, never `Ranking.canShowBadge()`: that is false for everything when dots are off system-wide.
  Pop-up tiles and the pinned `ReplyBox` are in `ui/home/PopupNotifications.kt`. Explainer: `ui/onboarding/NotificationCard`.
- `floating/` — sources for The Pill (the bar at the bottom, once "floating button"): `MediaSessions` (top media session with
  position, duration and seek; needs notification access, else null), `LiveActivity` (ongoing notifications that count time, ring, or
  are promoted live updates, from `NotificationStore.live`), `nextAlarm()` (AlarmManager, no permission), pure `FloatingChooser`
  (media > flashlight > live activities, newest first > alarm within 12 h, minus dismissed keys; at most two). No calendar: events are under the clock.
  The flashlight (`FloatingItem.Torch`, from `Torch.state`, whoever lit it) shows while lit; its icon is its switch, and once out it lingers
  `TORCH_LINGER_MS` (5 s) so it can be lit again.
  UI in `ui/floating`: `FloatingButton` shows the first item as a pill and the second as a bubble beside it; tap or swipe up morphs it
  into a card with both (cover, seek bar, previous/play/next, the activity's own notification buttons; cover and titles open the app);
  outside tap, back or swipe down collapses; swiping the small pill down dismisses its main item. One spring (0.78, 380) drives every
  shape change. `LiveCount` shows a timer's count by lifting the `Chronometer` out of the notification (standard chronometer or the
  AOSP clock's custom layout), since Android has no API for other apps' timers. It's drawn last in `HomeScreen`, above the alphabet.
  The event line under the clock opens `AgendaPopup` (`ui/home/AgendaPanel.kt`): the week ahead by day, in a `Popup` window.
  Clock apps mark nothing (Fossify's timer is a plain notification whose title is the count), so the listener also treats every
  ongoing notification from an app that answers `AlarmClock` intents as live. The seek line is Material 3 Expressive's wave
  (`FloatingSources.wavyProgress`); `FloatingSources.two` allows the second item. The Pill's drag is plain state while the finger
  moves and one cancellable settle after: queued `Animatable.snapTo` calls once landed after the release and threw it off screen.
  It collapses on `ON_STOP`, so it never waits open behind the lock screen. `AudioRoutes` (headset via the audio device list, Bluetooth
  adapter state; no runtime permission) drives the optional music offer (`FloatingItem.Music`, `FloatingSources.audioPrompt`): while
  the trigger holds and nothing plays, the pill offers the chosen or last-played app, with a play button that presses the media key.
  `PendingIntent.sendFromForeground()` (root package) handles the Android 14+ activity-start opt-in.
- `wellbeing/` — `UsageRepository` (UsageStatsManager events; needs the Usage access app-op, explained and deep-linked;
  leaves home apps out), pure `UsageMath` (foreground time per package, unlocks) and `Breaker` (rules → decision),
  `BreakerReminder` (inexact alarm → "time's up" notification if the app is still in front). The Breaker guards every
  launch path via `HomeViewModel.launchGuarded`; grants live in memory. Opening an app from outside Slate can't be
  intercepted without an overlay/accessibility service — not done, on purpose. UI in `ui/wellbeing`.
- `ui/settings` — `SettingsScreen` (a hub of topics showing their current state, each opening a page, The Pill's among them) + `SettingsViewModel`.
  Full screens handle back with `rememberBackProgress` + `Modifier.backProgress` (predictive back: they shrink under the gesture). The alphabet is
  not composed under them: its `systemGestureExclusion` strip would swallow the system's edge swipe. Every row has
  an icon and, where it helps, a one-line summary. Solid background (`PanelBackdrop(solid = true)`) with flat rows, labels aligned to the row titles, and a centred title that collapses into the toolbar (`SettingsPage`); Appearance and Clock keep the wallpaper for their previews, so their rows sit on cards.
  Shares `PanelBackdrop`/`Group`/`section`/`LinkRow`/`StatusRow`/`ChoiceRow`/`Chip`/`ToggleRow`/`ScreenTitle` with `ui/appearance`, which also has `ClockScreen`.
  The look (2026-11-12 redesign) lives in those helpers: a `Group` is a rounded card of tiles (`Modifier.piece()`, 2 dp gaps) on `palette.ground`
  (the accent's hue, dark, in three steps), icons sit on tinted discs (the hub gives each topic its own hue), values show in pills, a `ChoiceRow`
  unfolds its options as chips. Anything new placed in a group must paint itself with `piece()`. Pages: Home screen, Pop-ups, The Pill, Gestures, Permissions, Backup, About.
  These screens are see-through, so one opened over another hides the one beneath (see the `visible` conditions in `HomeScreen`).
- `ui/search` — `SearchSurface` (field at the bottom, results grow upward via reverseLayout) + `SearchViewModel`.
- `ui/components` — reusable widgets; hand-drawn `Glyphs` and `SlateIcons` (24-unit line icons from path data) instead of the icon library;
  and `AlphabetScrubber`: one Canvas, all motion read while drawing so a moving finger never recomposes; only its narrow strip takes
  touches, at both screen edges. It shows `AlphabetIndex.sections()`: letters that have apps, a star for favourites, a shield for the private space.
  Placement and motion are ported from Niagara in `AlphabetWave` (tested): at least half the free height, centred under the list; the peak follows the finger inward and the block follows it up and down.
- `ui/onboarding` — permission/role explainers that deep-link to system settings and degrade gracefully.

## Performance

- Judge smoothness on a **release** build: debug Compose is several times slower. Release is signed with the debug
  key, so `adb install -r app/build/outputs/apk/release/app-release.apk` swaps builds without losing data.
- `FrameStats` (opt-in, any build): `adb shell setprop log.tag.SlateFrames DEBUG`, restart Slate, `adb logcat -s SlateFrames`
  → p50/p90/p99 and missed-deadline share per 240 frames.
- `src/main/baseline-prof.txt` + `profileinstaller` AOT-compile startup/scroll paths; Milestone 10 swaps in a generated profile.
- Rows are the hot path: no per-frame callbacks per row (use `onPlaced`, not `onGloballyPositioned`), no always-composed
  hidden children.

## Dependencies beyond AndroidX/Kotlin

- `sh.calvin.reorderable` (Apache-2.0, pure Compose) — drag-reorder for LazyColumn/LazyGrid (favourites, pop-ups).

## Conventions

- Coroutines + Flow end to end; no blocking I/O or binder calls on the main thread (StrictMode logs in debug).
- One ViewModel per screen, built through `viewModelFactory { initializer { ... } }` from the container.
- Content drawn on the wallpaper uses `LocalSlatePalette`, not `MaterialTheme.colorScheme`.
- Settings fields are added with defaults so older JSON keeps decoding. Changing what a stored value means bumps
  `LauncherSettings.CURRENT_SCHEMA` and handles it in `migrated()`, which covers both stored settings and backups.
- Per-frame effects (swipe offsets, pull distance) read state inside `graphicsLayer {}` / lambdas, never in composition.
- Unit tests run with `isReturnDefaultValues = true`, so Android types can be constructed but do nothing.
- Check StrictMode on device after UI changes: `adb logcat | grep StrictMode` must show no `dev.slate` frames.
- Only public SDK APIs. If the platform can't do something, say so and use the closest real alternative — no silent stubs.
- Screens opened over the wallpaper keep the fixed `ScreenTitle` above their list. Drawn state (dots, chevrons) gets a
  `stateDescription`, section labels get `heading()`, decorative text gets `clearAndSetSemantics {}`.
- UI tone: calm and typographic, one accent at a time, generous spacing, spring motion.
- One commit per milestone; `assembleDebug lint` and unit tests green first.
