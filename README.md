# minimal launcher

a lightweight, text only android launcher 

## features

- **calm ui:** all launcher labels, app names, menus, and settings use lowercase modern sans-serif typography
- **home screen:**
  - wallpaper passthrough rendered by the Android system process (zero wallpaper memory overhead)
  - limitless pinned apps, bottom-aligned, with configurable `left` / `center` / `right` alignment
  - optional `clock & date` header (tap time for clock, tap date for calendar, or hide completely in `preferences`)
  - **swipe up** on Home to open `all apps`
  - **swipe down** on Home to expand the notification shade
  - **long-press** empty Home space to open `preferences`
- **all apps drawer:**
  - solid themes
  - alphabetical list
  - `a`–`z`  sidebar on the right edge
  - bottom `search` bar, and a `preferences` gear button right beside it
  - **swipe down** at the top of the drawer (or press home / back) to return to home
- **long-press app actions:** `pin to home` / `unpin from home`, `rename`, `reset custom name`, `hide app`, `app info`, and `uninstall`
- **text size options:** switch between `small`, `medium`, and `large` in `preferences`

## compatibility

- **sdk range:** `minSdk = 24` (android 7.0) to `targetSdk = 35` (android 15)

## building

1. open this folder in **android studio** (Ladybug or newer recommended)
2. let android studio sync gradle (it uses `gradle/wrapper/gradle-wrapper.properties` for gradle 8.10.2 and downloads `compileSdk = 35` if missing)
3. select **build → build bundle(s) / apk(s) → build apk(s)** or run directly on your device

