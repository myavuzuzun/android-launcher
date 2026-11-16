# Minimal Launcher

An ultra-lightweight, text-only Android home screen replacement built from scratch with **pure Kotlin** and **native Android Views** — zero third-party dependencies, zero AndroidX libraries, and zero background services.

## Features

- **Pure typography, lowercase UI:** All launcher labels, app names, menus, and settings use lowercase modern sans-serif typography.
- **Home screen:**
  - Wallpaper passthrough rendered by the Android system process (zero wallpaper memory overhead).
  - Limitless pinned apps, bottom-aligned, with configurable `left` / `center` / `right` alignment.
  - Optional `clock & date` header (tap time for Clock, tap date for Calendar, or hide completely in `preferences`).
  - **Swipe up** anywhere on Home to open `all apps`.
  - **Swipe down** on Home to expand the notification shade.
  - **Long-press** empty Home space to open `preferences`.
- **All apps drawer:**
  - Solid background driven by your chosen theme (`amoled black`, `matte dark`, `nordic slate`, `warm paper`, `pure light`).
  - Alphabetical list with work profile support (`work` tag) and pinned indicator dot.
  - Touch-and-drag `a`–`z` jump sidebar on the right edge.
  - Bottom `search` bar with live filtering, `Go`/Enter to launch the top match, and a `preferences` gear button right beside it.
  - **Swipe down** at the top of the drawer (or press Home / Back) to return to Home.
- **Long-press app actions:** `pin to home` / `unpin from home`, `rename`, `reset custom name`, `hide app`, `app info`, and `uninstall`.
- **Text size options:** Switch between `small`, `medium`, and `large` in `preferences`.

## Stack & Compatibility

- **Language & UI:** Pure Kotlin + native `android.view` / `android.widget` Views (`android.useAndroidX=false`).
- **SDK range:** `minSdk = 24` (Android 7.0) to `targetSdk = 35` (Android 15).
- **Navigation support:** Edge-to-edge `WindowInsets` handling works with both 3-button navigation bars (e.g., Samsung Galaxy A12 One UI Core) and gesture navigation.

## Building an APK

### Option 1: GitHub Actions (No local Android Studio required)
This project includes [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml). Push this folder to a GitHub repository and open the **Actions** tab to download the built Debug and Release (unsigned) APKs directly from workflow artifacts.

### Option 2: Android Studio (Local)
1. Open this folder in **Android Studio** (Ladybug or newer recommended).
2. Let Android Studio sync Gradle (it uses `gradle/wrapper/gradle-wrapper.properties` for Gradle 8.10.2 and downloads `compileSdk = 35` if missing).
3. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)** or run directly on your Galaxy A12 via USB debugging.

## Setting as Default Home App (Samsung Galaxy A12)

1. Install the built APK onto your phone.
2. Open **Settings → Apps → Choose default apps → Home app**.
3. Select **minimal launcher**.
