package com.tark.harness.context.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that distills oversized tool outputs to prevent context window saturation
 * while preserving high-signal details relative to the active GoalContract.
 */
public class ContextDistiller {

	private static final Logger log = LoggerFactory.getLogger(ContextDistiller.class);
	private final TextCompletionModel completionModel;
	private final int distillationThresholdCharacters;

	public ContextDistiller(TextCompletionModel completionModel, int distillationThresholdCharacters) {
		this.completionModel = completionModel;
		this.distillationThresholdCharacters = distillationThresholdCharacters;
	}

	/**
	 * True if this tool output is large enough and not a direct file/code read (which must stay
	 * verbatim) to be worth distilling.
	 */
	public boolean shouldDistill(String toolName, String arguments, String output) {
		if (output == null || output.isEmpty()) {
			return false;
		}

		if (output.length() < distillationThresholdCharacters) {
			return false;
		}

		String lowerArgs = arguments != null ? arguments.toLowerCase() : "";
		boolean isFileRead = lowerArgs.contains("cat ") ||
				lowerArgs.contains("less ") ||
				lowerArgs.contains("more ") ||
				lowerArgs.contains("tail ") ||
				lowerArgs.contains("head ");

		return !isFileRead;
	}

	/**
	 * Runs a secondary, lightweight LLM call to distill raw large output.
	 *
	 * @param contract The active GoalContract to ground relevance evaluation.
	 * @param toolName The name of the tool that generated this output.
	 * @param rawOutput The large raw output string.
	 * @return A content-preserved, redacted distilled representation.
	 */
	public String distill(GoalContract contract, String toolName, String rawOutput) {
		log.info("Distilling raw output for tool '{}' to prevent context pollution...", toolName);

		String systemInstructions = """
				You are an expert Context Distillation Assistant specializing in raw-content preservation.
				The user is running an autonomous agent to achieve an overarching Goal.
				A tool was executed and returned a very large raw output (such as logs, files, outputs, or code).
				Your task is to analyze the raw tool output and isolate the exact contiguous subsections, lines, or blocks that are highly relevant to achieving the Goal.

				CRITICAL RULES:
				1. You MUST preserve the extracted relevant subsections EXACTLY as they appear in the original text. Do NOT modify, paraphrase, edit, summarize, explain, or translate any part of the extracted content.
				2. For all irrelevant sections that you decide to remove, substitute them with a single-line placeholder: "... [REDACTED SECTION of X lines] ..." where X is your estimated count of original lines skipped.
				3. Do NOT introduce any conversational preambles, pleasantries, explanations, introductory/concluding text, or markdown blocks (```). Output ONLY the finalized content-preserved text.
				""";

		String userContextPrompt = String.format("""
				Goal: %s
				Deliverable: %s
				Tool executed: %s

				Raw Tool Output:
				%s

				Content-Preserved Distilled Output:
				""", contract.goal(), contract.deliverable(), toolName, rawOutput);

		try {
			return completionModel.complete(systemInstructions, userContextPrompt).trim();
		} catch (Exception e) {
			log.error("Failed to distill tool output for '{}'. Falling back to original raw output.", toolName, e);
			return rawOutput;
		}
	}
}
