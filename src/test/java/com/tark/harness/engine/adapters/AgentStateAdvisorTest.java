package com.tark.harness.engine.adapters;

import com.tark.harness.context.domain.AgentState;
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
 * Detailed step-collapsing branch coverage lives in {@link com.tark.harness.context.domain.PlanStatusRendererTest}.
 * This class only verifies the advisor's own wiring: passthrough and system-message splicing.
 */
class AgentStateAdvisorTest {

	private static ChatClientResponse emptyResponse() {
		return new ChatClientResponse(new ChatResponse(List.of()), Map.of());
	}

	@Test
	void emptyPlanLeavesRequestUnchanged() {
		AgentStateAdvisor advisor = new AgentStateAdvisor(AgentState::empty);
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(List.of(new SystemMessage("sys")))).build();
		FakeCallAdvisorChain chain = new FakeCallAdvisorChain(emptyResponse());

		advisor.adviseCall(request, chain);

		assertEquals(request, chain.capturedRequest());
	}

	@Test
	void renderedPlanBlockIsAppendedToTheSystemMessage() {
		AgentState state = new AgentState(List.of("step1", "step2"), 0, List.of(), false, "processing");
		AgentStateAdvisor advisor = new AgentStateAdvisor(() -> state);
		ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt(List.of(new SystemMessage("sys")))).build();
		FakeCallAdvisorChain chain = new FakeCallAdvisorChain(emptyResponse());

		advisor.adviseCall(request, chain);

		String systemText = chain.capturedRequest().prompt().getInstructions().stream()
				.filter(m -> m instanceof SystemMessage)
				.findFirst()
				.orElseThrow()
				.getText();

		assertTrue(systemText.startsWith("sys"));
		assertTrue(systemText.contains("step1"));
	}
}
