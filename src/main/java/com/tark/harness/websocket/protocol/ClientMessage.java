package com.tark.harness.websocket.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Discriminated polymorphic type for all incoming messages from client to server.
 */
@JsonTypeInfo(
		use = JsonTypeInfo.Id.NAME,
		include = JsonTypeInfo.As.PROPERTY,
		property = "type"
)
@JsonSubTypes({
		@JsonSubTypes.Type(value = ClientMessage.PromptMessage.class, name = "prompt"),
		@JsonSubTypes.Type(value = ClientMessage.CancelMessage.class, name = "cancel"),
		@JsonSubTypes.Type(value = ClientMessage.ConfigUpdateMessage.class, name = "config_update"),
		@JsonSubTypes.Type(value = ClientMessage.McpToolToggleMessage.class, name = "mcp_tool_toggle"),
		@JsonSubTypes.Type(value = ClientMessage.CleanupMemoryMessage.class, name = "cleanup_memory"),
		@JsonSubTypes.Type(value = ClientMessage.ChoiceResponseMessage.class, name = "choice_response"),
		@JsonSubTypes.Type(value = ClientMessage.InputResponseMessage.class, name = "input_response")
})
public interface ClientMessage {

	/**
	 * Client submits a new unstructured text prompt to execute.
	 */
	record PromptMessage(String text) implements ClientMessage {}

	/**
	 * Client requests immediate execution cancellation of the running agent stream.
	 */
	record CancelMessage() implements ClientMessage {}

	/**
	 * Client updates configuration values.
	 */
	record ConfigUpdateMessage(
			Integer maxTokens,
			Integer panelWidth,
			Integer contextWindowSize,
			Boolean enableDistillation
	) implements ClientMessage {}

	/**
	 * Client toggles the active state of an MCP tool.
	 */
	record McpToolToggleMessage(String name, boolean enabled) implements ClientMessage {}

	/**
	 * Client requests clearing of conversational history and memory context.
	 */
	record CleanupMemoryMessage() implements ClientMessage {}

	/**
	 * Client responds to a suspended multi-choice interactive questionnaire.
	 */
	record ChoiceResponseMessage(String selected) implements ClientMessage {}

	/**
	 * Client responds to a suspended free-form interactive input questionnaire.
	 */
	record InputResponseMessage(String value) implements ClientMessage {}
}
