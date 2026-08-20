# Feature Gap Report: Scala Tark Backend vs. tark-embedded-harness

This report identifies the core structural, behavioral, and feature gaps between the original Scala-based `tark` backend (which runs as a standalone server orchestrating subprocess sandboxes) and our new Java-based `tark-embedded-harness` (which runs as an embedded Spring AI-driven library).

---

## 1. High-Level Architecture Paradigm

| Dimension | Scala Tark Backend | tark-embedded-harness (Java Library) | Gap & Architectural Impact |
| :--- | :--- | :--- | :--- |
| **Orchestration Model** | Custom ReAct loop engine (`ReActLoopEngine.scala`) built with functional streams (`fs2.Stream`) and Cats Effect fibers (`IO`). | Automatic recursive function-calling loop managed by Spring AI's `ChatClient` using Project Reactor (`Flux`). | **None (Bridged).** Spring AI's internal loop simplifies the code significantly while remaining highly performant. |
| **Execution Mode** | Standalone server hosting standard-io MCP servers and managing sub-process executors. | Embedded library run inside the host application, exposing a WebSocket endpoint. | **None (Desired).** Aligning perfectly with your goal to let host apps easily embed the agent. |

---

## 2. Core Feature Gaps

### Gap 1: Suspendable Interactivity (The Questionnaire / Choice Problem)
* **Scala Tark Implementation:** 
  Tark supports interactive tools (like asking the user a free-form question or offering a list of choices, e.g., `RequestChoice` or `RequestInput`). When an interactive tool is hit, the Cats Effect fiber uses a `Deferred[IO, String]` to **suspend execution** without blocking threads, broadcasts the request over the WebSocket, waits for the client's `InputResponse` or `ChoiceResponse`, and resumes the fiber once the response is written.
* **Current Java Library Status:** 
  Spring AI expects tool executions (implementations of `ToolCallback`) to be **synchronous, blocking, and immediate**. There is no built-in support for suspending the execution loop to ask the user a question over a WebSocket and resuming later.
* **Resolution Strategy:** 
  We must implement a custom suspension mechanism using Java `CompletableFuture` or Project Reactor `Mono.sink` inside our `TracingFunctionCallbackWrapper`. When an interactive tool is called, it registers a pending future, sends the interactive action, blocks/waits reactive-style on that future, and completes it when the WebSocket handler receives the user's response.

---

### Gap 2: Sandbox Isolation (Security)
* **Scala Tark Implementation:** 
  Tark runs a `DockerSandbox` using the Docker Java client. It starts isolated Docker containers (`tark-sandbox:latest`) to run arbitrary terminal commands, compilation tasks, or test suites, ensuring that the host machine's filesystem remains 100% safe from the agent.
* **Current Java Library Status:** 
  Tools are executed **natively** in-process (Zero-IPC). This is extremely fast and perfect for domain-enabling APIs, but if an agent is allowed to execute arbitrary bash commands or modify files, it does so directly on the host JVM process, creating a major security risk.
* **Resolution Strategy:** 
  For embedded mode, we must make a distinction between **Safe Tools** (native JVM methods, read-only lookups) and **Risk Tools** (arbitrary shell commands). If shell command execution is required, we must compile a Docker-based executor bean that matches our `ToolCallback` interface.

---

### Gap 3: Pre-Flight Planning & Plan Verification
* **Scala Tark Implementation:** 
  Tark divides execution into distinct cognitive phases:
  1. **Intake Phase:** Extracts the `GoalContract` from the user.
  2. **Planning Phase:** Invokes a `TaskPlanner` to decompose the goal into sequential sub-tasks.
  3. **Verification Phase:** Invokes an `OllamaPlanVerifier` to run a self-critique pass on the plan, refining it *before* any tools are run.
* **Current Java Library Status:** 
  The engine is purely ReAct-based: the prompt is sent straight to the model, and the model starts executing tools immediately. We have scaffolded `AgentState` (with plans and steps), but we do not have the orchestrator that forces these separate cognitive pre-flight steps.
* **Resolution Strategy:** 
  We can create a multi-step `AgentOrchestrator` service that wraps the `EmbeddedAgentEngine`. Before running the ReAct loop, it can run distinct structured prompts (`PlanPrompt`, `VerifyPlanPrompt`) to populate the `AgentState` before passing it down.

---

### Gap 4: WebSocket Server & Protocol Router
* **Scala Tark Implementation:** 
  Houses `WebSocketHandler` which binds the HTTP route `/ws`, handles connection lifecycles, parses incoming JSON frames into `ClientMessage` types, and maps outgoing actions (`delta`, `tool_start`, `usage`) to `ServerMessage` JSON frames.
* **Current Java Library Status:** 
  Base packages are defined, but the actual Spring `WebSocketHandler` and `WebSocketConfigurer` have not been written.
* **Resolution Strategy:** 
  We must implement `TarkWebSocketHandler` (extending Spring's `TextWebSocketHandler`) to map incoming client payloads to our engine, instantiate the reactive `Flux` stream, and flush formatted JSON payloads down the socket.

---

### Gap 5: External MCP Client Integration
* **Scala Tark Implementation:** 
  Can connect to external MCP servers (like a python file-system server or a third-party DB server) over stdio or SSE.
* **Current Java Library Status:** 
  Focuses entirely on native tools defined within the host JVM. It does not yet connect to external standard-io MCP servers.
* **Resolution Strategy:** 
  Spring AI provides standard MCP clients (`McpClient`). We can write an auto-configuration adapter that connects to external MCP servers listed in a configuration file and registers their tools as `ToolCallback` objects in our engine automatically.

---

## 3. Summary of Gap Gaps

| Gap Category | Priority | Difficulty | Complexity Source |
| :--- | :--- | :--- | :--- |
| **Interactive Tool Suspension** | High | Hard | Bridging Spring AI's synchronous tool signature with reactive/async WebSocket responses. |
| **WebSocket Handler & Protocol** | High | Medium | Standard Spring WebSocket boilerplate. |
| **Pre-Flight Planning Phase** | Medium | Medium | Orchestrating multiple distinct model turns. |
| **Sandbox Isolation** | Low | Hard | Porting Docker JVM client libraries. |
| **External MCP Client** | Low | Medium | Spring AI McpClient configuration. |
