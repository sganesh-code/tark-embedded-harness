package com.tark.harness.context.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanStatusRendererTest {

	private final PlanStatusRenderer renderer = new PlanStatusRenderer();

	@Test
	void fewCompletedStepsAreAllRenderedIndividually() {
		List<String> plan = List.of("step1", "step2", "step3", "step4");
		AgentState state = new AgentState(plan, 2, List.of("step1", "step2"), false, "processing"); // 2 completed, <= MAX(3)

		String text = renderer.render(state);

		assertTrue(text.contains("[COMPLETED] Step 1: step1"));
		assertTrue(text.contains("[COMPLETED] Step 2: step2"));
		assertTrue(text.contains("--> [ACTIVE]  Step 3: step3"));
		assertTrue(text.contains("[PENDING]   Step 4: step4"));
		assertFalse(text.contains("earlier steps completed"));
	}

	@Test
	void manyCompletedStepsAreCollapsedBeyondTheRecentWindow() {
		List<String> plan = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			plan.add("step" + i);
		}
		AgentState state = new AgentState(plan, 8, plan.subList(0, 8), false, "processing"); // 8 completed, > MAX(3)

		String text = renderer.render(state);

		assertTrue(text.contains("[5 earlier steps completed]"));
		assertFalse(text.contains("Step 1:"));
		assertTrue(text.contains("[COMPLETED] Step 6: step5"));
		assertTrue(text.contains("[COMPLETED] Step 8: step7"));
		assertTrue(text.contains("--> [ACTIVE]  Step 9: step8"));
	}

	@Test
	void noCompletedStepsRendersOnlyActiveAndPending() {
		List<String> plan = List.of("step1", "step2");
		AgentState state = new AgentState(plan, 0, List.of(), false, "processing");

		String text = renderer.render(state);

		assertFalse(text.contains("earlier steps completed"));
		assertFalse(text.contains("COMPLETED"));
		assertTrue(text.contains("--> [ACTIVE]  Step 1: step1"));
		assertTrue(text.contains("[PENDING]   Step 2: step2"));
	}
}
