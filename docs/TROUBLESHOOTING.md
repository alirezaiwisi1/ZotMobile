# Troubleshooting

## Setup

**"Termux is not installed"**
Install Termux from F-Droid: https://f-droid.org/en/packages/com.termux/ — the Play Store version is unmaintained and will not receive RUN_COMMAND support correctly. Reopen Zot Mobile afterwards.

**Environment check hangs or returns nothing**
Open the Termux app once manually (first launch finishes its bootstrap), then return to Zot Mobile and re-run the check.

**"RUN_COMMAND permission not granted"**
Android Settings → Apps → Zot Mobile → Permissions → Additional permissions → allow *Run commands in Termux environment*.

**"Git was not found in the Termux environment"**
Open Termux and run `pkg update && pkg install git`, then re-run the environment check. Same pattern for `nodejs`, `python`, `golang`.

## Git / GitHub

**Clone fails with 403 / Authentication failed**
Your token is missing, expired, or lacks access to that repo. Create a fine-grained PAT (Contents: Read & write) and update Settings → GitHub.

**Push rejected (non-fast-forward)**
Someone pushed to the remote. Use the ⬇ (pull) button first, then push again.

**"This project is not a Git repository yet"**
Create the project through the Files tab (New project runs `git init`), or clone a repository instead of pointing at an arbitrary folder.

## AI

**"AI requires an internet connection"**
Only the AI chat needs network; files, terminal, git, and projects work offline. Reconnect and resend.

**"API key rejected (401)"**
Check the key in Settings → AI Provider; make sure it belongs to the selected provider.

**"Rate limit (429)"**
Wait a moment, or switch provider/model in Settings.

**Agent proposes no patches**
The agent only emits diffs for code changes. Ask it explicitly, e.g. "modify src/App.tsx so that … and give me a PATCH block".

## Commands

**`npm: command not found`** → Termux: `pkg install nodejs`
**`python: command not found`** → Termux: `pkg install python`
**Command times out** → long builds (Gradle on-device) can exceed the 5-minute runner cap; run them in stages.

## App

**Theme doesn't follow system** → Settings → Appearance → System.
**Keyboard covers the chat input** → the app resizes with the keyboard (`adjustResize`); if your device uses gesture nav, toggle fullscreen keyboard settings in Android.
