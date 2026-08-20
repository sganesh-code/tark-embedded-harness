package com.tark.harness.websocket.domain;

import com.tark.harness.websocket.domain.ObservabilityMessageFactory.RawObservation;
import com.tark.harness.websocket.protocol.ObservabilityConfig;
import com.tark.harness.websocket.protocol.ObservabilityMessage;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ObservabilityMessageFactoryTest {

	private static final RawObservation RAW = new RawObservation(
			"session-1", "ollama", "qwen3-coder", 1234L, 10, 20, 30, "prompt text", "completion text", true);

	private final ObservabilityMessageFactory factory = new ObservabilityMessageFactory();

	@Test
	void sessionIdProviderAndSuccessAreNeverFiltered() {
		ObservabilityMessage message = factory.create(RAW, new ObservabilityConfig(true, Set.of()));

		assertEquals("session-1", message.sessionId());
		assertEquals("ollama", message.provider());
		assertEquals(true, message.success());
	}

	@Test
	void modelInfoIsHiddenWhenCategoryDisabled() {
		ObservabilityMessage message = factory.create(RAW, new ObservabilityConfig(true, Set.of()));
		assertEquals("hidden", message.modelName());
	}

	@Test
	void modelInfoIsIncludedWhenCategoryEnabled() {
		ObservabilityMessage message = factory.create(RAW, new ObservabilityConfig(true, Set.of("model_info")));
		assertEquals("qwen3-coder", message.modelName());
	}

	@Test
	void durationIsNullWhenCategoryDisabledAndPresentWhenEnabled() {
		assertNull(factory.create(RAW, new ObservabilityConfig(true, Set.of())).durationMs());
		assertEquals(1234L, factory.create(RAW, new ObservabilityConfig(true, Set.of("duration"))).durationMs());
	}

	@Test
	void tokenCountsAreAllNullOrAllPresentTogether() {
		ObservabilityMessage hidden = factory.create(RAW, new ObservabilityConfig(true, Set.of()));
		assertNull(hidden.promptTokens());
		assertNull(hidden.completionTokens());
		assertNull(hidden.totalTokens());

		ObservabilityMessage shown = factory.create(RAW, new ObservabilityConfig(true, Set.of("tokens")));
		assertEquals(10, shown.promptTokens());
		assertEquals(20, shown.completionTokens());
		assertEquals(30, shown.totalTokens());
	}

	@Test
	void contentIsHiddenWhenCategoryDisabledAndPresentWhenEnabled() {
		ObservabilityMessage hidden = factory.create(RAW, new ObservabilityConfig(true, Set.of()));
		assertNull(hidden.promptContent());
		assertNull(hidden.completionContent());

		ObservabilityMessage shown = factory.create(RAW, new ObservabilityConfig(true, Set.of("content")));
		assertEquals("prompt text", shown.promptContent());
		assertEquals("completion text", shown.completionContent());
	}
}
