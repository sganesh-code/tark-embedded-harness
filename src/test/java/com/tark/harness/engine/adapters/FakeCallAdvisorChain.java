package com.tark.harness.engine.adapters;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

import java.util.List;

/**
 * Hand-written test double for {@link CallAdvisorChain}: records the request it was called
 * with and returns a fixed canned response - no dynamic-proxy mocking framework involved.
 */
class FakeCallAdvisorChain implements CallAdvisorChain {

	private final ChatClientResponse response;
	private ChatClientRequest capturedRequest;

	FakeCallAdvisorChain(ChatClientResponse response) {
		this.response = response;
	}

	@Override
	public ChatClientResponse nextCall(ChatClientRequest request) {
		this.capturedRequest = request;
		return response;
	}

	@Override
	public List<CallAdvisor> getCallAdvisors() {
		return List.of();
	}

	@Override
	public CallAdvisorChain copy(CallAdvisor advisor) {
		return this;
	}

	ChatClientRequest capturedRequest() {
		return capturedRequest;
	}
}
