package com.tark.harness.websocket.domain;

import com.tark.harness.websocket.protocol.ObservabilityConfig;
import com.tark.harness.websocket.protocol.ObservabilityMessage;

/**
 * Builds the observability telemetry sent to clients, hiding any field whose category isn't
 * enabled in the active configuration.
 */
public class ObservabilityMessageFactory {

	public record RawObservation(
			String sessionId,
			String provider,
			String modelName,
			long durationMs,
			int promptTokens,
			int completionTokens,
			int totalTokens,
			String promptContent,
			String completionContent,
			boolean success) {}

	public ObservabilityMessage create(RawObservation raw, ObservabilityConfig config) {
		String filteredModel = config.isCategoryEnabled("model_info") ? raw.modelName() : "hidden";
		Long filteredDuration = config.isCategoryEnabled("duration") ? raw.durationMs() : null;
		Integer filteredPromptTokens = config.isCategoryEnabled("tokens") ? raw.promptTokens() : null;
		Integer filteredCompletionTokens = config.isCategoryEnabled("tokens") ? raw.completionTokens() : null;
		Integer filteredTotalTokens = config.isCategoryEnabled("tokens") ? raw.totalTokens() : null;
		String filteredPromptContent = config.isCategoryEnabled("content") ? raw.promptContent() : null;
		String filteredCompletionContent = config.isCategoryEnabled("content") ? raw.completionContent() : null;

		return new ObservabilityMessage(
				raw.sessionId(),
				raw.provider(),
				filteredModel,
				filteredDuration,
				filteredPromptTokens,
				filteredCompletionTokens,
				filteredTotalTokens,
				filteredPromptContent,
				filteredCompletionContent,
				raw.success()
		);
	}
}
