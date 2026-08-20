package com.tark.harness.engine.adapters;

import com.tark.harness.autoconfigure.TarkHarnessProperties;
import com.tark.harness.context.domain.AgentState;
import com.tark.harness.context.domain.ContextDistiller;
import com.tark.harness.context.domain.EpisodicMemoryCompactor;
import com.tark.harness.context.domain.EpisodicSummarizer;
import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.context.domain.TextCompletionModel;
import com.tark.harness.context.domain.TokenEstimator;
import com.tark.harness.context.adapters.EpisodicMemoryStoreAdapter;
import com.tark.harness.context.adapters.SpringAiTextCompletionModel;
import com.tark.harness.context.application.ContextBudgetEnforcer;
import com.tark.harness.engine.domain.McpPromptMessageTranslator;
import com.tark.harness.engine.domain.PromptCommand;
import com.tark.harness.engine.domain.PromptCommandParser;
import com.tark.harness.engine.domain.TurnLifecycleMessages;
import com.tark.harness.websocket.TarkAgentActionEvent;
import com.tark.harness.websocket.TarkObservabilityEvent;
import com.tark.harness.websocket.protocol.AgentAction.*;
import com.tark.harness.websocket.protocol.ServerMessage;
import com.tark.harness.websocket.protocol.ServerMessage.ActionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the embedded agent's turn-by-turn execution loop: builds a Spring AI {@code ChatClient}
 * for each turn with the host's tools and the harness's context-management advisors attached,
 * and streams the resulting conversation (token deltas, tool activity, lifecycle status) back as
 * {@link ServerMessage}s. Also handles MCP-prompt calibration commands and routes external tool
 * and observability events into the right session's stream.
 */
public class EmbeddedAgentEngine {

	private static final Logger log = LoggerFactory.getLogger(EmbeddedAgentEngine.class);

	private final ChatModel chatModel;
	private final TarkHarnessProperties properties;
	private final TarkPromptRegistry promptRegistry;
	private final ObjectMapper objectMapper;
	private final EpisodicSummarizer summarizer;
	private final TokenEstimator tokenEstimator;
	private final EpisodicMemoryStoreAdapter chatMemory;
	private final ContextBudgetEnforcer budgetEnforcer;
	private final PromptCommandParser promptCommandParser = new PromptCommandParser();
	private final McpPromptMessageTranslator promptMessageTranslator = new McpPromptMessageTranslator();
	private final TurnLifecycleMessages turnLifecycleMessages = new TurnLifecycleMessages();

	private final AtomicReference<GoalContract> activeContractRef = new AtomicReference<>(GoalContract.empty());
	private final AtomicReference<AgentState> activeStateRef = new AtomicReference<>(AgentState.empty());
	private final Map<String, Sinks.Many<ServerMessage>> activeSinks = new ConcurrentHashMap<>();

	public EmbeddedAgentEngine(
			ChatModel chatModel,
			TarkHarnessProperties properties,
			TarkPromptRegistry promptRegistry,
			ChatMemoryRepository chatMemoryRepository,
			ObjectMapper objectMapper) {
		this.chatModel = chatModel;
		this.properties = properties;
		this.promptRegistry = promptRegistry;
		this.objectMapper = objectMapper;

		TextCompletionModel completionModel = new SpringAiTextCompletionModel(chatModel);
		ContextDistiller distiller = new ContextDistiller(completionModel, properties.getDistillationThresholdCharacters());
		this.summarizer = new EpisodicSummarizer(completionModel);
		this.tokenEstimator = new TokenEstimator(properties.getInitialCharsPerToken());

		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				this.summarizer, properties.getMaxMemoryTurns(), properties.getCompactionBatchSize());
		this.chatMemory = new EpisodicMemoryStoreAdapter(chatMemoryRepository, compactor);

