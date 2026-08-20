package com.tark.harness.engine.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.context.domain.TextCompletionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Owns the Plan Verification (Self-Critique) Phase's prompt/response round-trip against its own
 * {@link TextCompletionModel} capability: renders prompts, invokes the model, and parses the
 * response into a {@link VerificationResult}.
 */
public class PlanVerifierPrompt {

	private static final Logger log = LoggerFactory.getLogger(PlanVerifierPrompt.class);
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final TextCompletionModel completionModel;

	public PlanVerifierPrompt(TextCompletionModel completionModel) {
		this.completionModel = completionModel;
	}

	/** Verifies the given plan against the goal contract's constraints. */
	public VerificationResult verify(GoalContract contract, List<String> plan) {
		return parseVerification(completionModel.complete(systemInstructions(), userPrompt(contract, plan)));
	}

	private String systemInstructions() {
		return """
				You are a strict Plan Verifier. Your job is to analyze the proposed checklist plan against the original Goal Contract.

				Validate that:
				1. The plan logically addresses the primary goal.
				2. The plan directly leads to the testable deliverable.
				3. The plan respects and enforces all defined constraints.
				4. The plan steps have a logical sequential flow with no redundant or circular steps.

				You MUST return your response as a single, valid JSON object with the following schema:
				{
				  "valid": true,
				  "reason": "explanation of why it is valid or why it is invalid"
				}

				If the plan is logically complete and sound, set "valid" to true.
				If the plan misses critical constraints or has flaws, set "valid" to false.

				Your response MUST be valid JSON only. Do not output any XML tags, markdown blocks (like ```json), preambles, introductory or concluding text.
				""";
	}

	private String userPrompt(GoalContract contract, List<String> plan) {
		String constraintsStr = contract.constraints().isEmpty() ? "(None)" :
				contract.constraints().stream().map(c -> "- " + c).collect(Collectors.joining("\n"));

		String planStr = plan.stream().map(step -> "  * " + step).collect(Collectors.joining("\n"));

		return String.format("""
				Goal Contract:
				* Goal: %s
				* Deliverable: %s
				* Constraints:
				%s

				Proposed Plan Steps:
				%s

				Please execute the plan validation check now.
				""", contract.goal(), contract.deliverable(), constraintsStr, planStr);
	}

	/**
	 * Verification Result holding the validation status and critique explanations.
	 */
	public record VerificationResult(boolean valid, String reason) {}

	/**
	 * Parses the verifier output into a VerificationResult.
	 */
	private VerificationResult parseVerification(String rawOutput) {
		String trimmed = rawOutput.trim();

		if (trimmed.startsWith("```json")) {
			trimmed = trimmed.substring(7, trimmed.length() - 3).trim();
		} else if (trimmed.startsWith("```")) {
			trimmed = trimmed.substring(3, trimmed.length() - 3).trim();
		}

		try {
			JsonNode jsonNode = objectMapper.readTree(trimmed);
			boolean valid = jsonNode.get("valid").asBoolean();
			String reason = jsonNode.get("reason").textValue();
			return new VerificationResult(valid, reason);
		} catch (Exception e) {
			log.warn("Failed to parse verification results as JSON. Performing fallback heuristic...", e);
			boolean containsTrue = trimmed.toLowerCase().contains("\"valid\": true") ||
					trimmed.toLowerCase().contains("\"valid\":true") ||
					trimmed.toLowerCase().contains("valid: true") ||
					trimmed.toLowerCase().contains("valid:true");
			return new VerificationResult(containsTrue, rawOutput);
		}
	}
}
