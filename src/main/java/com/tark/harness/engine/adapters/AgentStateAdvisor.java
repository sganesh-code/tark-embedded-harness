package com.tark.harness.engine.adapters;

import com.tark.harness.context.domain.AgentState;
import com.tark.harness.context.domain.PlanStatusRenderer;
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
 * Custom Spring AI Advisor that dynamically injects active execution plans and
 * step-progress metadata (AgentState) into the LLM system instructions.
 */
public class AgentStateAdvisor implements CallAdvisor, StreamAdvisor {

	private final Supplier<AgentState> stateSupplier;
	private final PlanStatusRenderer renderer = new PlanStatusRenderer();

	public AgentStateAdvisor(Supplier<AgentState> stateSupplier) {
		this.stateSupplier = stateSupplier;
	}

	@Override
	public String getName() {
		return "AgentStateAdvisor";
	}

	private ChatClientRequest adviseRequest(ChatClientRequest request) {
		AgentState state = stateSupplier.get();
		if (state == null || state.plan().isEmpty()) {
			return request;
		}

		String planBlock = renderer.render(state);

		List<Message> messages = new ArrayList<>(request.prompt().getInstructions());
		boolean systemUpdated = false;

		for (int i = 0; i < messages.size(); i++) {
			Message msg = messages.get(i);
			if (msg instanceof SystemMessage systemMessage) {
				String newText = systemMessage.getText() + planBlock;
				messages.set(i, new SystemMessage(newText));
				systemUpdated = true;
				break;
			}
		}

		if (!systemUpdated) {
			messages.add(0, new SystemMessage("You are an autonomous step-execution assistant." + planBlock));
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
		return 110;
	}
}
