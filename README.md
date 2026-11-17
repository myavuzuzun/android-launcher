# minimal launcher

a lightweight, text only android launcher 

## features

- **calm ui:** all launcher labels, app names, menus, and settings use lowercase modern sans-serif typography.
- **home screen:**
  - wallpaper passthrough rendered by the Android system process (zero wallpaper memory overhead).
  - limitless pinned apps, bottom-aligned, with configurable `left` / `center` / `right` alignment.
  - optional `clock & date` header (tap time for clock, tap date for calendar, or hide completely in `preferences`).
  - **swipe up** on Home to open `all apps`.
  - **swipe down** on Home to expand the notification shade.
  - **long-press** empty Home space to open `preferences`.
- **All apps drawer:**
  - Solid themes
  - Alphabetical list with work profile support (`work` tag) and pinned indicator dot.
  - Touch-and-drag `a`–`z` jump sidebar on the right edge.
  - Bottom `search` bar with live filtering, `Go`/Enter to launch the top match, and a `preferences` gear button right beside it.
  - **Swipe down** at the top of the drawer (or press Home / Back) to return to Home.
- **Long-press app actions:** `pin to home` / `unpin from home`, `rename`, `reset custom name`, `hide app`, `app info`, and `uninstall`.
- **Text size options:** Switch between `small`, `medium`, and `large` in `preferences`.

## Stack & Compatibility

- **Language & UI:** Kotlin + native `android.view` / `android.widget` Views (`android.useAndroidX=false`).
- **SDK range:** `minSdk = 24` (Android 7.0) to `targetSdk = 35` (Android 15).
- **Navigation support:** Edge-to-edge `WindowInsets` handling works with both 3-button navigation bars and gesture navigation.

## Building

1. Open this folder in **Android Studio** (Ladybug or newer recommended).
2. Let Android Studio sync Gradle (it uses `gradle/wrapper/gradle-wrapper.properties` for Gradle 8.10.2 and downloads `compileSdk = 35` if missing).
3. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)** or run directly on your device.

