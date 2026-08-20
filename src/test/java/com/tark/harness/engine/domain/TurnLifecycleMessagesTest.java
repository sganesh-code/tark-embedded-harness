package com.tark.harness.engine.domain;

import com.tark.harness.websocket.protocol.AgentAction.EndAction;
import com.tark.harness.websocket.protocol.AgentAction.StatusAction;
import com.tark.harness.websocket.protocol.AgentAction.SystemAction;
import com.tark.harness.websocket.protocol.ServerMessage;
import com.tark.harness.websocket.protocol.ServerMessage.ActionMessage;
import com.tark.harness.websocket.protocol.ServerMessage.StateMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnLifecycleMessagesTest {

	private final TurnLifecycleMessages lifecycle = new TurnLifecycleMessages();

	@Test
	void onStartedAnnouncesProcessing() {
		List<ServerMessage> messages = lifecycle.onStarted();

		assertEquals(List.of(
				new StateMessage("processing"),
				new ActionMessage(new StatusAction("processing"))
		), messages);
	}

	@Test
	void onCompletedAnnouncesEndAndIdle() {
		List<ServerMessage> messages = lifecycle.onCompleted();

		assertEquals(List.of(
				new ActionMessage(new EndAction()),
				new StateMessage("idle"),
				new ActionMessage(new StatusAction("idle"))
		), messages);
	}

	@Test
	void onFailedIncludesTheErrorMessageAndReturnsToIdle() {
		List<ServerMessage> messages = lifecycle.onFailed(new RuntimeException("boom"));

		assertEquals(List.of(
				new ActionMessage(new SystemAction("Execution failed: boom")),
				new StateMessage("idle"),
				new ActionMessage(new StatusAction("idle"))
		), messages);
	}
}
