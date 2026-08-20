package com.tark.harness.context.domain;

/**
 * Hand-written test double for {@link TextCompletionModel}. Either returns a fixed canned
 * response, or throws a fixed exception, depending on how it's configured - no dynamic-proxy
 * mocking framework involved.
 */
public class FakeTextCompletionModel implements TextCompletionModel {

	private String cannedResponse = "";
	private RuntimeException failure;
	private String lastSystemPrompt;
	private String lastUserPrompt;
	private int callCount;

	public static FakeTextCompletionModel returning(String response) {
		FakeTextCompletionModel fake = new FakeTextCompletionModel();
		fake.cannedResponse = response;
		return fake;
	}

	public static FakeTextCompletionModel throwing(RuntimeException failure) {
		FakeTextCompletionModel fake = new FakeTextCompletionModel();
		fake.failure = failure;
		return fake;
	}

	@Override
	public String complete(String systemPrompt, String userPrompt) {
		callCount++;
		lastSystemPrompt = systemPrompt;
		lastUserPrompt = userPrompt;
		if (failure != null) {
			throw failure;
		}
		return cannedResponse;
	}

	public int callCount() {
		return callCount;
	}

	public String lastUserPrompt() {
		return lastUserPrompt;
	}

	public String lastSystemPrompt() {
		return lastSystemPrompt;
	}
}
