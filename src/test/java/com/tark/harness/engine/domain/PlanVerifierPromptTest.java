package com.tark.harness.engine.domain;

import com.tark.harness.context.domain.FakeTextCompletionModel;
import com.tark.harness.context.domain.GoalContract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanVerifierPromptTest {

	private static final GoalContract CONTRACT = new GoalContract("goal", "deliverable", List.of("must be fast"), List.of(), List.of());

	@Test
	void verifySendsGoalConstraintsAndPlanStepsAndParsesAValidVerification() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("{\"valid\": true, \"reason\": \"looks solid\"}");
		PlanVerifierPrompt prompt = new PlanVerifierPrompt(model);

		var result = prompt.verify(CONTRACT, List.of("1. step one", "2. step two"));

		assertTrue(result.valid());
		assertEquals("looks solid", result.reason());
		assertTrue(model.lastUserPrompt().contains("goal"));
		assertTrue(model.lastUserPrompt().contains("must be fast"));
		assertTrue(model.lastUserPrompt().contains("1. step one"));
		assertTrue(model.lastUserPrompt().contains("2. step two"));
	}

	@Test
	void verifyParsesAnInvalidJsonVerification() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("{\"valid\": false, \"reason\": \"misses a constraint\"}");
		PlanVerifierPrompt prompt = new PlanVerifierPrompt(model);

		var result = prompt.verify(CONTRACT, List.of("1. step"));

		assertFalse(result.valid());
		assertEquals("misses a constraint", result.reason());
	}

	@Test
	void verifyStripsMarkdownCodeBlockBeforeParsingJson() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("```json\n{\"valid\": true, \"reason\": \"ok\"}\n```");
		PlanVerifierPrompt prompt = new PlanVerifierPrompt(model);

		var result = prompt.verify(CONTRACT, List.of("1. step"));

		assertTrue(result.valid());
	}

	@Test
	void verifyFallsBackToHeuristicWhenNotValidJsonAndDetectsValidTrue() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("some malformed output with \"valid\": true in it");
		PlanVerifierPrompt prompt = new PlanVerifierPrompt(model);

		var result = prompt.verify(CONTRACT, List.of("1. step"));

		assertTrue(result.valid());
	}

	@Test
	void verifyFallsBackToHeuristicAndDefaultsToInvalidWhenNoValidTrueMarkerPresent() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("completely malformed, no valid marker at all");
		PlanVerifierPrompt prompt = new PlanVerifierPrompt(model);

		var result = prompt.verify(CONTRACT, List.of("1. step"));

		assertFalse(result.valid());
		assertEquals("completely malformed, no valid marker at all", result.reason());
	}
}
