package com.tark.harness.engine.adapters;

import com.tark.harness.context.domain.ContextDistiller;
import com.tark.harness.context.domain.FakeTextCompletionModel;
import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.context.domain.TokenEstimator;
import com.tark.harness.context.application.ContextBudgetEnforcer;
import com.tark.harness.context.application.FakeEpisodicMemoryStore;
import com.tark.harness.websocket.protocol.ServerMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetAdvisorTest {

	private static ChatClientRequest request(List<org.springframework.ai.chat.messages.Message> messages, String sessionId) {
		return ChatClientRequest.builder()
				.prompt(new Prompt(messages))
				.context(ChatMemory.CONVERSATION_ID, sessionId)
				.build();
	}

	private static ChatClientResponse responseWithUsage(int promptTokens) {
		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
				.usage(new DefaultUsage(promptTokens, 5))
				.build();
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("reply"))), metadata);
		return new ChatClientResponse(chatResponse, Map.of());
	}

	@Test
	void nullContractSkipsBudgetEnforcementEntirely() {
		ContextDistiller distiller = new ContextDistiller(FakeTextCompletionModel.returning(""), 1);
		TokenEstimator tokenEstimator = new TokenEstimator(1.0);
		FakeEpisodicMemoryStore store = new FakeEpisodicMemoryStore(List.of());
		ContextBudgetEnforcer enforcer = new ContextBudgetEnforcer(distiller, tokenEstimator, store, 1, 1.0); // trivially over budget if ever invoked

		Sinks.Many<ServerMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
		List<ServerMessage> emitted = new ArrayList<>();
		sink.asFlux().subscribe(emitted::add);

		ContextBudgetAdvisor advisor = new ContextBudgetAdvisor(enforcer, tokenEstimator, () -> null, sink);

		ChatClientRequest original = request(List.of(new SystemMessage("sys")), "s1");
		FakeCallAdvisorChain chain = new FakeCallAdvisorChain(responseWithUsage(100));

		advisor.adviseCall(original, chain);

		assertSame(original, chain.capturedRequest(), "request should pass through unchanged when there's no active goal contract");
		assertTrue(emitted.isEmpty());
		assertEquals(0, store.compactCallCount());
	}

	@Test
	void distillationMutatesRequestAndNotifiesSink() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("short");
		ContextDistiller distiller = new ContextDistiller(model, 10);
		TokenEstimator tokenEstimator = new TokenEstimator(1.0);
		FakeEpisodicMemoryStore store = new FakeEpisodicMemoryStore(List.of());
		ContextBudgetEnforcer enforcer = new ContextBudgetEnforcer(distiller, tokenEstimator, store, 100, 1.0);

		Sinks.Many<ServerMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
		List<ServerMessage> emitted = new ArrayList<>();
		sink.asFlux().subscribe(emitted::add);

		ContextBudgetAdvisor advisor = new ContextBudgetAdvisor(enforcer, tokenEstimator, () -> GoalContract.empty(), sink);

		ToolResponseMessage bigToolOutput = ToolResponseMessage.builder()
				.responses(List.of(new ToolResponse("1", "search", "x".repeat(200))))
				.build();
		ChatClientRequest original = request(List.of(new SystemMessage("sys"), bigToolOutput), "s1");
		FakeCallAdvisorChain chain = new FakeCallAdvisorChain(responseWithUsage(0));

		advisor.adviseCall(original, chain);

		ChatClientRequest sentToModel = chain.capturedRequest();
		assertTrue(sentToModel != original, "request should have been mutated with the distilled tool output");
		assertEquals(1, emitted.size());
	}

	@Test
	void recordsUsageFeedbackAfterACallWithRealPromptTokens() {
		ContextDistiller distiller = new ContextDistiller(FakeTextCompletionModel.returning(""), Integer.MAX_VALUE);
		TokenEstimator tokenEstimator = new TokenEstimator(4.0); // start at the naive default ratio
		FakeEpisodicMemoryStore store = new FakeEpisodicMemoryStore(List.of());
		ContextBudgetEnforcer enforcer = new ContextBudgetEnforcer(distiller, tokenEstimator, store, 100000, 1.0); // never triggers recovery

		ContextBudgetAdvisor advisor = new ContextBudgetAdvisor(enforcer, tokenEstimator, GoalContract::empty, null);

		// 400 chars of message text, but the model reports only 100 real prompt tokens used -
		// a true ratio of 4.0 chars/token, matching the seed, so recalibration should leave it put.
		ChatClientRequest original = request(List.of(new SystemMessage("a".repeat(400))), "s1");
		FakeCallAdvisorChain chain = new FakeCallAdvisorChain(responseWithUsage(100));

		advisor.adviseCall(original, chain);

		// Ratio should have converged toward (still near) 4.0: estimate for 800 chars should be ~200 tokens.
		int estimated = tokenEstimator.estimateTokens(List.of(new SystemMessage("a".repeat(800))), List.of());
		assertTrue(estimated > 150 && estimated < 250, "expected ratio to stay close to 4.0 chars/token, estimate was " + estimated);
	}
}
