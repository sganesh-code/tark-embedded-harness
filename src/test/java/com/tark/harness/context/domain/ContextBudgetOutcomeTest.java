package com.tark.harness.context.domain;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetOutcomeTest {

	@Test
	void wasMutatedIsFalseWhenNothingChanged() {
		var outcome = new ContextBudgetOutcome(List.of(new UserMessage("hi")), 0, false);

		assertFalse(outcome.wasMutated());
	}

	@Test
	void wasMutatedIsTrueWhenToolOutputsWereDistilled() {
		var outcome = new ContextBudgetOutcome(List.of(new UserMessage("hi")), 2, false);

		assertTrue(outcome.wasMutated());
	}

	@Test
	void wasMutatedIsTrueWhenMemoryWasCompacted() {
		var outcome = new ContextBudgetOutcome(List.of(new UserMessage("hi")), 0, true);

		assertTrue(outcome.wasMutated());
	}
}
