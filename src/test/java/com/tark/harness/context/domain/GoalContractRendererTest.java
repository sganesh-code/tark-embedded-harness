package com.tark.harness.context.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalContractRendererTest {

	private final GoalContractRenderer renderer = new GoalContractRenderer();

	@Test
	void rendersGoalAndDeliverable() {
		GoalContract contract = new GoalContract("goal text", "deliverable text", List.of(), List.of(), List.of());

		String text = renderer.render(contract);

		assertTrue(text.contains("goal text"));
		assertTrue(text.contains("deliverable text"));
	}

	@Test
	void shortListsAreRenderedInFull() {
		GoalContract contract = new GoalContract("goal", "deliverable", List.of("c1", "c2"), List.of(), List.of());

		String text = renderer.render(contract);

		assertTrue(text.contains("c1, c2"));
		assertFalse(text.contains("more)"));
	}

	@Test
	void largeConstraintListIsTruncatedWithACount() {
		List<String> manyConstraints = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			manyConstraints.add("constraint-" + i);
		}
		GoalContract contract = new GoalContract("goal", "deliverable", manyConstraints, List.of(), List.of());

		String text = renderer.render(contract);

		assertTrue(text.contains(String.format("(+%d more)", manyConstraints.size())));
	}

	@Test
	void assumptionsAndKnownFactsAreTruncatedIndependentlyOfConstraints() {
		List<String> manyFacts = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			manyFacts.add("fact-" + i);
		}
		GoalContract contract = new GoalContract("goal", "deliverable", List.of("short"), List.of("also short"), manyFacts);

		String text = renderer.render(contract);

		assertTrue(text.contains("short"));
		assertTrue(text.contains("also short"));
		assertTrue(text.contains(String.format("(+%d more)", manyFacts.size())));
	}
}
