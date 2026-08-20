# tark-embedded-harness

A Spring Boot library that embeds a Spring AI-driven cognitive agent directly into a host
application. It exposes a WebSocket endpoint for streaming agent turns, manages conversation
context so long-running sessions don't blow past the model's context window, and lets the host
register its own tools, prompts, and custom protocol messages.

The library is host-agnostic: it has no knowledge of any specific domain (math, code, support,
etc.) — that logic lives entirely in the host application's own Spring AI tools.

## What it does

- **Runs the agent loop.** Wraps a Spring AI `ChatClient`/`ChatModel` in a turn-by-turn execution
  loop, auto-invoking the host's tools and streaming deltas, tool activity, and lifecycle events
  back over a WebSocket as typed JSON messages.
- **Manages context automatically.** Tracks token pressure across a session and, once a
  configurable threshold is crossed, distills the active goal/plan and compacts older
  conversation turns into an episodic summary — without the host having to think about it.
- **Pre-flight plans and verifies.** Before executing a fresh request, generates a step plan and
  verifies it against the stated goal, refining once if verification fails.
- **Supports MCP calibration prompts.** Lets a client load a named, pre-registered prompt (e.g. a
  style/persona calibration) into a session synchronously via a `/prompt <name> key=value ...`
  command.
- **Reports live telemetry.** Emits Micrometer-based observability events (token usage, call
  duration, model info) over the same WebSocket stream, filtered by configurable categories.
- **Stays extensible.** Host applications can register their own `AgentAction`, `ClientMessage`,
  and `ServerMessage` subtypes on the shared protocol (see `TarkProtocolExtension`) and publish
  arbitrary custom actions into a session's stream via `TarkAgentActionEvent` — this is how a host
  tool (e.g. one that renders a chart) delivers its own payload to the client without the library
  needing to know anything about it.

## Requirements

- Java 21
- Spring Boot 4.x / Spring AI 2.0.0
- A `ChatModel` bean configured by the host application

## Using it in a host application

This library isn't published to a binary repository yet. Consume it from source via a Gradle
composite build in the host project's `settings.gradle.kts`:

```kotlin
includeBuild("../tark-embedded-harness")
```

and depend on it as usual:

```kotlin
dependencies {
    implementation("com.tark:tark-embedded-harness")
}
```

Auto-configuration takes over from there — the WebSocket endpoint and its supporting beans
register automatically as long as a `ChatModel` bean is present on the classpath and in context.

### Configuration (`tark.harness.*`)

| Property | Default | Description |
| --- | --- | --- |
| `model-name` | — | Optional model identifier metadata |
| `temperature` | — | Optional model temperature |
| `websocket-path` | `/ws` | Path the agent's WebSocket endpoint is registered under |
| `allowed-origins` | `*` | CORS origins allowed to connect |
| `max-memory-turns` | `20` | Turns kept before episodic compaction kicks in |
| `compaction-batch-size` | `10` | Number of older turns folded into a summary per compaction pass |
| `context-window-size` | `32768` | Token budget the context enforcer plans against |
| `pressure-threshold-percent` | `0.75` | Fraction of the window that triggers distillation/compaction |
| `distillation-threshold-characters` | `1000` | Size above which a goal/plan gets distilled instead of passed through raw |
| `initial-chars-per-token` | `4.0` | Starting ratio for the self-calibrating token estimator |
| `observability.enabled` | `true` | Whether telemetry events are broadcast |
| `observability.broadcast-categories` | `tokens, duration, model_info` | Which telemetry categories to broadcast |

## Architecture

The codebase follows a hexagonal style, split into three bounded contexts, each with its own
`domain` / `ports` / `adapters` / `application` packages:

- **`context`** — token budget enforcement, distillation, and episodic memory compaction.
- **`engine`** — pre-flight planning/verification and the turn-by-turn agent execution loop.
- **`websocket`** — the client-facing protocol, session routing, and observability messaging.

`autoconfigure` wires all of it together as Spring Boot auto-configuration beans, activating only
when their required dependencies (a `ChatModel`, a servlet web application, etc.) are present.

## Building and testing

```bash
./gradlew build
./gradlew test
```
