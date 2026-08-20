package com.tark.harness.context.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that condenses chat history into a highly structured episodic summary
 * containing overarching goals, steps taken, discoveries, and user preferences.
 */
public class EpisodicSummarizer {

	private static final Logger log = LoggerFactory.getLogger(EpisodicSummarizer.class);
	private final TextCompletionModel completionModel;

	public EpisodicSummarizer(TextCompletionModel completionModel) {
		this.completionModel = completionModel;
	}

	/**
	 * Summarizes a raw list of conversation messages into a compact, high-density episode block,
	 * optionally folding forward a previous episode summary so the result subsumes it rather than
	 * leaving multiple stacked summary blocks behind.
	 *
	 * @param messages The list of older messages to summarize.
	 * @param previousSummary A prior episode summary to merge into the new one, or null if none exists.
	 * @return A structured summary block containing accomplishments and user preference discoveries.
	 */
	public String summarize(List<Message> messages, String previousSummary) {
		log.info("Generating episodic summary for {} history messages...", messages.size());

		String systemPrompt = """
				You are a meticulous session-summarizing agent. Your job is to analyze the history of interactions in an LLM agent harness session and produce a clean, distilled, and highly structured summary.

				Analyze the inputs and outputs, identifying:
				1. What the user's overarching goal was.
				2. What steps were taken, and what final results or solutions were achieved.
				3. Any specific user preferences (e.g., visual styles, tool choices, directories) or constraints discovered.
				4. Major takeaways or findings that should carry forward.

				If a PRIOR EPISODE SUMMARY is provided, merge it with the new messages into a single,
				updated summary. Do not produce two separate summaries or reference "the prior summary" -
				fold its content forward as if it had always been part of one continuous summary.

				CRITICAL RULES:
				1. Be extremely concise. Avoid narrative fluff, introductory sentences, or pleasantries.
				2. Format the response strictly as a clean, markdown-bulleted document with sections: Goals, Steps & Accomplishments, Preferences Discovered, Takeaways.
				3. Do NOT add any markdown blocks (```) or other wrapping delimiters.
				""";

		String conversationHistoryText = messages.stream()
				.map(msg -> String.format("[%s]: %s", msg.getMessageType().name(), msg.getText()))
				.collect(Collectors.joining("\n\n"));

		String priorSummarySection = (previousSummary != null && !previousSummary.isBlank())
				? String.format("PRIOR EPISODE SUMMARY:\n%s\n\n", previousSummary)
				: "";

		String userPrompt = String.format("""
				%sAnalyze the following conversation logs and generate the high-density structured summary:

				%s

				Structured Summary:
				""", priorSummarySection, conversationHistoryText);

		try {
			return completionModel.complete(systemPrompt, userPrompt).trim();
		} catch (Exception e) {
			log.error("Failed to generate episodic summary", e);
			return "Failed to generate episodic summary: " + e.getMessage();
		}
	}
}
