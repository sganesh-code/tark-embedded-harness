package com.tark.harness.engine.adapters;

import com.tark.harness.context.domain.GoalContract;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detailed truncation-branch coverage lives in {@link com.tark.harness.context.domain.GoalContractRendererTest}.
 * This class only verifies the advisor's own wiring: passthrough and system-message splicing.
 */
class GoalContractAdvisorTest {

	private static ChatClientResponse emptyResponse() {
		return new ChatClientResponse(new ChatResponse(List.of()), Map.of());
	}

	@Test
	void nullContractLeavesRequestUnchanged() {
		GoalContractAdvisor advisor = new GoalContractAdvisor(() -> null);
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(List.of(new SystemMessage("sys")))).build();
		FakeCallAdvisorChain chain = new FakeCallAdvisorChain(emptyResponse());

		advisor.adviseCall(request, chain);

		assertEquals(request, chain.capturedRequest());
	}

	@Test
	void renderedGoalBlockIsAppendedToTheSystemMessage() {
		GoalContract contract = new GoalContract("the goal", "the deliverable", List.of(), List.of(), List.of());
		GoalContractAdvisor advisor = new GoalContractAdvisor(() -> contract);
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(List.of(new SystemMessage("sys")))).build();
		FakeCallAdvisorChain chain = new FakeCallAdvisorChain(emptyResponse());

		advisor.adviseCall(request, chain);

		String systemText = chain.capturedRequest().prompt().getInstructions().stream()
				.filter(m -> m instanceof SystemMessage)
				.findFirst()
				.orElseThrow()
				.getText();

		assertTrue(systemText.startsWith("sys"));
		assertTrue(systemText.contains("the goal"));
		assertTrue(systemText.contains("the deliverable"));
	}
}
