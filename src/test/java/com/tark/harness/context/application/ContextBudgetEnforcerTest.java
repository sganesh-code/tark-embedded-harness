package com.tark.harness.context.application;

import com.tark.harness.context.domain.ContextBudgetOutcome;
import com.tark.harness.context.domain.ContextDistiller;
import com.tark.harness.context.domain.FakeTextCompletionModel;
import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.context.domain.TokenEstimator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetEnforcerTest {

	private static final GoalContract CONTRACT = GoalContract.empty();

	private static ContextBudgetEnforcer enforcer(
			ContextDistiller distiller, FakeEpisodicMemoryStore store, int contextWindowSize, double pressureThresholdPercent) {
		// ratio 1.0 -> estimateTokens == char count, so budgets in tests are exact and deterministic
		return new ContextBudgetEnforcer(distiller, new TokenEstimator(1.0), store, contextWindowSize, pressureThresholdPercent);
	}

	@Test
	void underBudgetReturnsOriginalMessagesUnchanged() {
		ContextDistiller distiller = new ContextDistiller(FakeTextCompletionModel.returning(""), 10);
		FakeEpisodicMemoryStore store = new FakeEpisodicMemoryStore(List.of());
		ContextBudgetEnforcer enforcer = enforcer(distiller, store, 1000, 0.75);

		List<Message> messages = List.of(new SystemMessage("sys"), new UserMessage("hi"));
		ContextBudgetOutcome outcome = enforcer.enforce(CONTRACT, messages, List.of(), "session-1");

		assertSame(messages, outcome.messages());
		assertFalse(outcome.wasMutated());
		assertEquals(0, store.compactCallCount());
	}

	@Test
	void distillationAloneResolvesPressureWithoutCompaction() {
		// Threshold = 100 chars. Raw tool output of 200 chars pushes us over; the fake model
		// distills it down to 10 chars, which brings the whole request back under budget.
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("short");
		ContextDistiller distiller = new ContextDistiller(model, 10);
		FakeEpisodicMemoryStore store = new FakeEpisodicMemoryStore(List.of());
		ContextBudgetEnforcer enforcer = enforcer(distiller, store, 100, 1.0);

		ToolResponseMessage bigToolOutput = ToolResponseMessage.builder()
				.responses(List.of(new ToolResponse("1", "search", "x".repeat(200))))
				.build();
		List<Message> messages = List.of(new SystemMessage("sys"), bigToolOutput);

		ContextBudgetOutcome outcome = enforcer.enforce(CONTRACT, messages, List.of(), "session-1");

		assertTrue(outcome.wasMutated());
		assertEquals(1, outcome.toolOutputsDistilled());
		assertFalse(outcome.episodicMemoryCompacted());
		assertEquals(0, store.compactCallCount());
	}

	@Test
	void fallsBackToCompactionWhenDistillationIsNotEnough() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("");
		ContextDistiller distiller = new ContextDistiller(model, Integer.MAX_VALUE); // never distills

		Message compactedReplacement = new UserMessage("COMPACTED");
		FakeEpisodicMemoryStore store = new FakeEpisodicMemoryStore(List.of(compactedReplacement));

		Message historyMsg1 = new UserMessage("H".repeat(50));
		Message historyMsg2 = new AssistantMessage("H".repeat(50));
		store.seedHistory("session-1", List.of(historyMsg1, historyMsg2));

		ContextBudgetEnforcer enforcer = enforcer(distiller, store, 100, 1.0); // threshold = 100 chars

		Message systemMsg = new SystemMessage("SYS");
		Message newUserMsg = new UserMessage("NEW".repeat(50));
		List<Message> messages = List.of(systemMsg, historyMsg1, historyMsg2, newUserMsg); // 3+50+50+150 = 253 chars

		ContextBudgetOutcome outcome = enforcer.enforce(CONTRACT, messages, List.of(), "session-1");

		assertTrue(outcome.episodicMemoryCompacted());
		assertEquals(1, store.compactCallCount());
		// [system] + [compacted history (1 msg)] + [trailing new message] spliced into this turn
		assertEquals(List.of(systemMsg, compactedReplacement, newUserMsg), outcome.messages());
	}

	@Test
	void skipsSpliceAndOnlyCompactsStoreWhenMoreThanOneSystemMessagePresent() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("");
		ContextDistiller distiller = new ContextDistiller(model, Integer.MAX_VALUE);

		FakeEpisodicMemoryStore store = new FakeEpisodicMemoryStore(List.of(new UserMessage("COMPACTED")));
		store.seedHistory("session-1", List.of(new UserMessage("h")));

		ContextBudgetEnforcer enforcer = enforcer(distiller, store, 10, 1.0); // threshold = 10 chars, trivially exceeded

		List<Message> messages = List.of(
				new SystemMessage("sys one"),
				new SystemMessage("sys two"), // invariant violated: 2 system messages
				new UserMessage("x".repeat(50)));

		ContextBudgetOutcome outcome = enforcer.enforce(CONTRACT, messages, List.of(), "session-1");

		assertSame(messages, outcome.messages(), "should return original messages unchanged when the invariant is broken");
		assertTrue(outcome.episodicMemoryCompacted(), "store should still be compacted for the next turn's benefit");
		assertEquals(1, store.compactCallCount());
	}

	@Test
	void skipsSpliceWhenStoreHistoryIsLargerThanNonSystemInstructions() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("");
		ContextDistiller distiller = new ContextDistiller(model, Integer.MAX_VALUE);

		FakeEpisodicMemoryStore store = new FakeEpisodicMemoryStore(List.of(new UserMessage("COMPACTED")));
		// Store reports 3 history messages, but the request below only carries 1 non-system message -
		// invariant violated (stale/mismatched state), so splicing must be skipped.
		store.seedHistory("session-1", List.of(new UserMessage("a"), new UserMessage("b"), new UserMessage("c")));

		ContextBudgetEnforcer enforcer = enforcer(distiller, store, 10, 1.0);

		List<Message> messages = List.of(new SystemMessage("sys"), new UserMessage("x".repeat(50)));

		ContextBudgetOutcome outcome = enforcer.enforce(CONTRACT, messages, List.of(), "session-1");

		assertSame(messages, outcome.messages());
		assertTrue(outcome.episodicMemoryCompacted());
		assertEquals(1, store.compactCallCount());
	}

	@Test
	void nullSessionIdSkipsCompactionEntirely() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("");
		ContextDistiller distiller = new ContextDistiller(model, Integer.MAX_VALUE);
		FakeEpisodicMemoryStore store = new FakeEpisodicMemoryStore(List.of());
		ContextBudgetEnforcer enforcer = enforcer(distiller, store, 10, 1.0);

		List<Message> messages = List.of(new SystemMessage("sys"), new UserMessage("x".repeat(50)));

		ContextBudgetOutcome outcome = enforcer.enforce(CONTRACT, messages, List.of(), null);

		assertSame(messages, outcome.messages());
		assertFalse(outcome.wasMutated());
		assertEquals(0, store.compactCallCount());
	}
}
