package com.tark.harness.engine.domain;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates MCP prompt messages into Spring AI's {@link Message} vocabulary.
 */
public class McpPromptMessageTranslator {

	public List<Message> translate(List<McpSchema.PromptMessage> mcpMessages) {
		List<Message> messages = new ArrayList<>();

		for (McpSchema.PromptMessage mcpMsg : mcpMessages) {
			String text = "";
			if (mcpMsg.content() instanceof McpSchema.TextContent textContent) {
				text = textContent.text();
			}

			if (mcpMsg.role() == McpSchema.Role.USER) {
				messages.add(new UserMessage(text));
			} else if (mcpMsg.role() == McpSchema.Role.ASSISTANT) {
				messages.add(new AssistantMessage(text));
			} else {
				messages.add(new SystemMessage(text));
			}
		}

		return messages;
	}
}
