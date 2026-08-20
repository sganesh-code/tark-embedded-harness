package com.tark.harness.context.domain;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenEstimatorTest {

	private static ToolCallback toolCallback(String name, String description, String inputSchema) {
		ToolDefinition definition = ToolDefinition.builder()
				.name(name)
				.description(description)
				.inputSchema(inputSchema)
				.build();
		return new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String toolInput) {
				return "";
			}
		};
	}

	@Test
	void estimateCharsSumsMessageTextAndToolSchemas() {
		TokenEstimator estimator = new TokenEstimator(4.0);
		List<Message> messages = List.of(new UserMessage("1234567890")); // 10 chars
		List<ToolCallback> tools = List.of(toolCallback("abc", "de", "fghi")); // 3+2+4 = 9 chars

		assertEquals(19, estimator.estimateChars(messages, tools));
	}

	@Test
	void estimateCharsCountsToolResponsePayloadsDespiteEmptyGetText() {
		TokenEstimator estimator = new TokenEstimator(4.0);

		// ToolResponseMessage always reports an empty getText() - its real payload lives in
		// the ToolResponse entries, and that must still be counted toward the budget.
		ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
				.responses(List.of(new ToolResponse("1", "search", "x".repeat(100))))
				.build();

		assertEquals("", toolResponseMessage.getText());
		assertEquals(100, estimator.estimateChars(List.of(toolResponseMessage), List.of()));
	}

	@Test
	void estimateTokensDividesByCurrentRatio() {
		TokenEstimator estimator = new TokenEstimator(2.0);
		List<Message> messages = List.of(new UserMessage("12345678")); // 8 chars

		assertEquals(4, estimator.estimateTokens(messages, List.of()));
	}

	@Test
	void nonPositiveInitialRatioFallsBackToDefaultFour() {
		TokenEstimator estimator = new TokenEstimator(0.0);
		List<Message> messages = List.of(new UserMessage("12345678")); // 8 chars

		assertEquals(2, estimator.estimateTokens(messages, List.of()));
	}

	@Test
	void toolCallbackWithNullDefinitionIsSkippedWithoutThrowing() {
		TokenEstimator estimator = new TokenEstimator(4.0);
		ToolCallback nullDefinitionCallback = new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return null;
			}

			@Override
			public String call(String toolInput) {
				return "";
			}
		};

		int chars = estimator.estimateChars(List.of(), List.of(nullDefinitionCallback));

		assertEquals(0, chars);
	}

	@Test
	void recordActualUsageMovesRatioTowardObservedValue() {
		TokenEstimator estimator = new TokenEstimator(4.0);

		// Observed ratio is 2.0 chars/token (400 chars -> 200 tokens); EMA should pull the
		// running ratio down from 4.0 toward 2.0, without jumping all the way there.
		estimator.recordActualUsage(400, 200);

		List<Message> messages = List.of(new UserMessage("a".repeat(400)));
		int estimatedTokens = estimator.estimateTokens(messages, List.of());

		// New ratio = 0.7*4.0 + 0.3*2.0 = 3.4 -> 400/3.4 ~= 118 tokens (down from 100 at ratio 4.0... wait ratio 4 gives 100)
		assertTrue(estimatedTokens > 100 && estimatedTokens < 200,
				"Expected recalibrated estimate to sit between the old and new ratio bounds, was " + estimatedTokens);
	}

	@Test
	void recordActualUsageIgnoresNonPositiveInputs() {
		TokenEstimator estimator = new TokenEstimator(4.0);

		estimator.recordActualUsage(0, 100);
		estimator.recordActualUsage(100, 0);
		estimator.recordActualUsage(-5, 100);

		List<Message> messages = List.of(new UserMessage("12345678")); // 8 chars
		assertEquals(2, estimator.estimateTokens(messages, List.of())); // unchanged ratio of 4.0
	}
}
