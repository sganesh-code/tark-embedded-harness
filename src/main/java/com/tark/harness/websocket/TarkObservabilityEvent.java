package com.tark.harness.websocket;

import com.tark.harness.websocket.protocol.ObservabilityMessage;
import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent that wraps a completed ChatModel execution's observability telemetry.
 */
public class TarkObservabilityEvent extends ApplicationEvent {

	private final ObservabilityMessage message;

	public TarkObservabilityEvent(Object source, ObservabilityMessage message) {
		super(source);
		this.message = message;
	}

	public ObservabilityMessage getMessage() {
		return message;
	}

	public String getSessionId() {
		return message.sessionId();
	}
}
