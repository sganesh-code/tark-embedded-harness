package com.tark.harness.engine.domain;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.context.domain.TextCompletionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Owns the Task Planning Phase's prompt/response round-trip against its own
 * {@link TextCompletionModel} capability: renders prompts, invokes the model, and parses the
 * response into checklist steps.
 */
public class TaskPlannerPrompt {

	private static final Logger log = LoggerFactory.getLogger(TaskPlannerPrompt.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final TextCompletionModel completionModel;

	public TaskPlannerPrompt(TextCompletionModel completionModel) {
		this.completionModel = completionModel;
	}

	/** Generates a checklist plan for the given goal contract. */
	public List<String> generatePlan(GoalContract contract) {
		return parsePlan(completionModel.complete(systemInstructions(), userPrompt(contract)));
	}

	/** Regenerates a checklist plan that resolves the given verifier critique. */
	public List<String> refinePlan(GoalContract contract, String critique) {
		String refinementUserPrompt = String.format("""
				The original proposed plan has been critiqued by the Plan Verifier.

				Critique Flaws: %s

				Goal Contract:
				* Goal: %s
				* Deliverable: %s

				Please regenerate a refined checklist plan that fully resolves all the critique flaws identified.
				""", critique, contract.goal(), contract.deliverable());

		return parsePlan(completionModel.complete(systemInstructions(), refinementUserPrompt));
	}

	private String systemInstructions() {
		return """
				You are a systematic Task Planner. Your job is to decompose the established Goal and Deliverables into a sequential checklist of execution steps.

				You must break down the execution flow into 3 to 7 concrete, checkable, and logical steps. Each step must be clear and action-oriented.

				You MUST return your response as a single, valid JSON array of strings:
				[
				  "1. Step one description",
				  "2. Step two description",
				  "3. Step three description"
				]

				Your response MUST be valid JSON only. Do not output any XML tags, markdown blocks (like ```json), preambles, introductory or concluding text.
				""";
	}

	private String userPrompt(GoalContract contract) {
		String constraintsStr = contract.constraints().isEmpty() ? "(None)" :
				contract.constraints().stream().map(c -> "- " + c).collect(Collectors.joining("\n"));

		return String.format("""
				Goal: %s
				Deliverable: %s
				Constraints:
				%s

				Please generate the sequential checklist plan now.
				""", contract.goal(), contract.deliverable(), constraintsStr);
	}

	/**
	 * Parses the planner response into a List of steps.
	 * Includes a robust fallback line-by-line parser if JSON parsing fails.
	 */
	private List<String> parsePlan(String rawOutput) {
		String trimmed = rawOutput.trim();

		if (trimmed.startsWith("```json")) {
			trimmed = trimmed.substring(7, trimmed.length() - 3).trim();
		} else if (trimmed.startsWith("```")) {
			trimmed = trimmed.substring(3, trimmed.length() - 3).trim();
		}

		try {
			List<String> steps = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
			return steps.stream()
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.collect(Collectors.toList());
		} catch (Exception e) {
			log.warn("Failed to parse task plan as JSON. Falling back to line-by-line parsing...", e);
			return parseFallback(rawOutput);
		}
	}

	private List<String> parseFallback(String text) {
		String[] lines = text.split("\n");
		List<String> steps = new ArrayList<>();

		for (String line : lines) {
			String trimmedLine = line.trim();
			if (trimmedLine.isEmpty()) {
				continue;
			}

			if (trimmedLine.startsWith("-") || trimmedLine.startsWith("*")) {
				trimmedLine = trimmedLine.substring(1).trim();
			}

			if (!trimmedLine.isEmpty()) {
				steps.add(trimmedLine);
			}
		}

		return steps;
	}
}
