package com.tark.harness.engine.adapters;

import com.tark.harness.websocket.protocol.AgentAction.ToolEndAction;
import com.tark.harness.websocket.protocol.AgentAction.ToolOutputAction;
import com.tark.harness.websocket.protocol.AgentAction.ToolStartAction;
import com.tark.harness.websocket.protocol.ServerMessage;
import com.tark.harness.websocket.protocol.ServerMessage.ActionMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TracingFunctionCallbackWrapperTest {

	private static ToolCallback fakeToolCallback(String name, String result) {
		ToolDefinition definition = ToolDefinition.builder().name(name).description("d").inputSchema("{}").build();
		return new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String toolInput) {
				return result;
			}
		};
	}

	private static ToolCallback throwingToolCallback(String name, RuntimeException failure) {
		ToolDefinition definition = ToolDefinition.builder().name(name).description("d").inputSchema("{}").build();
		return new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String toolInput) {
				throw failure;
			}
		};
	}

	private static List<ServerMessage> subscribedSink(Sinks.Many<ServerMessage> sink) {
		List<ServerMessage> emitted = new ArrayList<>();
		sink.asFlux().subscribe(emitted::add);
		return emitted;
	}

	@Test
	void successfulCallEmitsStartOutputAndEndInOrder() {
		Sinks.Many<ServerMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
		List<ServerMessage> emitted = subscribedSink(sink);
		ToolCallback wrapper = new TracingFunctionCallbackWrapper(fakeToolCallback("calc", "42"), sink);

		String result = wrapper.call("{\"x\":1}");

		assertEquals("42", result);
		assertEquals(3, emitted.size());
		assertEquals("calc", ((ToolStartAction) ((ActionMessage) emitted.get(0)).action()).name());
		assertEquals("42", ((ToolOutputAction) ((ActionMessage) emitted.get(1)).action()).result());
		assertEquals("calc", ((ToolEndAction) ((ActionMessage) emitted.get(2)).action()).name());
	}

	@Test
	void delegateFailureIsCaughtAndTurnedIntoAnErrorResultInstant() {
		Sinks.Many<ServerMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
		List<ServerMessage> emitted = subscribedSink(sink);
		ToolCallback wrapper = new TracingFunctionCallbackWrapper(
				throwingToolCallback("calc", new RuntimeException("boom")), sink);

		String result = wrapper.call("{}");

		assertEquals("Error executing tool: boom", result);
		ToolOutputAction output = (ToolOutputAction) ((ActionMessage) emitted.get(1)).action();
		assertEquals("Error executing tool: boom", output.result());
	}

	@Test
	void nullSinkDoesNotThrow() {
		ToolCallback wrapper = new TracingFunctionCallbackWrapper(fakeToolCallback("calc", "42"), null);

		assertEquals("42", wrapper.call("{}"));
	}

	@Test
	void callWithToolContextOverloadBehavesTheSameAsPlainCall() {
		Sinks.Many<ServerMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
		List<ServerMessage> emitted = subscribedSink(sink);
		ToolCallback wrapper = new TracingFunctionCallbackWrapper(fakeToolCallback("calc", "42"), sink);

		String result = wrapper.call("{}", new ToolContext(java.util.Map.of()));

		assertEquals("42", result);
		assertEquals(3, emitted.size());
	}
}
