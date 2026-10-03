# GitHub setup

## Token

1. Go to https://github.com/settings/tokens → **Generate new token (fine-grained)**.
2. Give it access only to the repositories you need.
3. Permissions: **Contents: Read and write** (clone, push, pull). For listing your repos also
   allow **Metadata: Read**.
4. In ZotMobile: Settings → **GitHub** → paste the token → **Save token** → **Test**.

The token is encrypted with the Android Keystore, never written to logs, never embedded in
remotes that get stored, and never committed (`.gitignore` plus the app design keep it out of
repositories).

## What you can do in the app

- **Clone** — Projects → *Clone from GitHub* → enter `owner/repository` or a full HTTPS URL.
  Private repos work once the token is set.
- **Pull / Push** — per-project buttons (push requires your git identity in Settings).
- **Branches** — checkout and create branches (agent and terminal can also use plain `git`).
- **Commit** — after the agent's changes are approved, use the terminal or ask the agent:
  "commit and push these changes" (it will stage, commit with your message, push).

## SSH remotes

Not supported in-app (no SSH agent on Android without extra setup). Use HTTPS remotes with the
token; GitHub rejects push over token-less HTTPS, which the app surfaces as a plain-language
error pointing to Settings.
