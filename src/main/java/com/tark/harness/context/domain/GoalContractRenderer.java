package com.tark.harness.context.domain;

import java.util.List;

/**
 * Renders a {@link GoalContract} into the structured system-prompt block injected on every turn,
 * hard-capping large constraint/fact lists so they can't unboundedly inflate the system message.
 */
public class GoalContractRenderer {

	private static final int MAX_RENDERED_LIST_CHARS = 2000;

	public String render(GoalContract contract) {
		return String.format("""

				=========================================
				ACTIVE GOAL CONTRACT (DO NOT DEVIATE):
				* Goal: %s
				* Deliverable: %s
				* Constraints: %s
				* Assumptions: %s
				* Known Facts: %s
				=========================================
				""",
				contract.goal(),
				contract.deliverable(),
				renderCapped(contract.constraints()),
				renderCapped(contract.assumptions()),
				renderCapped(contract.knownFacts())
		);
	}

	private static String renderCapped(List<String> items) {
		String joined = String.join(", ", items);
		if (joined.length() <= MAX_RENDERED_LIST_CHARS) {
			return joined;
		}
		return joined.substring(0, MAX_RENDERED_LIST_CHARS) + String.format("... (+%d more)", items.size());
	}
}
