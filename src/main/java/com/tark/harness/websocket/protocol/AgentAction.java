package com.tark.harness.websocket.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

/**
 * Granular action steps emitted by the agent reasoning loop (type field: "actionType").
 */
@JsonTypeInfo(
		use = JsonTypeInfo.Id.NAME,
		include = JsonTypeInfo.As.PROPERTY,
		property = "actionType"
)
@JsonSubTypes({
		@JsonSubTypes.Type(value = AgentAction.StatusAction.class, name = "status"),
		@JsonSubTypes.Type(value = AgentAction.DeltaAction.class, name = "delta"),
		@JsonSubTypes.Type(value = AgentAction.EndAction.class, name = "end"),
		@JsonSubTypes.Type(value = AgentAction.ToolStartAction.class, name = "tool_start"),
		@JsonSubTypes.Type(value = AgentAction.ToolOutputAction.class, name = "tool_output"),
		@JsonSubTypes.Type(value = AgentAction.ToolEndAction.class, name = "tool_end"),
		@JsonSubTypes.Type(value = AgentAction.RequestChoiceAction.class, name = "request_choice"),
		@JsonSubTypes.Type(value = AgentAction.RequestInputAction.class, name = "request_input"),
		@JsonSubTypes.Type(value = AgentAction.SystemAction.class, name = "system"),
		@JsonSubTypes.Type(value = AgentAction.ClearAction.class, name = "clear"),
		@JsonSubTypes.Type(value = AgentAction.ExitAction.class, name = "exit")
})
public interface AgentAction {

	/**
	 * Agent status update (e.g., "thinking", "processing", etc.).
	 */
	@JsonTypeName("status")
	record StatusAction(String status) implements AgentAction {}

	/**
	 * Streaming assistant response text chunks (deltas).
	 */
	@JsonTypeName("delta")
	record DeltaAction(String text) implements AgentAction {}

	/**
	 * Signals completion of assistant response turn.
	 */
	@JsonTypeName("end")
	record EndAction() implements AgentAction {}

	/**
	 * Signals a tool is about to be executed with the given name and arguments.
	 * Annotated with @JsonProperty("args") to align with tark-webui.
	 */
	@JsonTypeName("tool_start")
	record ToolStartAction(String name, @JsonProperty("args") String arguments) implements AgentAction {}

	/**
	 * Signals a tool has completed execution with its result.
	 * Annotated with @JsonProperty("text") to align with tark-webui.
	 */
	@JsonTypeName("tool_output")
	record ToolOutputAction(String name, @JsonProperty("text") String result) implements AgentAction {}

	/**
	 * Signals the final teardown of a tool execution.
	 */
	@JsonTypeName("tool_end")
	record ToolEndAction(String name) implements AgentAction {}

	/**
	 * Suspends loop and queries the user with a questionnaire choice list.
	 */
	@JsonTypeName("request_choice")
	record RequestChoiceAction(String prompt, List<String> options, boolean allowCustom) implements AgentAction {}

	/**
	 * Suspends loop and queries the user with a questionnaire free-form input field.
	 */
	@JsonTypeName("request_input")
	record RequestInputAction(String prompt) implements AgentAction {}

	/**
	 * Emits a system notification or warning.
	 */
	@JsonTypeName("system")
	record SystemAction(String text) implements AgentAction {}

	/**
	 * Clears the client UI terminal.
	 */
	@JsonTypeName("clear")
	record ClearAction() implements AgentAction {}

	/**
	 * Closes the client chat connection.
	 */
	@JsonTypeName("exit")
	record ExitAction() implements AgentAction {}
}
