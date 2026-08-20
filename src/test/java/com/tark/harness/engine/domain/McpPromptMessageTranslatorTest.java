package com.tark.harness.engine.domain;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class McpPromptMessageTranslatorTest {

	private final McpPromptMessageTranslator translator = new McpPromptMessageTranslator();

	@Test
	void userRoleMapsToUserMessage() {
		McpSchema.PromptMessage mcpMessage = new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(null, "hello", java.util.Map.of()));

		List<Message> messages = translator.translate(List.of(mcpMessage));

		assertEquals(1, messages.size());
		assertInstanceOf(UserMessage.class, messages.get(0));
		assertEquals("hello", messages.get(0).getText());
	}

	@Test
	void assistantRoleMapsToAssistantMessage() {
		McpSchema.PromptMessage mcpMessage = new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent(null, "reply", java.util.Map.of()));

		List<Message> messages = translator.translate(List.of(mcpMessage));

		assertInstanceOf(AssistantMessage.class, messages.get(0));
		assertEquals("reply", messages.get(0).getText());
	}

	@Test
	void multipleMessagesPreserveOrder() {
		McpSchema.PromptMessage first = new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(null, "first", java.util.Map.of()));
		McpSchema.PromptMessage second = new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent(null, "second", java.util.Map.of()));

		List<Message> messages = translator.translate(List.of(first, second));

		assertEquals("first", messages.get(0).getText());
		assertEquals("second", messages.get(1).getText());
	}

	@Test
	void emptyInputYieldsEmptyOutput() {
		assertEquals(List.of(), translator.translate(List.of()));
	}

	@Test
	void nonTextContentMapsToAnEmptyStringMessage() {
		McpSchema.Content nonTextContent = () -> java.util.Map.of();
		McpSchema.PromptMessage mcpMessage = new McpSchema.PromptMessage(McpSchema.Role.USER, nonTextContent);

		List<Message> messages = translator.translate(List.of(mcpMessage));

		assertEquals("", messages.get(0).getText());
	}
}
