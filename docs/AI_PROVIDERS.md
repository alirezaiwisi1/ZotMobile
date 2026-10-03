# AI provider configuration

ZotMobile has **no built-in provider and no hard-coded keys**. OpenAI-compatible endpoints and
Anthropic are supported out of the box.

## Supported providers (Settings → AI provider)

| Provider | Base URL (default) | Example model | Where to get a key |
|---|---|---|---|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | platform.openai.com/api-keys |
| Anthropic Claude | `https://api.anthropic.com/v1` | `claude-sonnet-4-5` | console.anthropic.com |
| Google Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.0-flash` | aistudio.google.com/apikey |
| OpenRouter | `https://openrouter.ai/api/v1` | any OpenRouter model | openrouter.ai/keys |
| Ollama (local) | `http://127.0.0.1:11434/v1` | `qwen2.5-coder:7b` | none — runs in Termux |
| Custom | any `/chat/completions` server | — | your server |

## How to configure

1. Settings → **AI provider** → pick a provider (tap its name so it shows ●).
2. Paste the **API key**, optionally override **model** and **base URL**, tap **Save**.
3. Keys are encrypted (AES-GCM, Android Keystore master key) before storage and are sent only
   to the provider host you configured.

## Free / local options

- **Ollama in Termux**: `pkg install ollama`, then `ollama serve` and `ollama pull qwen2.5-coder:7b`
  in Termux. In ZotMobile pick *Ollama* — no key needed, fully offline agent.
- **OpenRouter free models**: several models have a `:free` variant.

## Notes

- The agent uses function-calling (tool use) when the model supports it; models without tool
  support still work for reading/explaining but cannot propose file edits.
- Agent quality depends heavily on the model; for edit/test/fix loops a strong coding model is
  recommended.
