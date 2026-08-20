package com.tark.harness.engine.domain;

import com.tark.harness.websocket.protocol.AgentAction.EndAction;
import com.tark.harness.websocket.protocol.AgentAction.StatusAction;
import com.tark.harness.websocket.protocol.AgentAction.SystemAction;
import com.tark.harness.websocket.protocol.ServerMessage;
import com.tark.harness.websocket.protocol.ServerMessage.ActionMessage;
import com.tark.harness.websocket.protocol.ServerMessage.StateMessage;

import java.util.List;

/**
 * Announces a turn's lifecycle (started, completed, failed) to the client as the matching
 * {@link ServerMessage} sequence.
 */
public class TurnLifecycleMessages {

	public List<ServerMessage> onStarted() {
		return List.of(
				new StateMessage("processing"),
				new ActionMessage(new StatusAction("processing")));
	}

	public List<ServerMessage> onCompleted() {
		return List.of(
				new ActionMessage(new EndAction()),
				new StateMessage("idle"),
				new ActionMessage(new StatusAction("idle")));
	}

	public List<ServerMessage> onFailed(Throwable error) {
		return List.of(
				new ActionMessage(new SystemAction("Execution failed: " + error.getMessage())),
				new StateMessage("idle"),
				new ActionMessage(new StatusAction("idle")));
	}
}
