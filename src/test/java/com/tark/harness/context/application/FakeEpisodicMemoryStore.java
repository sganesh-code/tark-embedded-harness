package com.tark.harness.context.application;

import com.tark.harness.context.ports.EpisodicMemoryStore;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand-written in-memory test double for {@link EpisodicMemoryStore}. {@link #compact}
 * replaces the stored history with a single canned "compacted" marker message so tests can
 * assert splicing behavior without depending on any real summarization logic.
 */
public class FakeEpisodicMemoryStore implements EpisodicMemoryStore {

	private final Map<String, List<Message>> historyBySession = new ConcurrentHashMap<>();
	private final List<Message> compactedReplacement;
	private int compactCallCount;

	public FakeEpisodicMemoryStore(List<Message> compactedReplacement) {
		this.compactedReplacement = compactedReplacement;
	}

	public void seedHistory(String sessionId, List<Message> messages) {
		historyBySession.put(sessionId, new ArrayList<>(messages));
	}

	@Override
	public List<Message> history(String conversationId) {
		return List.copyOf(historyBySession.getOrDefault(conversationId, List.of()));
	}

	@Override
	public void compact(String conversationId) {
		compactCallCount++;
		historyBySession.put(conversationId, new ArrayList<>(compactedReplacement));
	}

	public int compactCallCount() {
		return compactCallCount;
	}
}
