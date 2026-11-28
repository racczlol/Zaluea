# A17 Speed Doctor

A small Android app for the Samsung Galaxy A17 (works on any Android 8+ phone) that finds out
**what is making the phone slow** and tells you **how to fix it**.

## Install on the phone
1. On the phone, open this repo on GitHub and download `android-app/download/A17-Speed-Doctor.apk`.
2. Tap the downloaded file. If asked, allow your browser / My Files to "Install unknown apps".
3. Open **A17 Speed Doctor**. Tap **"Allow access"** on the *Turn on app analysis* card and switch the app on
   in *Usage data access* — this lets it see which apps run in the background and how much cache they hold.

## What it checks
- **What's causing the most lag** – ranked list (biggest cause first) with a "What to do" and a button that opens
  the right settings screen: low free RAM, nearly-full storage, overheating / thermal throttling, Power saving,
  days since last restart, 1x animations, live wallpaper, apps running in the background, huge app caches,
  unused apps, and known heavy apps (Facebook, TikTok, Snapchat, big games…).
- **Heaviest apps** – each app gets a lag score with the reasons, plus a shortcut to force stop / clear cache / restrict it.
- **Free up RAM** button, **speed test** (shows if the CPU is running below its best = throttling),
  and Galaxy-A-specific tips.

## Build it yourself
```
cd android-app
./gradlew assembleRelease   # needs the Android SDK (platform 35); output in app/build/outputs/apk/release/
```
