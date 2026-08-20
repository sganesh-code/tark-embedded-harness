package com.tark.harness.context.adapters;

import com.tark.harness.context.domain.EpisodicMemoryCompactor;
import com.tark.harness.context.domain.EpisodicSummarizer;
import com.tark.harness.context.domain.FakeTextCompletionModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EpisodicMemoryStoreAdapterTest {

	private static List<Message> userMessages(int count) {
		List<Message> messages = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			messages.add(new UserMessage("message " + i));
		}
		return messages;
	}

	@Test
	void addDoesNotCompactUntilTurnLimitExceeded() {
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				new EpisodicSummarizer(FakeTextCompletionModel.returning("summary")), 5, 3);
		EpisodicMemoryStoreAdapter adapter = new EpisodicMemoryStoreAdapter(new InMemoryChatMemoryRepository(), compactor);

		adapter.add("s1", userMessages(5));

		assertEquals(5, adapter.get("s1").size());
	}

	@Test
	void addTriggersCompactionOnceTurnLimitExceeded() {
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				new EpisodicSummarizer(FakeTextCompletionModel.returning("summary")), 5, 3);
		EpisodicMemoryStoreAdapter adapter = new EpisodicMemoryStoreAdapter(new InMemoryChatMemoryRepository(), compactor);

		adapter.add("s1", userMessages(6)); // exceeds maxTurnLimit of 5

		List<Message> history = adapter.get("s1");
		assertEquals(4, history.size()); // 1 summary + (6 - batchSize 3) preserved
	}

	@Test
	void compactIsANoOpWhenHistoryTooSmallToFormABatch() {
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				new EpisodicSummarizer(FakeTextCompletionModel.returning("summary")), 20, 5);
		EpisodicMemoryStoreAdapter adapter = new EpisodicMemoryStoreAdapter(new InMemoryChatMemoryRepository(), compactor);

		adapter.add("s1", userMessages(3));
		adapter.compact("s1"); // 3 <= batch size 5, should no-op

		assertEquals(3, adapter.history("s1").size());
	}

	@Test
	void compactForcesCompactionWhenEnoughHistoryExists() {
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				new EpisodicSummarizer(FakeTextCompletionModel.returning("summary")), 20, 3);
		EpisodicMemoryStoreAdapter adapter = new EpisodicMemoryStoreAdapter(new InMemoryChatMemoryRepository(), compactor);

		adapter.add("s1", userMessages(5)); // under turn limit of 20, so add() alone won't compact
		adapter.compact("s1"); // forced

		List<Message> history = adapter.history("s1");
		assertEquals(3, history.size()); // 1 summary + (5 - batchSize 3) preserved
	}

	@Test
	void historyReturnsAnImmutableSnapshot() {
		EpisodicMemoryCompactor compactor = new EpisodicMemoryCompactor(
				new EpisodicSummarizer(FakeTextCompletionModel.returning("summary")), 20, 5);
		EpisodicMemoryStoreAdapter adapter = new EpisodicMemoryStoreAdapter(new InMemoryChatMemoryRepository(), compactor);
		adapter.add("s1", userMessages(2));

		List<Message> snapshot = adapter.history("s1");

		org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
				() -> snapshot.add(new UserMessage("mutation attempt")));
	}
}
