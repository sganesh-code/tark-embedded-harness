package com.tark.harness.engine.adapters;

import com.tark.harness.context.domain.ContextBudgetOutcome;
import com.tark.harness.context.application.ContextBudgetEnforcer;
import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.context.domain.TokenEstimator;
import com.tark.harness.websocket.protocol.AgentAction.SystemAction;
import com.tark.harness.websocket.protocol.ServerMessage;
import com.tark.harness.websocket.protocol.ServerMessage.ActionMessage;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.function.Supplier;

/**
 * Spring AI Advisor adapter that enforces the context token budget for every outbound request.
 * Translates the request/response into calls on {@link ContextBudgetEnforcer} (which holds the
 * actual distill-then-compact recovery logic) and feeds real usage metadata back into the
 * {@link TokenEstimator} after every call to keep its calibration accurate.
 */
public class ContextBudgetAdvisor implements CallAdvisor, StreamAdvisor {

	private final ContextBudgetEnforcer enforcer;
	private final TokenEstimator tokenEstimator;
	private final Supplier<GoalContract> contractSupplier;
	private final Sinks.Many<ServerMessage> sink;

	public ContextBudgetAdvisor(
			ContextBudgetEnforcer enforcer,
			TokenEstimator tokenEstimator,
			Supplier<GoalContract> contractSupplier,
			Sinks.Many<ServerMessage> sink) {
		this.enforcer = enforcer;
		this.tokenEstimator = tokenEstimator;
		this.contractSupplier = contractSupplier;
		this.sink = sink;
	}

	@Override
	public String getName() {
		return "ContextBudgetAdvisor";
	}

	private static List<ToolCallback> extractToolCallbacks(ChatClientRequest request) {
		if (request.prompt().getOptions() instanceof ToolCallingChatOptions toolOptions
				&& toolOptions.getToolCallbacks() != null) {
			return toolOptions.getToolCallbacks();
		}
		return List.of();
	}

	private ChatClientRequest adviseRequest(ChatClientRequest request) {
		GoalContract contract = contractSupplier.get();
		if (contract == null) {
			return request;
		}

		String sessionId = (String) request.context().get(ChatMemory.CONVERSATION_ID);
		List<ToolCallback> toolCallbacks = extractToolCallbacks(request);

		ContextBudgetOutcome outcome = enforcer.enforce(contract, request.prompt().getInstructions(), toolCallbacks, sessionId);
		notifySink(outcome);

		if (!outcome.wasMutated()) {
			return request;
		}

		Prompt updatedPrompt = new Prompt(outcome.messages(), request.prompt().getOptions());
		return request.mutate().prompt(updatedPrompt).build();
	}

	private void notifySink(ContextBudgetOutcome outcome) {
		if (sink == null) {
			return;
		}
		if (outcome.toolOutputsDistilled() > 0) {
			sink.tryEmitNext(new ActionMessage(new SystemAction(String.format(
					"Successfully distilled %d oversized logs to restore memory window.", outcome.toolOutputsDistilled()))));
		}
		if (outcome.episodicMemoryCompacted()) {
			sink.tryEmitNext(new ActionMessage(new SystemAction(
					"Context still over budget after distillation. Compacted episodic memory.")));
		}
	}

	private void recordUsageIfPresent(ChatClientRequest request, ChatClientResponse response) {
		if (response == null || response.chatResponse() == null || response.chatResponse().getMetadata() == null) {
			return;
		}

		Usage usage = response.chatResponse().getMetadata().getUsage();
		if (usage == null || usage.getPromptTokens() == null || usage.getPromptTokens() <= 0) {
			return;
		}

		List<ToolCallback> toolCallbacks = extractToolCallbacks(request);
		int estimatedChars = tokenEstimator.estimateChars(request.prompt().getInstructions(), toolCallbacks);
		tokenEstimator.recordActualUsage(estimatedChars, usage.getPromptTokens());
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		ChatClientRequest budgetedRequest = adviseRequest(request);
		ChatClientResponse response = chain.nextCall(budgetedRequest);
		recordUsageIfPresent(budgetedRequest, response);
		return response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		ChatClientRequest budgetedRequest = adviseRequest(request);
		return chain.nextStream(budgetedRequest)
				.doOnNext(response -> recordUsageIfPresent(budgetedRequest, response));
	}

	@Override
	public int getOrder() {
		return 150;
	}
}
