# Architecture

Clean, layered, no DI framework, no network stack beyond the JDK (HTTP via HttpURLConnection,
JSON via kotlinx-serialization) — keeps the APK small and auditable.

```
app (Compose UI, ViewModels, wizard, navigation)
 │
 ├── core-agent   AgentOrchestrator (model ⇄ tool loop, MAX_ROUNDS=12),
 │                AgentTools (sandboxed list/read/search/write/delete/run/git),
 │                PendingChange proposals (accept/reject/revert)
 ├── core-ai      AiProvider interface; OpenAiCompatProvider (any /chat/completions
 │                server with function calling), AnthropicProvider (Messages API),
 │                DefaultProviderRegistry (catalog shown in Settings)
 ├── core-git     GitRepository (status/diff/stage/commit/pull/push/branches),
 │                porcelain v1 + unified diff parsers
 ├── core-github  GitHubClient (validate token, list/search repos, token URL handling)
 ├── core-project ProjectManager (clone/create/import/rename/delete, JSON index)
 ├── core-termux  TermuxCommandRunner (RUN_COMMAND intent + tee-tail output capture,
 │                local-process fallback), SafetyPolicy, EnvironmentDiagnostics
 ├── core-security SecretStore (Android Keystore AES-GCM)
 └── core-model   shared types (CommandEvent, …)
```

## Agent loop (zot-inspired)

1. User prompt → provider call with 8 tools (list/read/search/write/delete/run/git_status/git_diff).
2. Tool call returned → read-only tools execute immediately; **writes, deletes and destructive
   commands become proposals** — the loop pauses and the UI shows Approve/Cancel.
3. On approval the change is applied (or command run), the result is appended to the
   conversation and the loop continues — the model sees test output and can iterate.
4. Loop ends with a final answer, or at `MAX_ROUNDS`.

All provider calls, file IO and git run on `Dispatchers.IO`; the UI holds only immutable
StateFlows. No background services; the runner's coroutine scope dies with the process.
