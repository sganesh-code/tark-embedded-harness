package com.tark.harness.engine.domain;

import com.tark.harness.context.domain.FakeTextCompletionModel;
import com.tark.harness.context.domain.GoalContract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPlannerPromptTest {

	private static final GoalContract CONTRACT = new GoalContract("goal", "deliverable", List.of("must be fast"), List.of(), List.of());

	@Test
	void generatePlanSendsGoalDeliverableAndConstraintsAndParsesAJsonArray() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("[\"1. step one\", \"2. step two\"]");
		TaskPlannerPrompt prompt = new TaskPlannerPrompt(model);

		List<String> steps = prompt.generatePlan(CONTRACT);

		assertEquals(List.of("1. step one", "2. step two"), steps);
		assertTrue(model.lastUserPrompt().contains("goal"));
		assertTrue(model.lastUserPrompt().contains("deliverable"));
		assertTrue(model.lastUserPrompt().contains("must be fast"));
	}

	@Test
	void generatePlanRendersNoneWhenConstraintsAreEmpty() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("[\"1. step one\"]");
		TaskPlannerPrompt prompt = new TaskPlannerPrompt(model);

		prompt.generatePlan(GoalContract.empty());

		assertTrue(model.lastUserPrompt().contains("(None)"));
	}

	@Test
	void generatePlanStripsMarkdownCodeBlockBeforeParsingJson() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("```json\n[\"1. step one\"]\n```");
		TaskPlannerPrompt prompt = new TaskPlannerPrompt(model);

		List<String> steps = prompt.generatePlan(CONTRACT);

		assertEquals(List.of("1. step one"), steps);
	}

	@Test
	void generatePlanFallsBackToLineByLineParsingWhenNotValidJson() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("- step one\n* step two\nstep three\n");
		TaskPlannerPrompt prompt = new TaskPlannerPrompt(model);

		List<String> steps = prompt.generatePlan(CONTRACT);

		assertEquals(List.of("step one", "step two", "step three"), steps);
	}

	@Test
	void generatePlanFallbackParsingSkipsBlankLines() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("step one\n\n\nstep two\n");
		TaskPlannerPrompt prompt = new TaskPlannerPrompt(model);

		List<String> steps = prompt.generatePlan(CONTRACT);

		assertEquals(List.of("step one", "step two"), steps);
	}

	@Test
	void refinePlanIncludesTheCritiqueAndGoalContractInThePrompt() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("[\"1. refined step\"]");
		TaskPlannerPrompt prompt = new TaskPlannerPrompt(model);

		List<String> steps = prompt.refinePlan(CONTRACT, "misses a constraint");

		assertEquals(List.of("1. refined step"), steps);
		assertTrue(model.lastUserPrompt().contains("misses a constraint"));
		assertTrue(model.lastUserPrompt().contains("goal"));
		assertTrue(model.lastUserPrompt().contains("deliverable"));
	}
}
