package com.tark.harness.engine.application;

import com.tark.harness.context.domain.GoalContract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreflightPlannerTest {

	private static final GoalContract CONTRACT = new GoalContract("Ship the feature", "Working code", List.of("no breaking changes"), List.of(), List.of());

	@Test
	void approvedPlanIsReturnedAsIsWithoutRefinement() {
		SequencedFakeTextCompletionModel model = new SequencedFakeTextCompletionModel(
				"[\"1. step one\", \"2. step two\"]",
				"{\"valid\": true, \"reason\": \"looks good\"}");
		PreflightPlanner planner = new PreflightPlanner(model);

		PreflightPlanner.PlanningOutcome outcome = planner.plan(CONTRACT);

		assertEquals(List.of("1. step one", "2. step two"), outcome.finalPlan());
		assertFalse(outcome.wasRefined());
		assertTrue(outcome.verification().valid());
		assertEquals(2, model.callCount());
	}

	@Test
	void rejectedPlanTriggersARefinementPassIncludingTheCritique() {
		SequencedFakeTextCompletionModel model = new SequencedFakeTextCompletionModel(
				"[\"1. bad step\"]",
				"{\"valid\": false, \"reason\": \"misses the constraint\"}",
				"[\"1. refined step\", \"2. another refined step\"]");
		PreflightPlanner planner = new PreflightPlanner(model);

		PreflightPlanner.PlanningOutcome outcome = planner.plan(CONTRACT);

		assertEquals(List.of("1. refined step", "2. another refined step"), outcome.finalPlan());
		assertTrue(outcome.wasRefined());
		assertFalse(outcome.verification().valid());
		assertEquals(3, model.callCount());
		assertTrue(model.lastUserPrompt().contains("misses the constraint"));
	}

	/** Hand-written test double returning a different canned response per successive call. */
	private static class SequencedFakeTextCompletionModel implements com.tark.harness.context.domain.TextCompletionModel {
		private final java.util.Queue<String> responses;
		private String lastUserPrompt;
		private int callCount;

		SequencedFakeTextCompletionModel(String... responses) {
			this.responses = new java.util.ArrayDeque<>(List.of(responses));
		}

		@Override
		public String complete(String systemPrompt, String userPrompt) {
			callCount++;
			lastUserPrompt = userPrompt;
			return responses.poll();
		}

		String lastUserPrompt() {
			return lastUserPrompt;
		}

		int callCount() {
			return callCount;
		}
	}
}
