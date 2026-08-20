package com.tark.harness.websocket.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Discriminated polymorphic type for all outgoing messages from server to client.
 */
@JsonTypeInfo(
		use = JsonTypeInfo.Id.NAME,
		include = JsonTypeInfo.As.PROPERTY,
		property = "type"
)
@JsonSubTypes({
		@JsonSubTypes.Type(value = ServerMessage.ActionMessage.class, name = "action"),
		@JsonSubTypes.Type(value = ServerMessage.UsageMessage.class, name = "usage"),
		@JsonSubTypes.Type(value = ServerMessage.StateMessage.class, name = "state"),
		@JsonSubTypes.Type(value = ObservabilityMessage.class, name = "observability")
})
public interface ServerMessage {

	/**
	 * Outgoing main execution step action payload.
	 * Flattened using @JsonUnwrapped to make actionType and type siblings.
	 */
	@JsonTypeName("action")
	record ActionMessage(@JsonUnwrapped AgentAction action) implements ServerMessage {
		
		/**
		 * Dynamically extracts the action's type name from its @JsonTypeName annotation,
		 * explicitly serializing it as a first-class sibling of "type" to bypass @JsonUnwrapped metadata stripping.
		 */
		@JsonProperty("actionType")
		public String actionType() {
			if (action == null) {
				return null;
			}
			JsonTypeName typeName = action.getClass().getAnnotation(JsonTypeName.class);
			return typeName != null ? typeName.value() : null;
		}
	}

	/**
	 * Outgoing LLM token usage stats.
	 * Annotated with snake_case @JsonProperty to align with tark-webui.
	 */
	@JsonTypeName("usage")
	record UsageMessage(
			@JsonProperty("prompt_tokens") int promptTokens,
			@JsonProperty("completion_tokens") int completionTokens,
			@JsonProperty("total_tokens") int totalTokens
	) implements ServerMessage {}

	/**
	 * Outgoing aggregate connection status (e.g. "idle", "processing").
	 */
	@JsonTypeName("state")
	record StateMessage(String status) implements ServerMessage {}
}
