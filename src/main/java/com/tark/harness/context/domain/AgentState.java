package com.tark.harness.context.domain;

import java.util.List;

/**
 * Structured Agent State representing the execution plan, current progress,
 * and live milestones.
 */
public record AgentState(
		List<String> plan,
		int currentStep,
		List<String> completedSteps,
		boolean done,
		String currentStatus
) {
	public static AgentState empty() {
		return new AgentState(List.of(), 0, List.of(), false, "idle");
	}
}
