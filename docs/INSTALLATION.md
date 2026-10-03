# Installation & Termux setup

## 1. Install the app

- Build from source (`./gradlew :app:assembleDebug`) and sideload `app-debug.apk`, or install a
  release build. Android 8.0+ (API 26) required.

## 2. Install Termux (required)

ZotMobile runs all commands (git, npm, python, your tests) inside **Termux**, the Android
terminal emulator. Install **both** apps from **F-Droid** (the Play Store builds are outdated and
incompatible with each other):

1. Termux — https://f-droid.org/en/packages/com.termux/
2. Termux:API — https://f-droid.org/en/packages/com.termux.api/

The app's first-launch wizard detects what is missing and links straight to both pages.

> **Important:** install Termux and Termux:API from the *same source* (both F-Droid), otherwise
> Android's signature check blocks communication between them.

## 3. Grant the command permission

On first command, Android asks *"Allow ZotMobile to run commands in Termux environment?"* —
choose **Allow**. (This is Termux's official RUN_COMMAND permission.)

## 4. One-time Termux preparation

Open Termux once and run:

```bash
pkg update
pkg install git        # required for clone/pull/push
pkg install nodejs     # only if your projects need Node
pkg install python     # only if your projects need Python
```

Nothing else is needed inside Termux — normal use never requires typing terminal commands;
ZotMobile drives Termux for you.

## 5. Wizard

On first launch the setup wizard walks through: welcome → Termux checks → AI provider + key →
GitHub token (optional) → ready. You can rerun the checks anytime in **Settings → Environment**.

## Local file locations

Projects are cloned into the app's private storage:
`/data/data/com.zotmobile.app/files/projects/<name>`. Termux reaches them because ZotMobile
invokes Termux's run-command service with that working directory — no root, no storage
permission prompts for your personal files.
