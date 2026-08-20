package com.tark.harness.websocket.adapters;

import com.tark.harness.websocket.TarkObservabilityEvent;
import com.tark.harness.websocket.protocol.ObservabilityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarkChatModelObservationHandlerTest {

	private static class CapturingEventPublisher implements ApplicationEventPublisher {
		private final List<Object> events = new ArrayList<>();

		@Override
		public void publishEvent(Object event) {
			events.add(event);
		}

		List<Object> events() {
			return events;
		}
	}

	private static ChatModelObservationContext contextWithSessionId(String sessionId) {
		ChatModelObservationContext context = ChatModelObservationContext.builder()
				.prompt(new Prompt(List.of(new UserMessage("hi"))))
				.provider("ollama")
				.build();
		if (sessionId != null) {
			context.put(ChatMemory.CONVERSATION_ID, sessionId);
		}
		context.setResponse(new ChatResponse(List.of()));
		return context;
	}

	@Test
	void doesNotPublishWhenConfigDisabled() {
		CapturingEventPublisher publisher = new CapturingEventPublisher();
		ObservabilityConfig config = new ObservabilityConfig(false, Set.of("tokens"));
		TarkChatModelObservationHandler handler = new TarkChatModelObservationHandler(publisher, config);

		handler.onStop(contextWithSessionId("session-1"));

		assertTrue(publisher.events().isEmpty());
	}

	@Test
	void doesNotPublishWhenSessionIdCannotBeResolved() {
		CapturingEventPublisher publisher = new CapturingEventPublisher();
		ObservabilityConfig config = new ObservabilityConfig(true, Set.of("tokens"));
		TarkChatModelObservationHandler handler = new TarkChatModelObservationHandler(publisher, config);

		handler.onStop(contextWithSessionId(null));

		assertTrue(publisher.events().isEmpty());
	}

	@Test
	void publishesAnObservabilityEventForTheResolvedSession() {
		CapturingEventPublisher publisher = new CapturingEventPublisher();
		ObservabilityConfig config = new ObservabilityConfig(true, Set.of("tokens", "duration", "model_info"));
		TarkChatModelObservationHandler handler = new TarkChatModelObservationHandler(publisher, config);

		ChatModelObservationContext context = contextWithSessionId("session-1");
		handler.onStart(context);
		handler.onStop(context);

		assertEquals(1, publisher.events().size());
		TarkObservabilityEvent event = (TarkObservabilityEvent) publisher.events().get(0);
		assertEquals("session-1", event.getSessionId());
		assertEquals("ollama", event.getMessage().provider());
	}

	@Test
	void supportsContextOnlyAcceptsChatModelObservationContext() {
		TarkChatModelObservationHandler handler = new TarkChatModelObservationHandler(
				event -> {}, new ObservabilityConfig(true, Set.of()));

		assertTrue(handler.supportsContext(contextWithSessionId("session-1")));
	}
}
