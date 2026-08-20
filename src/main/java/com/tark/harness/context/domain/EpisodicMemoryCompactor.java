package com.tark.harness.context.domain;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether and how to compact conversation history into a single episodic summary block.
 */
public class EpisodicMemoryCompactor {

	static final String SUMMARY_MARKER = "PRIOR CONVERSATION EPISODE SUMMARY:";

	private final EpisodicSummarizer summarizer;
	private final int maxTurnLimit;
	private final int compactionBatchSize;

	public EpisodicMemoryCompactor(EpisodicSummarizer summarizer, int maxTurnLimit, int compactionBatchSize) {
		this.summarizer = summarizer;
		this.maxTurnLimit = maxTurnLimit;
		this.compactionBatchSize = compactionBatchSize;
	}

	/** True once the given history has grown enough to warrant proactive compaction. */
	public boolean exceedsTurnLimit(List<Message> fullHistory) {
		return fullHistory.size() > maxTurnLimit;
	}

	/** True once there's enough history to form a compaction batch on demand. */
	public boolean canCompactNow(List<Message> fullHistory) {
		return fullHistory.size() > compactionBatchSize;
	}

	/**
	 * Compacts the oldest batch of the given history into a single episodic summary message,
	 * folding forward any prior summary block so at most one ever exists. Returns the new,
	 * compacted history (oldest batch replaced by a single leading summary SystemMessage).
	 */
	public List<Message> compact(List<Message> fullHistory) {
		List<Message> workingHistory = new ArrayList<>(fullHistory);

		String previousSummary = null;
		if (!workingHistory.isEmpty()
				&& workingHistory.get(0) instanceof SystemMessage systemMessage
				&& systemMessage.getText() != null
				&& systemMessage.getText().contains(SUMMARY_MARKER)) {
			previousSummary = systemMessage.getText();
			workingHistory.remove(0);
		}

		int batchSize = Math.min(compactionBatchSize, workingHistory.size());
		List<Message> messagesToSummarize = new ArrayList<>(workingHistory.subList(0, batchSize));
		List<Message> preservedMessages = new ArrayList<>(workingHistory.subList(batchSize, workingHistory.size()));

		String summaryText = summarizer.summarize(messagesToSummarize, previousSummary);

		String summaryBlock = String.format("""

				=========================================
				%s
				%s
				=========================================
				""", SUMMARY_MARKER, summaryText);

		preservedMessages.add(0, new SystemMessage(summaryBlock));
		return preservedMessages;
	}
}
