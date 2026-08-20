package com.tark.harness.context.domain;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpisodicMemoryCompactorTest {

	private static List<Message> userMessages(int count) {
		List<Message> messages = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			messages.add(new UserMessage("message " + i));
		}
		return messages;
	}

	@Test
	void exceedsTurnLimitIsFalseAtOrBelowLimit() {
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				new EpisodicSummarizer(FakeTextCompletionModel.returning("s")), 20, 10);

		assertFalse(compactor.exceedsTurnLimit(userMessages(20)));
		assertTrue(compactor.exceedsTurnLimit(userMessages(21)));
	}

	@Test
	void canCompactNowRequiresMoreThanBatchSize() {
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				new EpisodicSummarizer(FakeTextCompletionModel.returning("s")), 20, 10);

		assertFalse(compactor.canCompactNow(userMessages(10)));
		assertTrue(compactor.canCompactNow(userMessages(11)));
	}

	@Test
	void compactReplacesOldestBatchWithASingleSummaryMessage() {
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				new EpisodicSummarizer(FakeTextCompletionModel.returning("dense summary")), 20, 5);

		List<Message> history = userMessages(8); // batch of 5 summarized, 3 preserved
		List<Message> compacted = compactor.compact(history);

		assertEquals(4, compacted.size()); // 1 summary + 3 preserved
		assertTrue(compacted.get(0) instanceof SystemMessage);
		assertTrue(compacted.get(0).getText().contains("dense summary"));
		assertTrue(compacted.get(0).getText().contains(EpisodicMemoryCompactor.SUMMARY_MARKER));
		assertEquals("message 5", compacted.get(1).getText());
	}

	@Test
	void compactFoldsForwardAnExistingSummaryInsteadOfStacking() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("merged summary");
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(new EpisodicSummarizer(model), 20, 3);

		String priorSummaryBlock = String.format("""

				=========================================
				%s
				earlier notes
				=========================================
				""", EpisodicMemoryCompactor.SUMMARY_MARKER);

		List<Message> history = new ArrayList<>();
		history.add(new SystemMessage(priorSummaryBlock));
		history.addAll(userMessages(5));

		List<Message> compacted = compactor.compact(history);

		// Exactly one summary block should ever exist - never two stacked.
		long summaryBlocks = compacted.stream().filter(m -> m.getText() != null && m.getText().contains(EpisodicMemoryCompactor.SUMMARY_MARKER)).count();
		assertEquals(1, summaryBlocks);
		assertTrue(model.lastUserPrompt().contains("earlier notes"));
	}

	@Test
	void compactClampsBatchSizeWhenHistorySmallerThanConfiguredBatch() {
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				new EpisodicSummarizer(FakeTextCompletionModel.returning("summary")), 20, 10);

		List<Message> history = userMessages(3); // smaller than batch size of 10
		List<Message> compacted = compactor.compact(history);

		assertEquals(1, compacted.size()); // everything folded into the single summary message
		assertTrue(compacted.get(0) instanceof SystemMessage);
	}
}
