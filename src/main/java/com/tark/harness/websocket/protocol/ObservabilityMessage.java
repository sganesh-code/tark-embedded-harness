package com.tark.harness.websocket.protocol;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * Outgoing real-time LLM execution telemetry metrics and tracing message (type field: "observability").
 */
@JsonTypeName("observability")
public record ObservabilityMessage(
		String sessionId,
		String provider,
		String modelName,
		Long durationMs,
		Integer promptTokens,
		Integer completionTokens,
		Integer totalTokens,
		String promptContent,
		String completionContent,
		boolean success
) implements ServerMessage {}
