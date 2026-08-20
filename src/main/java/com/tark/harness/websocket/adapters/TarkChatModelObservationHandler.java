package com.tark.harness.websocket.adapters;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.context.ApplicationEventPublisher;
import com.tark.harness.websocket.TarkObservabilityEvent;
import com.tark.harness.websocket.domain.ConversationIdResolver;
import com.tark.harness.websocket.domain.ObservabilityMessageFactory;
import com.tark.harness.websocket.domain.ObservabilityMessageFactory.RawObservation;
import com.tark.harness.websocket.protocol.ObservabilityConfig;
import com.tark.harness.websocket.protocol.ObservabilityMessage;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Turns each ChatModel call into a live observability update: captures latency, token usage,
 * model info, and (if enabled) prompt/completion content, then publishes it for the WebSocket
 * layer to broadcast to the session that made the call.
 */
public class TarkChatModelObservationHandler implements ObservationHandler<ChatModelObservationContext> {

	private static final Logger log = LoggerFactory.getLogger(TarkChatModelObservationHandler.class);

	private final ApplicationEventPublisher eventPublisher;
	private final java.util.concurrent.atomic.AtomicReference<ObservabilityConfig> configRef = new java.util.concurrent.atomic.AtomicReference<>();
	private final ConversationIdResolver conversationIdResolver = new ConversationIdResolver();
	private final ObservabilityMessageFactory messageFactory = new ObservabilityMessageFactory();

	public TarkChatModelObservationHandler(ApplicationEventPublisher eventPublisher, ObservabilityConfig config) {
		this.eventPublisher = eventPublisher;
		this.configRef.set(config);
	}

	@Override
	public void onStart(ChatModelObservationContext context) {
		context.put("startTime", System.currentTimeMillis());
	}

	@Override
	public void onStop(ChatModelObservationContext context) {
		ObservabilityConfig config = configRef.get();
		if (config != null && !config.enabled()) {
			return;
		}

		String sessionId = conversationIdResolver.resolve(context);
		if (sessionId == null) {
			log.debug("Observation missing session/conversation ID in the active context chain. Skipping WebSocket broadcast.");
			return;
		}

		long startTime = context.getOrDefault("startTime", System.currentTimeMillis());
		long durationMs = System.currentTimeMillis() - startTime;

		String provider = context.getOperationMetadata() != null ? context.getOperationMetadata().provider() : "unknown";
		boolean success = context.getError() == null;

		String modelName = "unknown";
		int promptTokens = 0;
		int completionTokens = 0;
		int totalTokens = 0;
		String promptContent = null;
		String completionContent = null;

		ChatResponse response = context.getResponse();

		if (response != null) {
			if (response.getMetadata() != null) {
				modelName = response.getMetadata().getModel();
				Usage usage = response.getMetadata().getUsage();
				if (usage != null) {
					promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
					completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
					totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
				}
			}

			if (config != null && config.isCategoryEnabled("content") && response.getResult() != null && response.getResult().getOutput() != null) {
				completionContent = response.getResult().getOutput().getText();
			}
		}

		if (config != null && config.isCategoryEnabled("content") && context.getRequest() != null && context.getRequest().getInstructions() != null) {
			promptContent = context.getRequest().getInstructions().stream()
					.map(Message::getText)
					.filter(Objects::nonNull)
					.collect(Collectors.joining("\n"));
		}

		RawObservation raw = new RawObservation(
				sessionId, provider, modelName, durationMs,
				promptTokens, completionTokens, totalTokens,
				promptContent, completionContent, success);

		ObservabilityConfig effectiveConfig = config != null ? config : new ObservabilityConfig(true, java.util.Set.of());
		ObservabilityMessage obsMessage = messageFactory.create(raw, effectiveConfig);

		log.info("Broadcasting live execution observability metrics for session '{}'...", sessionId);
		eventPublisher.publishEvent(new TarkObservabilityEvent(this, obsMessage));
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ChatModelObservationContext;
	}

	public String getName() {
		return "TarkChatModelObservationHandler";
	}
}
