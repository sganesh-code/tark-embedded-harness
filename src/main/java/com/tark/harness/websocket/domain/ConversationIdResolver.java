package com.tark.harness.websocket.domain;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationView;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * Resolves the active conversation/session ID for a Micrometer {@link Observation.Context}
 * (such as Spring AI's {@code ChatModelObservationContext}), recursively walking up the parent-
 * observation chain since the ID may be attached as a direct context variable or as a
 * low/high-cardinality tag on an ancestor observation (e.g.
 * {@code spring.ai.chat.client.conversation.id}) rather than on the context directly.
 */
public class ConversationIdResolver {

	public String resolve(Observation.Context context) {
		String sessionId = context.get(ChatMemory.CONVERSATION_ID);
		if (sessionId != null) {
			return sessionId;
		}

		ObservationView currentObs = context.getParentObservation();
		while (currentObs != null) {
			Observation.ContextView parentContext = currentObs.getContextView();

			String parentSessionId = parentContext.get(ChatMemory.CONVERSATION_ID);
			if (parentSessionId != null) {
				return parentSessionId;
			}

			for (KeyValue kv : parentContext.getLowCardinalityKeyValues()) {
				if (kv.getKey().contains("conversation.id")) {
					return kv.getValue();
				}
			}

			for (KeyValue kv : parentContext.getHighCardinalityKeyValues()) {
				if (kv.getKey().contains("conversation.id")) {
					return kv.getValue();
				}
			}

			currentObs = parentContext.getParentObservation();
		}

		return null;
	}
}
