package com.tark.harness.context.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalContractTest {

	@Test
	void emptyHasNoConstraintsAssumptionsOrFacts() {
		GoalContract contract = GoalContract.empty();

		assertTrue(contract.constraints().isEmpty());
		assertTrue(contract.assumptions().isEmpty());
		assertTrue(contract.knownFacts().isEmpty());
	}

	@Test
	void fromFreeformPromptUsesTheTextAsTheGoalWithNoConstraints() {
		GoalContract contract = GoalContract.fromFreeformPrompt("Explain the chain rule");

		assertEquals("Explain the chain rule", contract.goal());
		assertEquals("Complete and deliver execution results", contract.deliverable());
		assertTrue(contract.constraints().isEmpty());
		assertTrue(contract.assumptions().isEmpty());
		assertTrue(contract.knownFacts().isEmpty());
	}
}
