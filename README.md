# App Hub

A tiny self-hosted "app store" for the Android apps I make. It checks a catalog
([`manifest.json`](manifest.json)) of monitored apps and, for each one, tells you
whether it's **not installed**, **has an update**, or is **up to date** — then
downloads and installs/updates it with one tap. It monitors and updates itself too.

## For friends: how to install

1. Download the latest **`app-hub.apk`** from
   [Releases](https://github.com/simon-liesinger/app-hub/releases/latest) and open it.
2. Android will ask to allow installing unknown apps — allow it.
3. Open **App Hub**. It lists every app and offers Install / Update buttons.

From then on, App Hub keeps itself and everything in the catalog current — just open
it and tap Update.

## How it works

- The app fetches `manifest.json` over HTTPS and reads each entry's `versionCode`.
- It compares that against the version installed on your phone (`PackageManager`).
- Tapping Install/Update downloads the APK from this repo's GitHub Releases,
  verifies it's signed with the **same certificate** as the installed copy
  (trust-on-first-use — blocks tampered downloads), then launches the installer.
- APK downloads are restricted to `github.com` / `githubusercontent.com` hosts.

## Adding another app to the catalog

When you want App Hub to monitor one of your other apps:

1. Publish that app's APK as a GitHub release asset (any repo of yours).
2. Add an entry to [`manifest.json`](manifest.json):
   ```json
   {
     "name": "My Other App",
     "packageName": "com.simon.otherapp",
     "versionCode": 3,
     "versionName": "1.2",
     "apkUrl": "https://github.com/simon-liesinger/otherapp/releases/download/v3/otherapp.apk",
     "description": "What it does."
   }
   ```
3. Commit. Every phone running App Hub will see it on the next refresh.

## Releasing a new version of App Hub itself

1. Bump `VERSION_CODE` (and `VERSION_NAME`) in [`version.properties`](version.properties).
2. Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug`
3. Upload `app/build/outputs/apk/debug/app-debug.apk` as `app-hub.apk` to a new release
   (tag `vN` matching the new `versionCode`).
4. Update the App Hub entry's `versionCode` / `versionName` / `apkUrl` in `manifest.json` and commit.

> Note: releases use the Android **debug** signing key so any machine with this repo
> can cut a build. The TOFU check pins whatever key first installed on each device.
