package com.tark.harness.context.adapters;

import com.tark.harness.context.domain.TextCompletionModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Completes prompts using whichever LLM backend the host application has configured.
 */
public class SpringAiTextCompletionModel implements TextCompletionModel {

	private final ChatModel chatModel;

	public SpringAiTextCompletionModel(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public String complete(String systemPrompt, String userPrompt) {
		Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
		return chatModel.call(prompt).getResult().getOutput().getText();
	}
}
