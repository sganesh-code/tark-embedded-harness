package com.tark.harness.context.ports;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Reads and force-compacts a session's conversation history.
 */
public interface EpisodicMemoryStore {

	/** Returns an immutable snapshot of the current history for the given session. */
	List<Message> history(String conversationId);

	/** Forces episodic compaction of the given session's history, if there's enough to compact. */
	void compact(String conversationId);
}
