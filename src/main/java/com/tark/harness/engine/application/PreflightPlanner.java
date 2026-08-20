package com.tark.harness.engine.application;

import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.context.domain.TextCompletionModel;
import com.tark.harness.engine.domain.PlanVerifierPrompt;
import com.tark.harness.engine.domain.TaskPlannerPrompt;

import java.util.List;

/**
 * Runs the pre-flight planning sequence: generates a checklist plan, runs it through
 * self-critique verification, and refines it once if the critique rejects it.
 */
public class PreflightPlanner {

	public record PlanningOutcome(List<String> finalPlan, PlanVerifierPrompt.VerificationResult verification, boolean wasRefined) {}

	private final TaskPlannerPrompt taskPlannerPrompt;
	private final PlanVerifierPrompt planVerifierPrompt;

	public PreflightPlanner(TextCompletionModel completionModel) {
		this.taskPlannerPrompt = new TaskPlannerPrompt(completionModel);
		this.planVerifierPrompt = new PlanVerifierPrompt(completionModel);
	}

	public PlanningOutcome plan(GoalContract contract) {
		List<String> proposedPlan = taskPlannerPrompt.generatePlan(contract);

		PlanVerifierPrompt.VerificationResult verification = planVerifierPrompt.verify(contract, proposedPlan);

		if (verification.valid()) {
			return new PlanningOutcome(proposedPlan, verification, false);
		}

		List<String> refinedPlan = taskPlannerPrompt.refinePlan(contract, verification.reason());
		return new PlanningOutcome(refinedPlan, verification, true);
	}
}
