package com.tark.harness.context.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Objects;

/**
 * Self-calibrating token estimator. Approximates prompt token counts from raw character
 * counts (messages + bound tool schemas), and continuously recalibrates its chars-per-token
 * ratio against the real {@code promptTokens} usage reported back by the model after each call.
 */
public class TokenEstimator {

	private static final Logger log = LoggerFactory.getLogger(TokenEstimator.class);
	private static final double EMA_WEIGHT = 0.3;

	private volatile double charsPerToken;

	public TokenEstimator(double initialCharsPerToken) {
		this.charsPerToken = initialCharsPerToken > 0 ? initialCharsPerToken : 4.0;
	}

	/**
	 * Estimates the total prompt token count for the given messages and bound tool schemas,
	 * using the current calibrated chars-per-token ratio.
	 */
	public int estimateTokens(List<Message> messages, List<ToolCallback> toolCallbacks) {
		return (int) Math.ceil(estimateChars(messages, toolCallbacks) / charsPerToken);
	}

	/**
	 * Estimates the total raw character footprint of the given messages and bound tool schemas.
	 */
	public int estimateChars(List<Message> messages, List<ToolCallback> toolCallbacks) {
		int totalChars = 0;

		if (messages != null) {
			for (Message message : messages) {
				String text = message.getText();
				if (text != null) {
					totalChars += text.length();
				}
				if (message instanceof ToolResponseMessage toolResponseMessage) {
					for (var response : toolResponseMessage.getResponses()) {
						totalChars += lengthOf(response.responseData());
					}
				}
			}
		}

		if (toolCallbacks != null) {
			for (ToolCallback callback : toolCallbacks) {
				if (callback == null || callback.getToolDefinition() == null) {
					continue;
				}
				var definition = callback.getToolDefinition();
				totalChars += lengthOf(definition.name()) + lengthOf(definition.description()) + lengthOf(definition.inputSchema());
			}
		}

		return totalChars;
	}

	/**
	 * Feeds back the actual reported prompt token usage for a request whose character
	 * footprint we previously estimated, recalibrating the chars-per-token ratio via EMA.
	 */
	public void recordActualUsage(int estimatedChars, int actualPromptTokens) {
		if (estimatedChars <= 0 || actualPromptTokens <= 0) {
			return;
		}

		double observedRatio = (double) estimatedChars / actualPromptTokens;
		double updatedRatio = ((1 - EMA_WEIGHT) * charsPerToken) + (EMA_WEIGHT * observedRatio);

		log.debug("Recalibrating token estimator: observed ratio {} (chars={}, tokens={}), ratio {} -> {}",
				observedRatio, estimatedChars, actualPromptTokens, charsPerToken, updatedRatio);

		this.charsPerToken = updatedRatio;
	}

	private static int lengthOf(String value) {
		return Objects.requireNonNullElse(value, "").length();
	}
}
