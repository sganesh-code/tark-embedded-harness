package com.tark.harness.context.domain;

/**
 * Renders an {@link AgentState}'s plan/progress into the structured system-prompt block injected
 * on every turn, collapsing older completed steps into a single line so long-running plans can't
 * unboundedly inflate the system message.
 */
public class PlanStatusRenderer {

	/** Number of most-recent completed steps rendered individually before collapsing older ones. */
	private static final int MAX_RENDERED_COMPLETED_STEPS = 3;

	public String render(AgentState state) {
		StringBuilder planBuilder = new StringBuilder();
		planBuilder.append("\n=========================================\n");
		planBuilder.append("ACTIVE EXECUTION PLAN STATUS:\n");

		int firstRenderedCompletedStep = Math.max(0, state.currentStep() - MAX_RENDERED_COMPLETED_STEPS);
		if (firstRenderedCompletedStep > 0) {
			planBuilder.append(String.format("[%d earlier steps completed]\n", firstRenderedCompletedStep));
		}

		for (int i = firstRenderedCompletedStep; i < state.plan().size(); i++) {
			String step = state.plan().get(i);
			if (i < state.currentStep()) {
				planBuilder.append(String.format("[COMPLETED] Step %d: %s\n", i + 1, step));
			} else if (i == state.currentStep()) {
				planBuilder.append(String.format("--> [ACTIVE]  Step %d: %s\n", i + 1, step));
			} else {
				planBuilder.append(String.format("[PENDING]   Step %d: %s\n", i + 1, step));
			}
		}
		planBuilder.append("=========================================\n");

		return planBuilder.toString();
	}
}
