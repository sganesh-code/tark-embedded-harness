package com.tark.harness.context.domain;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Result of enforcing the context token budget against a set of prompt instructions:
 * the (possibly unchanged) message list to send, and what recovery actions were taken.
 */
public record ContextBudgetOutcome(List<Message> messages, int toolOutputsDistilled, boolean episodicMemoryCompacted) {

	public boolean wasMutated() {
		return toolOutputsDistilled > 0 || episodicMemoryCompacted;
	}
}
