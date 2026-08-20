package com.tark.harness.context.application;

import com.tark.harness.context.domain.ContextBudgetOutcome;
import com.tark.harness.context.domain.ContextDistiller;
import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.context.domain.TokenEstimator;
import com.tark.harness.context.ports.EpisodicMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a session's conversation within its context window budget: estimates prompt tokens
 * against the configured pressure threshold, and if exceeded, first distills oversized tool
 * outputs in-place, then falls back to compacting older conversation history if that alone
 * isn't enough - restoring headroom without losing the information the agent needs to continue.
 */
public class ContextBudgetEnforcer {

	private static final Logger log = LoggerFactory.getLogger(ContextBudgetEnforcer.class);

	private final ContextDistiller distiller;
	private final TokenEstimator tokenEstimator;
	private final EpisodicMemoryStore memoryStore;
	private final int contextWindowSize;
	private final double pressureThresholdPercent;

	public ContextBudgetEnforcer(
			ContextDistiller distiller,
			TokenEstimator tokenEstimator,
			EpisodicMemoryStore memoryStore,
			int contextWindowSize,
			double pressureThresholdPercent) {
		this.distiller = distiller;
		this.tokenEstimator = tokenEstimator;
		this.memoryStore = memoryStore;
		this.contextWindowSize = contextWindowSize;
		this.pressureThresholdPercent = pressureThresholdPercent;
	}

	/**
	 * Enforces the budget against the given prompt instructions for the given goal contract,
	 * bound tool schemas, and session. Returns the original messages unchanged if under budget.
	 */
	public ContextBudgetOutcome enforce(
			GoalContract contract,
			List<Message> messages,
			List<ToolCallback> toolCallbacks,
			String sessionId) {

		int pressureThreshold = (int) (contextWindowSize * pressureThresholdPercent);
		int estimatedTokens = tokenEstimator.estimateTokens(messages, toolCallbacks);
		log.debug("Current estimated context size: {} tokens. Threshold is {} tokens.", estimatedTokens, pressureThreshold);

		if (estimatedTokens <= pressureThreshold) {
			return new ContextBudgetOutcome(messages, 0, false);
		}

		log.warn("Estimated context ({} tokens) exceeds pressure threshold ({} tokens). Running context budget recovery...",
				estimatedTokens, pressureThreshold);

		DistillationResult distillation = distillToolOutputs(messages, contract);
		int tokensAfterDistillation = tokenEstimator.estimateTokens(distillation.messages(), toolCallbacks);

		if (tokensAfterDistillation <= pressureThreshold) {
			return new ContextBudgetOutcome(distillation.messages(), distillation.itemsDistilled(), false);
		}

		log.warn("Still over budget ({} tokens) after distillation. Forcing episodic compaction...", tokensAfterDistillation);
		SpliceResult splice = compactAndSplice(distillation.messages(), sessionId);

		return new ContextBudgetOutcome(splice.messages(), distillation.itemsDistilled(), splice.compacted());
	}

	private DistillationResult distillToolOutputs(List<Message> originalMessages, GoalContract contract) {
		List<Message> distilledMessages = new ArrayList<>(originalMessages.size());
		int itemsDistilled = 0;

		for (Message msg : originalMessages) {
			if (!(msg instanceof ToolResponseMessage toolMsg)) {
				distilledMessages.add(msg);
				continue;
			}

			List<ToolResponse> originalResponses = toolMsg.getResponses();
			List<ToolResponse> updatedResponses = new ArrayList<>(originalResponses.size());
			boolean toolMsgMutated = false;

			for (ToolResponse response : originalResponses) {
				String responseData = response.responseData();

				if (distiller.shouldDistill(response.name(), "", responseData)) {
					String distilledData = distiller.distill(contract, response.name(), responseData);
					updatedResponses.add(new ToolResponse(response.id(), response.name(), distilledData));
					toolMsgMutated = true;
					itemsDistilled++;
				} else {
					updatedResponses.add(response);
				}
			}

			if (!toolMsgMutated) {
				distilledMessages.add(msg);
				continue;
			}

			distilledMessages.add(ToolResponseMessage.builder()
					.responses(updatedResponses)
					.metadata(toolMsg.getMetadata())
					.build());
		}

		if (itemsDistilled == 0) {
			return new DistillationResult(originalMessages, 0);
		}

		log.info("Successfully distilled {} oversized tool responses in-place.", itemsDistilled);
		return new DistillationResult(distilledMessages, itemsDistilled);
	}

	/**
	 * Compacts the session's stored history and splices the result into the current turn's
	 * messages so this turn benefits immediately, falling back to compacting the store alone
	 * (benefiting only the next turn) if the current request's shape is unexpected.
	 */
	private SpliceResult compactAndSplice(List<Message> messages, String sessionId) {
		if (sessionId == null || memoryStore == null) {
			return new SpliceResult(messages, false);
		}

		List<Message> systemMessages = messages.stream().filter(m -> m instanceof SystemMessage).toList();
		if (systemMessages.size() != 1) {
			log.warn("Expected exactly 1 SystemMessage in request instructions but found {}. " +
					"Skipping in-turn splice; compacting store only for next turn.", systemMessages.size());
			memoryStore.compact(sessionId);
			return new SpliceResult(messages, true);
		}

		int preCompactionHistorySize = memoryStore.history(sessionId).size();
		List<Message> nonSystemMessages = messages.stream().filter(m -> !(m instanceof SystemMessage)).toList();

		if (preCompactionHistorySize > nonSystemMessages.size()) {
			log.warn("Chat memory history ({} messages) is larger than the non-system instructions in this " +
					"request ({} messages). Skipping in-turn splice; compacting store only for next turn.",
					preCompactionHistorySize, nonSystemMessages.size());
			memoryStore.compact(sessionId);
			return new SpliceResult(messages, true);
		}

		List<Message> trailingNewMessages = nonSystemMessages.subList(preCompactionHistorySize, nonSystemMessages.size());

		memoryStore.compact(sessionId);
		List<Message> compactedHistory = memoryStore.history(sessionId);

		List<Message> splicedMessages = new ArrayList<>(systemMessages.size() + compactedHistory.size() + trailingNewMessages.size());
		splicedMessages.addAll(systemMessages);
		splicedMessages.addAll(compactedHistory);
		splicedMessages.addAll(trailingNewMessages);

		return new SpliceResult(splicedMessages, true);
	}

	private record DistillationResult(List<Message> messages, int itemsDistilled) {}

	private record SpliceResult(List<Message> messages, boolean compacted) {}
}
