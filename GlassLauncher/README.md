# Glass Launcher

An Android Home screen with an iOS 26 "Liquid Glass" look. It's written in Kotlin and Jetpack Compose and works on Android 12 or newer.

You don't need Android Studio. GitHub builds the app for you every time code is pushed.

## Get the app onto your phone (Samsung Galaxy)

### 1. Download the APK

**Easiest way (on your phone):**
1. Open this repository on GitHub in your phone's browser.
2. Tap **Releases** (or go to `…/releases`) and open **Latest Glass Launcher build**.
3. Tap **GlassLauncher.apk** to download it.

**Other way (Actions artifact):**
1. Open the **Actions** tab, then tap the newest green ✅ "Build Glass Launcher APK" run.
2. Scroll to **Artifacts** and tap **GlassLauncher-apk**. You need to be signed in to GitHub.
3. You get a `.zip` file. Open it in **My Files** and tap **Extract** to get `GlassLauncher.apk`.

### 2. Install it
1. Tap the downloaded `GlassLauncher.apk`, either from the browser's download notification or from **My Files → Downloads**.
2. Samsung will say *"For your security, your phone is not allowed to install unknown apps from this source."* Tap **Settings**, switch on **Allow from this source**, then go back.
3. Tap **Install**. If **Auto Blocker** stops it, go to **Settings → Security and privacy → Auto Blocker**, turn it off, install, then turn it back on.
4. If Play Protect warns you, tap **More details → Install anyway**. This app isn't from the Play Store, so the warning is normal.

### 3. Make it your Home screen
1. Open **Glass Launcher** and tap **"Tap to make Glass your Home screen"** on the clock. Choose **Glass Launcher** and confirm.
2. Or go to **Settings → Apps → Choose default apps → Home app → Glass Launcher**.
3. Press the Home button. You're in!

To switch back later, go to **Settings → Apps → Choose default apps → Home app → One UI Home**.

### 4. Turn on the real blur (optional but pretty)
Android only gives apps the wallpaper picture if they ask in one of these ways:
- **Glass Settings → Choose a wallpaper photo** (no permission needed). The launcher sets the photo as your wallpaper and blurs it behind the glass.
- **Glass Settings → Use my current wallpaper**. Switch on *All files access* for Glass Launcher. The launcher only uses this to read the wallpaper.

Without either, the glass is still see-through and frosted, just not blurred.

## How to use it
| Do this | What happens |
|---|---|
| Tap an app | Opens it |
| Swipe up | Glass app drawer, A–Z |
| Tap **Search** | Drawer with the keyboard open |
| Swipe down | Notification panel |
| Hold an app | Glass menu: Add to home / Add to dock / App info / Uninstall |
| Hold an app, then drag | Move it. Drop it on another app to make a folder, or on a folder to add it. Hold it at the screen edge to change page |
| Drag an app from the drawer | Put it on the Home screen |
| Hold an empty spot or the clock | Glass settings / Choose wallpaper |
| Tap a folder name | Rename it |

## Updating
Push a change and GitHub builds a new APK. Download it and install it over the old one. Your layout is kept, because every build is signed with the same key (`app/debug.keystore`).

## Project map
- `app/src/main/AndroidManifest.xml`: the HOME launcher setup, the `<queries>` block and permissions
- `data/`: loads your apps, saves the layout and settings, and the wallpaper copy for blur
- `ui/Glass.kt`: the liquid glass look (RenderEffect blur, frost, top shine, bright border, shadow)
- `ui/HomeScreen.kt`: pages, grid, clock widget, dots, search pill, dock
- `ui/AppDrawer.kt`, `ui/FolderOverlay.kt`, `ui/SettingsScreen.kt`, `ui/LauncherRoot.kt` (menus and drag)
- `../.github/workflows/build-glass-launcher.yml`: the cloud build
