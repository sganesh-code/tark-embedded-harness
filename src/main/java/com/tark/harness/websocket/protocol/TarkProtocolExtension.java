package com.tark.harness.websocket.protocol;

import tools.jackson.databind.jsontype.NamedType;

import java.util.List;

/**
 * Interface that allows hosting applications to register their own custom
 * polymorphic WebSocket protocol types (ClientMessage, ServerMessage, or AgentAction)
 * seamlessly without altering the core library codebase.
 * Compatible with Jackson 3.
 */
public interface TarkProtocolExtension {

	/**
	 * Custom incoming messages from client to server (type field: "type").
	 */
	List<NamedType> registerClientMessages();

	/**
	 * Custom outgoing envelopes from server to client (type field: "type").
	 */
	List<NamedType> registerServerMessages();

	/**
	 * Custom granular execution action steps inside the main envelope (type field: "actionType").
	 */
	List<NamedType> registerAgentActions();
}
