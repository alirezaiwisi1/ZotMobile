# Security model

ZotMobile executes code on your device, so security is a design constraint, not a feature.

## Command execution

- **Single gate** — every command (yours or the agent's) passes through
  `core-termux/SafetyPolicy.evaluate()` before execution:
  - **Denied outright**: `rm -rf /` (outside project trees), `mkfs`/`dd if=`/`shred`,
    fork bombs, `chmod -R 777 /`, user/credential manipulation, `curl … | sh`
    pipe-to-shell installs, reading SSH private keys.
  - **Needs explicit confirmation**: any `rm`, hard resets, force pushes, `git clean`,
    kill/pkill, package uninstalls. The UI shows the exact command and requires a tap on
    *Run it*.
  - **Allowed**: everything else (build/test/git/dev servers).
- **No remote command execution**: there is no server component, no webhook, no way for the
  AI provider or any network peer to run commands. Only the on-device UI and the agent loop
  (through the same SafetyPolicy) can execute anything.
- **Sandboxing**: agent file tools resolve and canonicalize every path and reject anything
  outside the open project root, including `..` traversal and absolute paths.

## Secrets

- API keys and the GitHub token are AES-GCM-encrypted with a non-exportable **Android
  Keystore** key; ciphertext lives in app-private preferences. They never appear in chat
  transcripts, logs, or git operations.
- Clone URLs containing the embedded token are never persisted; the stored remote is sanitized.
- The app never prints secrets; the agent system prompt forbids requesting or echoing them.

## Termux integration

- Uses the official, documented `com.termux.RUN_COMMAND` intent — no hacks, no root, no
  undocumented APIs. The `<queries>` manifest declarations disclose the integration.
- Commands run with Termux's own permissions inside its sandbox.

## Data

- `allowBackup=false` — secrets are not included in Android cloud backups.
- No analytics, no telemetry, no third-party SDKs. Network egress is limited to the AI
  provider you configure and api.github.com.

## Known limitations

- Termux:API's RUN_COMMAND does not stream output over intents; ZotMobile wraps commands with a
  redirect into app-private storage and tails the file. A stopped/killed Termux process may
  leave a "running" entry until the timeout.
- The safety regexes catch common dangerous patterns but are not a substitute for reading a
  command before approving it.
