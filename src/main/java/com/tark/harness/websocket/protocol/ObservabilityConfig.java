package com.tark.harness.websocket.protocol;

import java.util.Set;

/**
 * Configuration for real-time AI observability broadcast over WebSockets.
 */
public record ObservabilityConfig(
		boolean enabled,
		Set<String> broadcastCategories // e.g. "tokens", "duration", "model_info", "content"
) {
	public static ObservabilityConfig defaultOnly() {
		return new ObservabilityConfig(true, Set.of("tokens", "duration", "model_info"));
	}

	public boolean isCategoryEnabled(String category) {
		return enabled && broadcastCategories != null && broadcastCategories.contains(category);
	}
}
