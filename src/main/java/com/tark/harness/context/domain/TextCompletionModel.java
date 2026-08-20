package com.tark.harness.context.domain;

/**
 * Turns a system/user prompt pair into a model completion.
 */
public interface TextCompletionModel {

	String complete(String systemPrompt, String userPrompt);
}
