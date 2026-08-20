package com.tark.harness.engine.application;

import com.tark.harness.context.domain.AgentState;
import com.tark.harness.context.domain.GoalContract;
import com.tark.harness.engine.adapters.EmbeddedAgentEngine;
import com.tark.harness.engine.application.PreflightPlanner.PlanningOutcome;
import com.tark.harness.websocket.protocol.AgentAction.StatusAction;
import com.tark.harness.websocket.protocol.AgentAction.SystemAction;
import com.tark.harness.websocket.protocol.ServerMessage;
import com.tark.harness.websocket.protocol.ServerMessage.ActionMessage;
import com.tark.harness.websocket.protocol.ServerMessage.StateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entry point for turning a user prompt into a running agent turn. For a brand-new conversation
 * it runs the full pre-flight sequence - goal intake, plan generation, self-critique
 * verification, and refinement if needed - narrating each phase before handing off to the
 * streaming ReAct execution loop; a prompt that continues an existing conversation skips
 * straight to execution.
 */
public class CognitiveOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(CognitiveOrchestrator.class);

	private final PreflightPlanner preflightPlanner;
	private final EmbeddedAgentEngine engine;

	public CognitiveOrchestrator(PreflightPlanner preflightPlanner, EmbeddedAgentEngine engine) {
		this.preflightPlanner = preflightPlanner;
		this.engine = engine;
	}

	/**
	 * Routes a user prompt to the right entry point: straight into the ReAct loop if the
	 * session already has an active conversation, otherwise through full pre-flight planning.
	 */
	public Flux<ServerMessage> handlePrompt(String sessionId, String promptText, List<ToolCallback> tools) {
		if (engine.hasActiveConversation(sessionId)) {
			return engine.execute(sessionId, promptText, tools);
		}
		return planAndExecute(sessionId, GoalContract.fromFreeformPrompt(promptText), tools);
	}

	/**
	 * Runs the full pre-flight cognitive sequence for the given goal contract, then hands off to
	 * the streaming ReAct execution loop.
	 */
	public Flux<ServerMessage> planAndExecute(
			String sessionId,
			GoalContract contract,
			List<ToolCallback> tools) {

		Flux<ServerMessage> preflightFlux = Flux.defer(() -> {
			try {
				log.info("Starting cognitive pre-flight sequence for session '{}'...", sessionId);
				List<ServerMessage> preflightMessages = new ArrayList<>();

				preflightMessages.add(new StateMessage("planning"));
				preflightMessages.add(new ActionMessage(new StatusAction("planning")));
				preflightMessages.add(new ActionMessage(new SystemAction("Phase 1: Goal Intake acknowledged.")));

				engine.updateGoalContract(contract);

				preflightMessages.add(new ActionMessage(new SystemAction("Phase 2: Generating sequential checklist plan...")));
				preflightMessages.add(new ActionMessage(new SystemAction("Phase 3: Validating plan against constraints & goals...")));

				PlanningOutcome outcome = preflightPlanner.plan(contract);

				if (!outcome.wasRefined()) {
					log.info("Proposed plan approved by verifier. Proceeding...");
					preflightMessages.add(new ActionMessage(new SystemAction("Plan validation approved: " + outcome.verification().reason())));
				} else {
					log.warn("Proposed plan rejected by verifier. Critique: {}. Refinement pass completed.", outcome.verification().reason());
					preflightMessages.add(new ActionMessage(new SystemAction("Plan critique received. Regenerating refined plan...")));
					preflightMessages.add(new ActionMessage(new SystemAction("Refined plan generated successfully.")));
				}

				List<String> finalPlan = outcome.finalPlan();

				preflightMessages.add(new ActionMessage(new SystemAction("Finalized Plan Checklist:\n" +
						finalPlan.stream().map(step -> " * " + step).collect(Collectors.joining("\n")))));

				AgentState initialAgentState = new AgentState(
						finalPlan,
						0,
						List.of(),
						false,
						"processing"
				);
				engine.updateAgentState(initialAgentState);

				preflightMessages.add(new ActionMessage(new SystemAction("Phase 5: Launching autonomous tool execution...")));

				return Flux.fromIterable(preflightMessages);
			} catch (Exception e) {
				log.error("Cognitive pre-flight sequence failed inside session '{}'", sessionId, e);
				return Flux.just(
						new ActionMessage(new SystemAction("Pre-flight planning failed: " + e.getMessage())),
						new StateMessage("idle"),
						new ActionMessage(new StatusAction("idle"))
				);
			}
		}).subscribeOn(Schedulers.boundedElastic());

		Flux<ServerMessage> executionFlux = Flux.defer(() ->
				engine.execute(sessionId, "Proceed to execute the plan steps sequentially starting with Step 1.", tools)
		).subscribeOn(Schedulers.boundedElastic());

		return Flux.concat(preflightFlux, executionFlux);
	}
}
