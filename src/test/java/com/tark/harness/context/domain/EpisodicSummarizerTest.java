package com.tark.harness.context.domain;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpisodicSummarizerTest {

	@Test
	void summarizeReturnsTrimmedCompletionAndOmitsPriorSummarySectionWhenNull() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("  summary text  ");
		EpisodicSummarizer summarizer = new EpisodicSummarizer(model);

		String result = summarizer.summarize(List.of(new UserMessage("hello")), null);

		assertEquals("summary text", result);
		assertTrue(model.lastUserPrompt().contains("hello"));
		assertFalse(model.lastUserPrompt().contains("PRIOR EPISODE SUMMARY"));
	}

	@Test
	void summarizeIncludesPriorSummaryWhenProvided() {
		FakeTextCompletionModel model = FakeTextCompletionModel.returning("merged summary");
		EpisodicSummarizer summarizer = new EpisodicSummarizer(model);

		summarizer.summarize(List.of(new UserMessage("hello")), "earlier episode notes");

		assertTrue(model.lastUserPrompt().contains("earlier episode notes"));
		assertTrue(model.lastUserPrompt().contains("PRIOR EPISODE SUMMARY"));
	}

	@Test
	void summarizeDegradesGracefullyOnFailure() {
		EpisodicSummarizer summarizer = new EpisodicSummarizer(
				FakeTextCompletionModel.throwing(new RuntimeException("model down")));

		String result = summarizer.summarize(List.of(new UserMessage("hello")), null);

		assertTrue(result.startsWith("Failed to generate episodic summary"));
	}
}
