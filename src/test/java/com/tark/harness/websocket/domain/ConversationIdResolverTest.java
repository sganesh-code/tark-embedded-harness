package com.tark.harness.websocket.domain;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Built entirely with real Micrometer {@link Observation} objects (no mocking framework) since
 * the resolver's whole job is walking real observation-context relationships. A registry with no
 * handlers silently produces no-op observations whose context mutations are discarded, so a
 * permissive handler is registered to force real {@code SimpleObservation}s to be created.
 */
class ConversationIdResolverTest {

	private final ConversationIdResolver resolver = new ConversationIdResolver();
	private final ObservationRegistry registry = newRegistryWithRealObservations();

	private static ObservationRegistry newRegistryWithRealObservations() {
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}
		});
		return registry;
	}

	@Test
	void resolvesFromDirectContextVariable() {
		Observation.Context context = new Observation.Context();
		context.put(ChatMemory.CONVERSATION_ID, "session-direct");

		assertEquals("session-direct", resolver.resolve(context));
	}

	@Test
	void resolvesFromLowCardinalityTagOnAParentObservation() {
		Observation parent = Observation.createNotStarted("parent", registry);
		parent.getContext().addLowCardinalityKeyValue(KeyValue.of("spring.ai.chat.client.conversation.id", "session-low-card"));

		Observation.Context childContext = new Observation.Context();
		childContext.setParentObservation(parent);

		assertEquals("session-low-card", resolver.resolve(childContext));
	}

	@Test
	void resolvesFromHighCardinalityTagOnAGrandparentObservation() {
		Observation grandparent = Observation.createNotStarted("grandparent", registry);
		grandparent.getContext().addHighCardinalityKeyValue(KeyValue.of("conversation.id", "session-high-card"));

		Observation parent = Observation.createNotStarted("parent", registry);
		parent.getContext().setParentObservation(grandparent);

		Observation.Context childContext = new Observation.Context();
		childContext.setParentObservation(parent);

		assertEquals("session-high-card", resolver.resolve(childContext));
	}

	@Test
	void returnsNullWhenNotFoundAnywhereInTheChain() {
		Observation parent = Observation.createNotStarted("parent", registry);

		Observation.Context childContext = new Observation.Context();
		childContext.setParentObservation(parent);

		assertNull(resolver.resolve(childContext));
	}
}
