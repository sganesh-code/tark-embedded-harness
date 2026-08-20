package com.tark.harness.context.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextDistillerTest {

	private static final GoalContract CONTRACT = new GoalContract("goal", "deliverable", java.util.List.of(), java.util.List.of(), java.util.List.of());

	@Test
	void shouldDistillIsFalseForNullOrEmptyOutput() {
		ContextDistiller distiller = new ContextDistiller(FakeTextCompletionModel.returning(""), 10);

		assertFalse(distiller.shouldDistill("tool", "", null));
		assertFalse(distiller.shouldDistill("tool", "", ""));
	}

	@Test
	void shouldDistillIsFalseWhenOutputBelowThreshold() {
		ContextDistiller distiller = new ContextDistiller(FakeTextCompletionModel.returning(""), 100);

		assertFalse(distiller.shouldDistill("tool", "", "short output"));
	}

	@Test
	void shouldDistillIsFalseForFileReadArguments() {
		ContextDistiller distiller = new ContextDistiller(FakeTextCompletionModel.returning(""), 5);

		assertFalse(distiller.shouldDistill("shell", "cat /var/log/huge.log", "x".repeat(50)));
		assertFalse(distiller.shouldDistill("shell", "tail -f app.log", "x".repeat(50)));
	}

	@Test
	void shouldDistillIsTrueForLargeNonFileReadOutput() {
		ContextDistiller distiller = new ContextDistiller(FakeTextCompletionModel.returning(""), 5);

		assertTrue(distiller.shouldDistill("search", "grep foo", "x".repeat(50)));
	}

	@Test
	void distillReturnsTrimmedCompletionOnSuccess() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("  distilled content  ");
		ContextDistiller distiller = new ContextDistiller(model, 10);

		String result = distiller.distill(CONTRACT, "search", "raw huge output");

		assertEquals("distilled content", result);
		assertEquals(1, model.callCount());
	}

	@Test
	void distillFallsBackToRawOutputOnFailure() {
		ContextDistiller distiller = new ContextDistiller(
				FakeTextCompletionModel.throwing(new RuntimeException("model unavailable")), 10);

		String result = distiller.distill(CONTRACT, "search", "raw huge output");

		assertEquals("raw huge output", result);
	}
}
