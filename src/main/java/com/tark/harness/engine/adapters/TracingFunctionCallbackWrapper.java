package com.tark.harness.engine.adapters;

import com.tark.harness.websocket.protocol.AgentAction.ToolEndAction;
import com.tark.harness.websocket.protocol.AgentAction.ToolOutputAction;
import com.tark.harness.websocket.protocol.AgentAction.ToolStartAction;
import com.tark.harness.websocket.protocol.ServerMessage;
import com.tark.harness.websocket.protocol.ServerMessage.ActionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Sinks;

/**
 * Wraps a host tool so every call is instrumented: it announces the call and reports the tool's
 * output (or a caught failure) as chat telemetry.
 */
public class TracingFunctionCallbackWrapper implements ToolCallback {

	private static final Logger log = LoggerFactory.getLogger(TracingFunctionCallbackWrapper.class);

	private final ToolCallback delegate;
	private final Sinks.Many<ServerMessage> sink;

	public TracingFunctionCallbackWrapper(ToolCallback delegate, Sinks.Many<ServerMessage> sink) {
		this.delegate = delegate;
		this.sink = sink;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return delegate.getToolDefinition();
	}

	@Override
	public String call(String arguments) {
		String toolName = getToolDefinition().name();
		log.info("Intercepted execution call for tool '{}' with arguments: {}", toolName, arguments);

		if (sink != null) {
			sink.tryEmitNext(new ActionMessage(new ToolStartAction(toolName, arguments)));
		}

		String result;
		try {
			result = delegate.call(arguments);
		} catch (Exception e) {
			log.error("Error executing tool '{}'", toolName, e);
			result = "Error executing tool: " + e.getMessage();
		}

		if (sink != null) {
			sink.tryEmitNext(new ActionMessage(new ToolOutputAction(toolName, result)));
			sink.tryEmitNext(new ActionMessage(new ToolEndAction(toolName)));
		}

		return result;
	}

	@Override
	public String call(String arguments, ToolContext toolContext) {
		String toolName = getToolDefinition().name();
		log.info("Intercepted execution call for tool '{}' with arguments and ToolContext: {}", toolName, arguments);

		if (sink != null) {
			sink.tryEmitNext(new ActionMessage(new ToolStartAction(toolName, arguments)));
		}

		String result;
		try {
			result = delegate.call(arguments, toolContext);
		} catch (Exception e) {
			log.error("Error executing tool '{}'", toolName, e);
			result = "Error executing tool: " + e.getMessage();
		}

		if (sink != null) {
			sink.tryEmitNext(new ActionMessage(new ToolOutputAction(toolName, result)));
			sink.tryEmitNext(new ActionMessage(new ToolEndAction(toolName)));
		}

		return result;
	}
}
