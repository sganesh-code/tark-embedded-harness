package com.tark.harness.context.domain;

import java.util.List;

/**
 * Structured Goal Contract defining the target outcome, deliverables,
 * constraints, and assumptions for an autonomous agent run.
 */
public record GoalContract(
		String goal,
		String deliverable,
		List<String> constraints,
		List<String> assumptions,
		List<String> knownFacts
) {
	public static GoalContract empty() {
		return new GoalContract("Accomplish task", "Deliver results", List.of(), List.of(), List.of());
	}

	/** Builds a default contract for an unstructured user prompt with no explicit constraints. */
	public static GoalContract fromFreeformPrompt(String text) {
		return new GoalContract(text, "Complete and deliver execution results", List.of(), List.of(), List.of());
	}
}
