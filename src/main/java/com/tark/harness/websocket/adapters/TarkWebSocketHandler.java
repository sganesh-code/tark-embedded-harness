package com.tark.harness.websocket.adapters;

import tools.jackson.databind.ObjectMapper;
import com.tark.harness.engine.adapters.EmbeddedAgentEngine;
import com.tark.harness.engine.application.CognitiveOrchestrator;
import com.tark.harness.engine.domain.PromptCommandParser;
import com.tark.harness.websocket.protocol.AgentAction.StatusAction;
import com.tark.harness.websocket.protocol.AgentAction.SystemAction;
import com.tark.harness.websocket.protocol.ClientMessage;
import com.tark.harness.websocket.protocol.ServerMessage;
import com.tark.harness.websocket.protocol.ServerMessage.ActionMessage;
import com.tark.harness.websocket.protocol.ServerMessage.StateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The WebSocket endpoint clients connect to: decodes incoming client messages, routes prompts
 * into the agent loop and streams the response back, and handles cancellation and memory-clear
 * requests for the connection's session.
 */
public class TarkWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(TarkWebSocketHandler.class);

	private final CognitiveOrchestrator orchestrator;
	private final EmbeddedAgentEngine engine;
	private final List<ToolCallback> tools;
	private final ObjectMapper objectMapper;
	private final PromptCommandParser promptCommandParser = new PromptCommandParser();

	private final Map<String, Disposable> activeStreams = new ConcurrentHashMap<>();
	private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

	public TarkWebSocketHandler(
			CognitiveOrchestrator orchestrator,
			EmbeddedAgentEngine engine,
			List<ToolCallback> tools,
			ObjectMapper objectMapper) {
		this.orchestrator = orchestrator;
		this.engine = engine;
		this.tools = tools;
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		log.info("WebSocket connection established. Session: {}", session.getId());
		activeSessions.put(session.getId(), session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		log.info("WebSocket connection closed. Session: {}, Status: {}", session.getId(), status);
		activeSessions.remove(session.getId());
		cancelActiveStream(session.getId());
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		String payload = message.getPayload();
		log.debug("Received text payload: {}", payload);

		try {
			ClientMessage clientMsg = objectMapper.readValue(payload, ClientMessage.class);
			handleClientMessage(session, clientMsg);
		} catch (Exception e) {
			log.error("Failed to process WebSocket text message", e);
			sendErrorMessage(session, "Malformed message: " + e.getMessage());
		}
	}

	private void handleClientMessage(WebSocketSession session, ClientMessage clientMsg) {
		String sessionId = session.getId();

		if (clientMsg instanceof ClientMessage.PromptMessage promptMsg) {
			log.info("WebSocket prompt received for session '{}'", sessionId);
			cancelActiveStream(sessionId);

			if (promptCommandParser.isPromptCommand(promptMsg.text())) {
				log.info("Detected local MCP Prompt calibration command: '{}'. Executing synchronously...", promptMsg.text());
				engine.executePromptCommandSynchronously(session, promptMsg.text());
				return;
			}

			Flux<ServerMessage> stream = orchestrator.handlePrompt(sessionId, promptMsg.text(), tools);

			Disposable disposable = stream
					.subscribeOn(Schedulers.boundedElastic())
					.subscribe(
							msg -> sendServerMessage(session, msg),
							err -> {
								log.error("Execution error in session '{}'", sessionId, err);
								activeStreams.remove(sessionId);
							},
							() -> {
								activeStreams.remove(sessionId);
								log.info("Session '{}' stream finished processing.", sessionId);
							}
					);

			activeStreams.put(sessionId, disposable);

		} else if (clientMsg instanceof ClientMessage.CancelMessage) {
			log.info("Cancellation request received for session '{}'", sessionId);
			boolean cancelled = cancelActiveStream(sessionId);
			if (cancelled) {
				sendSystemWarning(session, "Execution cancelled by user.");
			}
		} else if (clientMsg instanceof ClientMessage.CleanupMemoryMessage) {
			log.info("Memory cleanup /clear request received for session '{}'. Resetting stateless context...", sessionId);
			engine.getChatMemory().clear(sessionId);
			sendSystemWarning(session, "Conversation context cleared.");
		} else {
			log.warn("Unsupported client message type received: {}", clientMsg.getClass().getSimpleName());
		}
	}

	private boolean cancelActiveStream(String sessionId) {
		Disposable disposable = activeStreams.remove(sessionId);
		if (disposable != null && !disposable.isDisposed()) {
			disposable.dispose();
			log.info("Successfully cancelled and disposed stream for session '{}'", sessionId);
			return true;
		}
		return false;
	}

	private void sendSystemWarning(WebSocketSession session, String text) {
		sendServerMessage(session, new StateMessage("idle"));
		sendServerMessage(session, new ActionMessage(new StatusAction("idle")));
		sendServerMessage(session, new ActionMessage(new SystemAction(text)));
	}

	private void sendErrorMessage(WebSocketSession session, String error) {
		sendServerMessage(session, new ActionMessage(new SystemAction(error)));
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
}
