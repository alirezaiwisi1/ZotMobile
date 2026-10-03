# ZotMobile

**ChatGPT + a coding agent + Git + Terminal — on your Android phone, powered locally by Termux. No VPS required.**

ZotMobile is a mobile-first Android app (Kotlin, Jetpack Compose, Material 3) inspired by the
workflow of [zot](https://github.com/patriceckhart/zot): an AI agent that reads and edits project
files, runs commands and tests, shows diffs, and loops on errors until the build is green — with
the user approving every file change.

```
Android App (this project)
    ↓  local communication (Termux RUN_COMMAND intents)
Termux  (local execution environment)
    ↓
Git / GitHub / your commands / project files
    ↓ (internet only for these)
AI provider API · GitHub API
```

## Features

- **AI agent chat** — talk to the agent in plain language; it plans, reads files, proposes edits
  (`Approve` / `Cancel` per change), runs tests, reads the output, and fixes errors in a loop.
- **Multi-provider AI** — OpenAI, Anthropic Claude, Google Gemini, OpenRouter, local Ollama,
  and any OpenAI-compatible endpoint. Keys are entered in Settings, stored in Android Keystore-
  backed encrypted storage, and never hard-coded.
- **Termux integration** — official `RUN_COMMAND` intent (Termux:API). Output is captured via a
  teed log file and streamed to the UI. A diagnostic screen checks Termux, Termux:API, Git, Node,
  Python and the project directory with fix hints for anything missing.
- **Projects** — clone from GitHub (token-authenticated), create empty projects, open, rename,
  delete, pull, push. Per-project branch, ahead/behind, modified-file count and last commit.
- **Files** — mobile file explorer and code editor with monospace viewer, works fully offline.
- **Run** — command runner with status, live output, exit code, duration, Stop / Clear / Copy,
  and a central safety policy: destructive commands require confirmation, dangerous ones are blocked.
- **Offline-first** — files, editing, local git and the terminal work without internet; the agent
  clearly reports when AI needs connectivity.
- **Light & dark theme**, bottom navigation, phone-sized components.

## Build

Requirements: JDK 17, Android SDK 34.

```bash
git clone <this repo>
cd ZotMobile
./gradlew :app:assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest         # 21 unit tests
```

Or open in Android Studio and press Run.

## Documentation

- [Installation & Termux setup](docs/INSTALLATION.md)
- [AI provider configuration](docs/AI_PROVIDERS.md)
- [GitHub setup](docs/GITHUB_SETUP.md)
- [Security model](docs/SECURITY.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Architecture](docs/ARCHITECTURE.md)

## Project layout

```
app/            UI (Compose), ViewModels, setup wizard, navigation
core-ai/        AI provider abstraction (OpenAI-compat + Anthropic clients)
core-agent/     Agent loop, sandboxed file tools, proposals
core-termux/    Termux RUN_COMMAND runner, safety policy, diagnostics
core-git/       Git status/diff/commit/push/pull layer
core-github/    GitHub REST client, token handling
core-project/   Project list, clone/create/rename/delete
core-security/  Keystore-encrypted secret storage
core-model/     Shared data types
```

## License

MIT