		this.budgetEnforcer = new ContextBudgetEnforcer(
				distiller, this.tokenEstimator, this.chatMemory, properties.getContextWindowSize(), properties.getPressureThresholdPercent());
	}

	public ChatMemory getChatMemory() {
		return chatMemory;
	}

	/**
	 * True once a session has real conversation turns beyond its initial style calibration - in
	 * which case a new prompt continues straight into the ReAct loop instead of going through
	 * pre-flight planning again.
	 */
	public boolean hasActiveConversation(String sessionId) {
		List<Message> history = chatMemory.get(sessionId);
		if (history == null || history.isEmpty()) {
			return false;
		}
		return history.size() > 2;
	}

	/**
	 * Updates the active goal contract that guides enforcements and distillation.
	 */
	public void updateGoalContract(GoalContract contract) {
		activeContractRef.set(contract);
		log.info("Active goal contract updated: {}", contract.goal());
	}

	/**
	 * Updates the active agent state representing execution steps and plan progress.
	 */
	public void updateAgentState(AgentState state) {
		activeStateRef.set(state);
		log.info("Active agent state plan progress updated: step {}", state.currentStep());
	}

	/**
	 * Runs a single user prompt through the agent loop, auto-executing tools,
	 * applying custom advisors, and streaming telemetry actions via Flux.
	 *
	 * @param sessionId Unique session ID for conversation history isolation.
	 * @param promptText The user's unstructured request.
	 * @param rawCallbacks The host application's raw local tool callbacks.
	 * @return A Flux stream of ServerMessages.
	 */
	public Flux<ServerMessage> execute(
			String sessionId,
			String promptText,
			List<ToolCallback> rawCallbacks) {

		log.info("Initiating embedded agent execution for session '{}', prompt: '{}'", sessionId, promptText);

		Sinks.Many<ServerMessage> sessionSink = Sinks.many().multicast().onBackpressureBuffer();
		activeSinks.put(sessionId, sessionSink);

		ToolCallback[] wrappedCallbacks = rawCallbacks.stream()
				.map(callback -> new TracingFunctionCallbackWrapper(callback, sessionSink))
				.toArray(ToolCallback[]::new);

		ChatClient chatClient = ChatClient.builder(chatModel)
				.defaultAdvisors(
						MessageChatMemoryAdvisor.builder(chatMemory).build(),
						new GoalContractAdvisor(activeContractRef::get),
						new AgentStateAdvisor(activeStateRef::get),
						new ContextBudgetAdvisor(
								budgetEnforcer,
								tokenEstimator,
								activeContractRef::get,
								sessionSink
						)
				)
				.defaultTools((Object[]) wrappedCallbacks)
				.defaultSystem("""
						You are an elite, highly precise CBSE Mathematics Assistant. You have access to a suite of advanced calculation tools.

						CRITICAL SELF-CORRECTION & GROUNDING GUARDRAILS:
						1. GROUNDING MANDATE: All your final answers, math formulas, derivatives, steps, and intermediate values MUST be strictly grounded and verified by actual, successful tool executions. You are strictly forbidden from assuming, inventing, or speculating any numerical or algebraic coefficients unless they have been explicitly returned by a successful tool execution.
						2. TOOL FAILURE SELF-CORRECTION: If a tool execution fails or returns an error (e.g., "Error executing tool: ..."), you MUST NOT bypass the failure or fabricate/pretend you got a valid output. Instead, you must:
						   - Carefully analyze the error message.
						   - Identify what expression, syntax, or argument format was invalid.
						   - Self-correct on-the-fly and CALL THE TOOL AGAIN with corrected parameters (for example, decomposing a product rule term like 'x^3 * sin(x)' into 'x^3' and 'sin(x)' separately, executing them individually, and then combining them step-by-step yourself).
						   - Continue executing corrected tool calls until you obtain verified, successful results.
						3. NO HALLUCINATIONS: You are strictly forbidden from inventing calculation outputs, making up mock steps, or pretending a failed tool call succeeded. All outputs must reflect actual, grounded tool results.
						""")
				.build();

		Flux<ServerMessage> chatClientFlux = chatClient.prompt()
				.user(promptText)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
				.toolContext(Map.of("chat_memory_conversation_id", sessionId))
				.stream()
				.content()
				.map(delta -> (ServerMessage) new ActionMessage(new DeltaAction(delta)))
				.doOnComplete(() -> {
					log.info("Chat client delta stream completed successfully. Closing session sink.");
					turnLifecycleMessages.onCompleted().forEach(sessionSink::tryEmitNext);
					sessionSink.tryEmitComplete();
				})
				.doOnError(err -> {
					log.error("Chat client delta stream encountered an error", err);
					turnLifecycleMessages.onFailed(err).forEach(sessionSink::tryEmitNext);
					sessionSink.tryEmitError(err);
				});

		return Flux.merge(chatClientFlux, sessionSink.asFlux())
				.doOnSubscribe(sub -> turnLifecycleMessages.onStarted().forEach(sessionSink::tryEmitNext))
				.doFinally(signal -> {
					log.info("Merged stream for session '{}' terminated with signal: {}", sessionId, signal);
					activeSinks.remove(sessionId);
				});
	}

	/**
	 * Loads a named style/calibration prompt (registered via MCP) into the session's memory and
	 * confirms back to the client synchronously, bypassing the async ReAct loop entirely.
	 *
	 * @param session Active WebSocket session.
	 * @param commandText The raw command string, e.g. "/prompt explain_for_grade grade=10"
	 */
	public void executePromptCommandSynchronously(WebSocketSession session, String commandText) {
		String sessionId = session.getId();
		log.info("Processing local prompt command synchronously: '{}' in session '{}'", commandText, sessionId);
		try {
			PromptCommand command = promptCommandParser.parse(commandText);

			io.modelcontextprotocol.spec.McpSchema.GetPromptResult result =
					promptRegistry.executePrompt(command.name(), command.arguments());

			List<Message> springAiMessages = promptMessageTranslator.translate(result.messages());
			chatMemory.add(sessionId, springAiMessages);
			log.info("Successfully calibrated and injected {} style guide messages to ChatMemory.", springAiMessages.size());

			String desc = result.description() != null ? result.description() : ("CBSE calibration for " + command.name());

			sendServerMessage(session, new ActionMessage(new SystemAction("Style calibration loaded: " + desc)));
			sendServerMessage(session, new ActionMessage(new EndAction()));
		} catch (Exception e) {
			log.error("Failed to execute local prompt command synchronously in session '{}'", sessionId, e);
			sendServerMessage(session, new ActionMessage(new SystemAction("Calibration failed: " + e.getMessage())));
			sendServerMessage(session, new ActionMessage(new EndAction()));
		}
	}

	private void sendServerMessage(WebSocketSession session, ServerMessage message) {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
		} catch (Exception e) {
			log.error("Failed to send ServerMessage to session '{}'", session.getId(), e);
		}
	}

	/**
	 * Pipes an externally-published {@link TarkAgentActionEvent} (e.g. from a host tool) into
	 * the matching session's active stream.
	 */
	@EventListener
	public void handleAgentActionEvent(TarkAgentActionEvent event) {
		Sinks.Many<ServerMessage> sink = activeSinks.get(event.getSessionId());
		if (sink != null) {
			log.info("Piping external AgentAction '{}' into active stream for session '{}'",
					event.getAction().getClass().getSimpleName(), event.getSessionId());
			sink.tryEmitNext(new ActionMessage(event.getAction()));
		} else {
			log.warn("No active reactive sink found for session '{}'. Skipping custom action routing.", event.getSessionId());
		}
	}

	/**
	 * Pipes a completed {@link TarkObservabilityEvent} into the matching session's active
	 * stream so live telemetry reaches the WebSocket client.
	 */
	@EventListener
	public void handleObservabilityEvent(TarkObservabilityEvent event) {
		Sinks.Many<ServerMessage> sink = activeSinks.get(event.getSessionId());
		if (sink != null) {
			log.info("Piping external TarkObservabilityEvent into active stream for session '{}'", event.getSessionId());
			sink.tryEmitNext(event.getMessage());
		} else {
			log.warn("No active reactive sink found for session '{}'. Skipping observability stats routing.", event.getSessionId());
		}
	}
}
