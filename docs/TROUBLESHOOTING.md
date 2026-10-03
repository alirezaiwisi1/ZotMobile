# Troubleshooting

Everything below is also surfaced in-app with **Fix / Retry** buttons wherever possible.

## "Termux could not be started"

- Termux or Termux:API missing → install both from **F-Droid** (same source!), then rerun the
  environment check (Settings → Environment → Re-run checks).
- Permission dialog denied → Settings (Android) → Apps → Termux → Permissions, or just trigger a
  command again and choose **Allow** on the *"Run commands in Termux environment"* prompt.

## "Git was not found in the Termux environment"

Open Termux and run `pkg install git`, then rerun the environment check.

## Clone fails

- *"GitHub rejected the credentials"* → add/refresh the token (Settings → GitHub). Private repos
  need a token with Contents access.
- *"No internet connection"* → cloning needs the network; editing works offline.

## Push fails with "GitHub rejected the credentials"

Set your git identity (Settings → Git identity) and confirm the token has **Contents: read/write**.

## "AI requires an internet connection…"

Expected offline behavior. File browsing, editing, local git and the terminal keep working.

## Agent says "No API key set for …"

Add the key in Settings → AI provider for the *selected* provider (the one marked ●).

## Command hangs as "running"

Termux processes don't report completion if killed externally. Use the **Stop** button; the
runner also times out. If Termux itself was force-closed, reopen it once.

## The agent proposes an edit but nothing changes on disk

Nothing is written until you tap **Approve**. Rejected proposals are kept in the conversation
("USER REJECTED …") so the agent adjusts.

## Output looks garbled for long-running commands

The output file is tailed every 250 ms; very fast output is batched. Final output is complete
once the exit code line appears.

## Reset everything

Android Settings → Apps → ZotMobile → Clear data (removes projects, keys, settings).
