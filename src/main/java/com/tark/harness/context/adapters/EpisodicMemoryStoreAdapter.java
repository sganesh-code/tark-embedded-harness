package com.tark.harness.context.adapters;

import com.tark.harness.context.domain.EpisodicMemoryCompactor;
import com.tark.harness.context.ports.EpisodicMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Persists and retrieves a session's conversation history, compacting older turns into an
 * episodic summary once the history grows too large.
 */
public class EpisodicMemoryStoreAdapter implements ChatMemory, EpisodicMemoryStore {

	private static final Logger log = LoggerFactory.getLogger(EpisodicMemoryStoreAdapter.class);

	private final ChatMemory delegate;
	private final EpisodicMemoryCompactor compactor;

	public EpisodicMemoryStoreAdapter(ChatMemoryRepository chatMemoryRepository, EpisodicMemoryCompactor compactor) {
		this.delegate = MessageWindowChatMemory.builder()
				.chatMemoryRepository(chatMemoryRepository)
				.maxMessages(100) // Set high - compaction is managed by EpisodicMemoryCompactor instead
				.build();
		this.compactor = compactor;
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		delegate.add(conversationId, messages);

		List<Message> fullHistory = delegate.get(conversationId);
		if (!compactor.exceedsTurnLimit(fullHistory)) {
			return;
		}

		log.warn("Conversation '{}' history ({} messages) exceeds the turn limit. Running episodic compaction...",
				conversationId, fullHistory.size());
		persistCompacted(conversationId, fullHistory);
	}

	@Override
	public void compact(String conversationId) {
		List<Message> fullHistory = delegate.get(conversationId);
		if (!compactor.canCompactNow(fullHistory)) {
			log.info("Conversation history size ({}) is too small to compact on-demand.", fullHistory.size());
			return;
		}

		log.info("Force-running proactive episodic memory compaction for session '{}' (current size: {} messages)...",
				conversationId, fullHistory.size());
		persistCompacted(conversationId, fullHistory);
	}

	@Override
	public List<Message> history(String conversationId) {
		return List.copyOf(delegate.get(conversationId));
	}

	private void persistCompacted(String conversationId, List<Message> fullHistory) {
		List<Message> compacted = compactor.compact(fullHistory);
		delegate.clear(conversationId);
		delegate.add(conversationId, compacted);
		log.info("Episodic compaction completed for session '{}'.", conversationId);
	}

	@Override
	public List<Message> get(String conversationId) {
		return delegate.get(conversationId);
	}

	@Override
	public void clear(String conversationId) {
		delegate.clear(conversationId);
	}
}
