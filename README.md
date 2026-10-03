# Zot Mobile

**ChatGPT + VS Code Agent + GitHub + Terminal — designed for Android, powered locally by Termux. No VPS required.**

Inspired by the workflow of [patriceckhart/zot](https://github.com/patriceckhart/zot) (a lightweight coding-agent harness), Zot Mobile gives you a polished mobile GUI for an AI coding agent that runs **on your phone**, inside Termux — not on a cloud server.

## How it works

```
Android App (Jetpack Compose, Material 3)
        ↓  RUN_COMMAND intent (official Termux API)
      Termux  (local execution sandbox)
        ↓
Local environment: git, node, python, go …
        ↓
Git / GitHub / AI agent / project files
```

All commands execute inside the Termux sandbox via the **officially supported `com.termux.RUN_COMMAND` intent** — no root, no undocumented hacks, no remote code execution surface.

## Features

- **Setup wizard** (8 steps): welcome → environment check → Termux detection → local env → AI provider → GitHub (optional) → project → ready
- **AI chat** as the home screen: describe a change, the agent proposes unified diffs, you **Accept / Reject / Revert** each one; diffs apply atomically via `git apply`
- **File explorer**: mobile-friendly tree, tap to open in the editor, save and delete
- **Runner tab**: run `npm test`, builds, any project command — see output, exit code, duration; destructive commands require explicit confirmation
- **Git**: status, branch, log, diff viewer, commit, pull, push, clone
- **GitHub**: personal access token stored in Android Keystore-encrypted storage
- **Multi-provider AI** (abstraction, nothing hard-coded in app logic): OpenAI-compatible, Anthropic, Google AI, OpenRouter, Groq, custom OpenAI-compatible endpoints (e.g. local llama.cpp server)
- **Offline-friendly**: files, editor, git operations, projects and terminal all work offline; AI features clearly say "AI requires an internet connection" when offline
- **Light/dark theme** (system / manual), dynamic color on Android 12+, responsive layouts
- Human-readable errors with fix hints (e.g. "Git was not found in the Termux environment. Install Git and run the environment check again.")

## Build

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest # 14 unit tests (AI wires, git parsing, safety, Termux results)
```

Requirements: JDK 17, Android SDK 34. Install the debug APK on any Android 8.0+ device.

## Termux setup

1. Install **Termux from F-Droid** (Play Store builds are outdated): https://f-droid.org/en/packages/com.termux/
2. Install the app and launch the setup wizard — it runs an environment check
3. When Android asks, grant the **Run command** permission (Settings → Apps → Zot Mobile → Permissions)
4. Open Termux once, then install base tools:
   ```
   pkg update && pkg install git nodejs
   ```
   (`pkg install python` / `pkg install golang` as needed for your projects)
5. Re-run the environment check in Zot Mobile — everything should show ✓

Termux:API is **not required**. Projects live in `~/zot-projects` inside Termux.

## AI provider configuration

Settings → AI Provider → pick a provider → paste your API key.
Keys are stored with `EncryptedSharedPreferences` (Android Keystore master key) and are never logged, committed, or sent anywhere except the provider's API endpoint. Add a **custom provider** to point at any OpenAI-compatible server, including one running locally in Termux (e.g. llama.cpp).

## GitHub setup

1. Create a **fine-grained personal access token**: github.com → Settings → Developer settings → Fine-grained tokens — scope it to only the repos you need, Contents: Read & write
2. Settings → GitHub → paste token + username
3. Clone from the Files tab; commits push via `git push` using HTTPS auth

## Security model

- **No VPS / no cloud backend.** Only external services: your AI provider and GitHub.
- **Command execution** happens exclusively inside the Termux sandbox; the app has no shell of its own.
- **Safety gate**: destructive commands (`rm -rf /`, `mkfs`, `dd if=`, pipe-to-shell, etc.) require an explicit confirmation dialog before running.
- **Sandboxing**: all projects live under `~/zot-projects` in Termux; the agent prompt instructs the model never to emit destructive commands.
- **Secrets**: EncryptedSharedPreferences (Keystore-backed AES256-GCM); nothing in plain text, nothing in Git, no keys compiled into the APK.
- **AI output is reviewed, never auto-applied**: patches land as review cards with Accept/Reject/Revert; they apply through `git apply` so they are atomic and revertible.
- **Clear logs**: every command with output/exit code/duration is recorded in the local command log.

## Architecture

```
app/src/main/java/com/zot/mobile/
├── core/
│   ├── ai/        # provider catalog, wire encoders (OpenAI/Anthropic/Google), agent loop, prompt
│   ├── git/       # GitLayer — porcelain status/diff/commit/pull/push over Termux
│   ├── github/    # auth config (token via SecretStore)
│   ├── termux/    # TermuxClient (RUN_COMMAND intents), job/poller, diagnostics, safety patterns
│   ├── security/  # SecretStore (EncryptedSharedPreferences)
│   ├── settings/  # DataStore preferences (theme, provider, model)
│   └── terminal/  # output formatting
├── data/
│   ├── db/        # Room: projects, chat messages, command log
│   └── project/   # ProjectManager — FS ops + command execution via Termux
└── ui/
    ├── screens/chat|files|terminal|settings|setup   # Compose feature screens + ViewModels
    └── theme/     # Material 3, dynamic color, rounded shapes
```

State: Kotlin Coroutines + StateFlow + ViewModel. Navigation: Compose Navigation with bottom bar (Agent / Files / Run / Settings).

## Known limitations

- **Syntax highlighting** in the editor is plain monospace in this MVP (lightweight to keep memory low on phones); the viewer supports copy/select/search via text selection.
- AI agents on phones are slower than desktops — the agent keeps context lean (branch, root listing, last 20 messages).
- Local LLMs in Termux work through the "custom provider" option but need a server (e.g. llama.cpp) started in Termux.

## Troubleshooting

| Problem | Fix |
|---|---|
| "Termux is not installed" | Install from F-Droid, reopen Zot Mobile |
| Environment check hangs | Open Termux once manually, then retry |
| "RUN_COMMAND permission not granted" | Settings → Apps → Zot Mobile → Permissions → Additional permissions |
| "Git was not found in the Termux environment" | Open Termux → `pkg install git` → re-run check |
| Clone fails with 403 | Token missing/expired — update it in Settings → GitHub |
| "AI requires an internet connection" | Reconnect; only AI features need network |
| Push says non-fast-forward | Pull first (⬇ button in Files tab), then push |

## License

MIT
