package com.tark.harness.engine.adapters;

import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.context.domain.GoalContractRenderer;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Custom Spring AI Advisor that dynamically injects active Goal Contracts
 * into the chat client's system context on every conversation turn.
 * Implements both blocking (CallAdvisor) and streaming (StreamAdvisor) modes.
 */
public class GoalContractAdvisor implements CallAdvisor, StreamAdvisor {

	private final Supplier<GoalContract> contractSupplier;
	private final GoalContractRenderer renderer = new GoalContractRenderer();

	public GoalContractAdvisor(Supplier<GoalContract> contractSupplier) {
		this.contractSupplier = contractSupplier;
	}

	@Override
	public String getName() {
		return "GoalContractAdvisor";
	}

	private ChatClientRequest adviseRequest(ChatClientRequest request) {
		GoalContract contract = contractSupplier.get();
		if (contract == null) {
			return request;
		}

		String goalBlock = renderer.render(contract);

		List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
		boolean systemUpdated = false;

		for (int i = 0; i < messages.size(); i++) {
			Message msg = messages.get(i);
			if (msg instanceof SystemMessage systemMessage) {
				String newText = systemMessage.getText() + goalBlock;
				messages.set(i, new SystemMessage(newText));
				systemUpdated = true;
				break;
			}
		}

		if (!systemUpdated) {
			messages.addFirst(new SystemMessage("You are an autonomous task execution assistant." + goalBlock));
		}

		Prompt modifiedPrompt = new Prompt(messages, request.prompt().getOptions());
		return request.mutate()
				.prompt(modifiedPrompt)
				.build();
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		return chain.nextCall(adviseRequest(request));
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
		return chain.nextStream(adviseRequest(request));
	}

	@Override
	public int getOrder() {
		return 100;
	}
}
