package com.tark.harness.websocket;

import com.tark.harness.websocket.protocol.AgentAction;
import org.springframework.context.ApplicationEvent;

/**
 * Generic decoupled Spring ApplicationEvent published by host applications to stream
 * any standard or custom {@link AgentAction} frame down to the active WebSocket session.
 */
public class TarkAgentActionEvent extends ApplicationEvent {

	private final String sessionId;
	private final AgentAction action;

	public TarkAgentActionEvent(Object source, String sessionId, AgentAction action) {
		super(source);
		this.sessionId = sessionId;
		this.action = action;
	}

	public String getSessionId() {
		return sessionId;
	}

	public AgentAction getAction() {
		return action;
	}
}
