---
title: DeepSeek Harness Integration
description: Install DeepSeek Harness and configure it as a digital expert runtime in HjjMate.
head:
  - - meta
    - name: keywords
      content: DeepSeek Harness,DSH,digital expert,Agent runtime,JSON-RPC,Cordis
---

# DeepSeek Harness Integration

This guide connects the official DeepSeek Harness (DSH) to HjjMate and creates a digital expert powered by the DSH runtime.

In HjjMate, DSH is an **expert runtime**, not an MCP tool and not a regular plugin. MCP supplies tools; DSH owns the external Agent process, the ReAct loop, and the event stream that HjjMate projects into the conversation UI.

## Architecture

```text
HjjMate Chat / SSE
        |
        v
DSH Runtime Provider
        |
        v  JSON-RPC over stdin/stdout
dsh-jsonrpc-agent
        |
        v
DeepSeek API + Cordis composition
```

HjjMate remains responsible for experts, sessions, permissions, workspaces, message persistence, and UI projection. DSH runs the turn. HjjMate injects the API key from the DeepSeek provider configuration into the DSH child process. Never put secrets in `runtimeConfig`, expert prompts, or the repository.

## Prerequisites

- macOS, Linux, or Windows (commands below use macOS / Linux syntax)
- JDK 21
- A running HjjMate backend and frontend
- A DeepSeek API key
- A built DSH JSON-RPC agent
- The Cordis configuration from the DSH checkout

Check the runtime files:

```bash
"$DSH_JSONRPC_AGENT" --help
test -x "$DSH_JSONRPC_AGENT"
test -f "$DSH_CORDIS_CONFIG"
```

## Install DSH

Follow the [official DeepSeek Harness repository](https://github.com/deepseek-ai/deepseek-harness) for the current build instructions. The build must provide these two paths:

```text
<dsh-root>/dist-exe/dsh-jsonrpc-agent-pkg-<platform>
<dsh-root>/python/sdk-runtime/src/deepseek_harness_runtime/runtime/cordis.yml
```

Keep the DSH binary outside the HjjMate source tree. Configure its location with environment variables.

## Configure the IDEA backend

Open **Run | Edit Configurations...** in IDEA, select the HjjMate Spring Boot configuration, and add these variables under **Environment variables**:

```text
DSH_JSONRPC_AGENT=/absolute/path/to/dsh-jsonrpc-agent-pkg-macos-arm64
DSH_CORDIS_CONFIG=/absolute/path/to/cordis.yml
DSH_CWD=/absolute/path/to/mateclaw-workspace
```

Example:

```text
DSH_JSONRPC_AGENT=/opt/deepseek-harness/dist-exe/dsh-jsonrpc-agent-pkg-macos-arm64
DSH_CORDIS_CONFIG=/opt/deepseek-harness/python/sdk-runtime/src/deepseek_harness_runtime/runtime/cordis.yml
DSH_CWD=/var/lib/mateclaw/workspace
```

`DSH_CWD` must be readable and writable by the backend process. Use absolute paths in the IDEA run configuration. Restart the backend after changing them; Spring Boot does not hot-reload process environment variables.

## Configure the DeepSeek provider

1. Sign in to HjjMate.
2. Open **Settings → Models**.
3. Configure and enable the **DeepSeek** provider.
4. Enter the DeepSeek API key and base URL.
5. Confirm that at least one enabled DeepSeek chat model exists.

The default DSH model is `deepseek-v4-flash`. If the expert has no explicit model, HjjMate uses the global model name and injects credentials from the `deepseek` provider. A custom model must be usable by the DeepSeek provider route in DSH.

## Create a DSH digital expert

Open **Digital Experts → New**:

1. Enter the expert name, role, and goal.
2. Select **DSH / DeepSeek Harness** as the runtime.
3. Set a workspace; when blank, `DSH_CWD` is used.
4. Use a JSON object for `runtimeConfig`, for example:

```json
{
  "mode": "qa",
  "workspace": "default",
  "policy": "read-only"
}
```

5. Save the expert and open its chat.

Runtime configuration describes expert policy only. Do not put `DEEPSEEK_API_KEY`, cookies, bearer tokens, or sensitive local paths in it.

## Verification checklist

Send this message in the DSH expert conversation:

```text
Reply with exactly: DSH_RUNTIME_OK
```

Success means:

- The expert header shows `DSH Harness`.
- Thinking state and text deltas appear in the chat.
- The log contains `provider=deepseek` and `apiKeyConfigured=true`.
- The log contains a `turn/end` event with `kind=completed`.
- The UI does not show “no output for this run”.
- The same conversation is not used to start two different DSH live sessions.

Do not reuse a completed test `conversationId` for a new DSH live session. DSH detects a mismatch between the persisted session log and the new live session and reports `id collision`. Use **New conversation** for every fresh runtime test.

## Logs and diagnostics

The backend log is commonly located at:

```text
logs/mateclaw.log
```

Search for the runtime signals:

```bash
rg "\[DSH\]|MISSING_CREDENTIAL|EMPTY_RESPONSE|id collision" logs/mateclaw.log
```

The admin-only diagnostics endpoint is:

```http
GET /api/v1/admin/agent-runtime/dsh/diagnostics
```

It returns command, executable, Cordis, and capability status without returning the API key.

## Troubleshooting

### `MISSING_CREDENTIAL`

Check that:

1. The DeepSeek provider is enabled under Settings → Models.
2. The API key was saved successfully.
3. The expert uses DSH rather than configuring the DSH binary as an MCP command.
4. The backend was restarted with the updated IDEA configuration.

`apiKeyConfigured=false` in the log means the credential did not reach the DSH child process.

### `EMPTY_RESPONSE`

Check model availability and the base URL. Verify with a short fixed prompt before adding tools or skills.

### `dsh.command_unavailable`

`DSH_JSONRPC_AGENT` must point to an executable file:

```bash
chmod +x /absolute/path/to/dsh-jsonrpc-agent-pkg-macos-arm64
```

### `dsh.cordis_missing`

`DSH_CORDIS_CONFIG` must point to the actual `cordis.yml`, not only the package directory. If a package directory is provided, HjjMate also checks its `runtime/cordis.yml` child path.

### The answer appears twice

Use the latest backend version. DSH emits text deltas followed by a final assistant snapshot. HjjMate must project the deltas only and must not append the snapshot again.

## MCP, plugins, and DSH

| Mechanism | Best for | Replaces DSH? |
|-----------|----------|--------------|
| MCP | File, GitHub, database, and other tools | No |
| Plugin | Extending HjjMate tools, models, channels, or memory | No |
| DSH expert runtime | Hosting the DeepSeek Harness Agent loop and process | It is the expert runtime, not a tool |

The recommended composition is: **DSH as the expert runtime, MCP as the tool layer, and HjjMate as the governance and visualization layer**.

## Security recommendations

- Store API keys only in HjjMate provider configuration or a controlled environment.
- Give DSH a dedicated workspace instead of the whole user home directory.
- Start with a read-only policy and the smallest possible tool set.
- Never commit `.sessions/`, logs, or local credential configuration.
- In production, restrict the DSH child process filesystem, network, and credential access.
