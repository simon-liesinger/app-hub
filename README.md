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

## Adding a project to the catalog (new or existing)

Use [`tools/add-app.sh`](tools/add-app.sh). Point it at a **built APK** and it does the
rest — reads the package name + version straight out of the APK, publishes the APK as a
GitHub release asset in this repo, and upserts the entry in [`manifest.json`](manifest.json)
(keyed by package name, so re-running the same command just updates that app).

```bash
# 1. Build the app's APK however that project builds (debug is fine — see note below).
#    e.g. for a gradle project:
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk \
  ./gradlew assembleDebug

# 2. Add / update it in the catalog:
tools/add-app.sh \
  --apk path/to/app-debug.apk \
  --name "My App" \
  --desc "What it does."

# 3. Commit the manifest change and push (the release is already live):
git -C ~/app-hub commit -am "catalog: add My App" && git -C ~/app-hub push
```

Every phone running App Hub sees it on the next refresh. To ship a **new version** of an
app later, rebuild with a higher `versionCode` and run the exact same `add-app.sh` command —
it publishes a new release and bumps the entry; phones then show an **Update**.

### Marking an app deprecated

To flag an app as superseded (greyed out, sorted to the bottom, no install button):

```bash
tools/add-app.sh --name "Old App" --package com.simon.oldapp --deprecated \
  --desc "Replaced by New App."
```

### A note on signing

`add-app.sh` publishes whatever APK you hand it. Because App Hub verifies (TOFU) that an
update is signed with the **same key** as the already-installed copy, keep signing each app
consistently. Debug builds on this machine all share `~/.android/debug.keystore`, so they
stay mutually consistent — that's what the current catalog uses. Apps whose original release
was signed with a different key should be re-signed with that same key (or friends reinstall
fresh, which always works).

## Releasing a new version of App Hub itself

1. Bump `VERSION_CODE` (and `VERSION_NAME`) in [`version.properties`](version.properties).
2. Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew assembleDebug`
3. Upload `app/build/outputs/apk/debug/app-debug.apk` as `app-hub.apk` to a new release
   (tag `vN` matching the new `versionCode`).
4. Update the App Hub entry's `versionCode` / `versionName` / `apkUrl` in `manifest.json` and commit.

> Note: releases use the Android **debug** signing key so any machine with this repo
> can cut a build. The TOFU check pins whatever key first installed on each device.
