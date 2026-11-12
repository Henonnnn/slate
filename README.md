# Slate

A quiet, typographic Android home screen: your favourite apps as a list on the wallpaper, an alphabet
down the edge for everything else, pop-ups for shortcuts and notifications, and The Pill at the bottom
for what's happening now (music, timers, the flashlight, the next alarm).

Kotlin and Jetpack Compose (Material 3), one module, Android 11 and later (minSdk 30, targetSdk 36).

- No accounts, ads, analytics or crash reporting.
- The only network use is the optional weather fetch from Open-Meteo.
- Notification content is read on the device to show dots and previews; nothing is stored or sent.

## Install

Download the APK from the [Releases](../../releases) page and open it on your phone (you may have to
allow your browser or file manager to install apps). Then choose Slate as your home app when Android
asks, or in *Settings → Apps → Default apps → Home app*.

Updates install over the previous version: every release is signed with the same key.

Optional access, each explained in the app when it matters:

- **Notification access** for dots, previews, quick replies, media controls and timers in The Pill.
- **Calendar** for the next event under the clock.
- **Contacts** to find people in search.
- **Usage access** for screen time and app limits.

## Build it yourself

You need the Android SDK (Platform 36, Build-Tools 36) and a JDK between 17 and 21.

```
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # wherever your SDK is
./gradlew assembleDebug lint testDebugUnitTest
python3 tools/lint_issues.py     # lint warnings; Gradle only fails on errors
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with your machine's debug key, so a build of your own won't install over a
downloaded release (or the other way round) without uninstalling first, which clears Slate's settings.
Save a backup in *Settings → Backup* before switching.

## Layout of the code

`CLAUDE.md` describes every package and the conventions the code follows. `third_party/fonts/` holds
the licences of the two bundled fonts (Google Sans Flex and Kumbh Sans, both SIL OFL 1.1).
