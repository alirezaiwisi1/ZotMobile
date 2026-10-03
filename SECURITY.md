# Security

Threat model: the app executes code on the user's own device, talks to external AI APIs and GitHub. The priorities are (1) no unauthorized command execution, (2) no secret leakage, (3) user consent for destructive actions.

## Command execution

- All execution goes through the **official Termux RUN_COMMAND intent** (`com.termux.RUN_COMMAND`) inside the Termux app sandbox. Zot Mobile has no shell and cannot execute anything if Termux is absent.
- **Allowlist-style safety check**: `TermuxClient.DESTRUCTIVE_PATTERNS` flags `rm -rf /`, `rm -rf ~`, `mkfs`, `dd if=`, `shutdown`, `reboot`, fork bombs, `chmod 777 /`, and pipe-to-shell (`curl … | sh`) patterns. Flagged commands require an explicit per-invocation confirmation dialog; nothing dangerous runs silently.
- **No remote trigger**: there is no server component, no webhook, no intent that lets other apps submit commands. Commands originate only from user input in the UI.
- **Project sandboxing**: file operations are rooted at `~/zot-projects/<project>` inside Termux; paths are single-quoted/escaped at the command layer.
- **AI output is never executed directly.** The agent proposes unified diffs which are applied via `git apply` (atomic, revertible) after explicit user Accept.

## Secrets

- API keys and GitHub tokens are stored in **EncryptedSharedPreferences**, backed by an Android Keystore AES-256-GCM master key. Not accessible without device unlock, not included in backups (`allowBackup=false`).
- Secrets are never logged, never written to plain-text files, never committed (no keys exist in the repository), and only transmitted to the configured provider/GitHub endpoints over HTTPS.
- Settings → Security → "Erase all stored credentials" deletes every stored secret.

## Data

- Room DB (projects, chat history, command log) is local-only, in app-private storage.
- The GitHub token should be a **fine-grained PAT scoped to specific repos with Contents read/write only**; the README and in-app instructions say so.

## Reporting

Open a GitHub issue for security concerns; for sensitive reports contact the maintainer directly.
