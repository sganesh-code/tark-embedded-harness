package com.tark.harness.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Spring Boot Configuration Properties representing the configurable knobs
 * of the embedded Tark Agent Harness library (prefix: "tark.harness").
 */
@ConfigurationProperties(prefix = "tark.harness")
public class TarkHarnessProperties {

	private String modelName;
	private Double temperature;

	private String websocketPath = "/ws";
	private Set<String> allowedOrigins = Set.of("*");

	private int maxMemoryTurns = 20;
	private int compactionBatchSize = 10;

	private int contextWindowSize = 32768;
	private double pressureThresholdPercent = 0.75;
	private int distillationThresholdCharacters = 1000;
	private double initialCharsPerToken = 4.0;

	private ObservabilityProperties observability = new ObservabilityProperties();

	public String getModelName() {
		return modelName;
	}

	public void setModelName(String modelName) {
		this.modelName = modelName;
	}

	public Double getTemperature() {
		return temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public String getWebsocketPath() {
		return websocketPath;
	}

	public void setWebsocketPath(String websocketPath) {
		this.websocketPath = websocketPath;
	}

	public Set<String> getAllowedOrigins() {
		return allowedOrigins;
	}

	public void setAllowedOrigins(Set<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	public int getMaxMemoryTurns() {
		return maxMemoryTurns;
	}

	public void setMaxMemoryTurns(int maxMemoryTurns) {
		this.maxMemoryTurns = maxMemoryTurns;
	}

	public int getCompactionBatchSize() {
		return compactionBatchSize;
	}

	public void setCompactionBatchSize(int compactionBatchSize) {
		this.compactionBatchSize = compactionBatchSize;
	}

	public int getContextWindowSize() {
		return contextWindowSize;
	}

	public void setContextWindowSize(int contextWindowSize) {
		this.contextWindowSize = contextWindowSize;
	}

	public double getPressureThresholdPercent() {
		return pressureThresholdPercent;
	}

	public void setPressureThresholdPercent(double pressureThresholdPercent) {
		this.pressureThresholdPercent = pressureThresholdPercent;
	}

	public int getDistillationThresholdCharacters() {
		return distillationThresholdCharacters;
	}

	public void setDistillationThresholdCharacters(int distillationThresholdCharacters) {
		this.distillationThresholdCharacters = distillationThresholdCharacters;
	}

	public double getInitialCharsPerToken() {
		return initialCharsPerToken;
	}

	public void setInitialCharsPerToken(double initialCharsPerToken) {
		this.initialCharsPerToken = initialCharsPerToken;
	}

	public ObservabilityProperties getObservability() {
		return observability;
	}

	public void setObservability(ObservabilityProperties observability) {
		this.observability = observability;
	}

	public static class ObservabilityProperties {
		private boolean enabled = true;
		private Set<String> broadcastCategories = Set.of("tokens", "duration", "model_info");

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Set<String> getBroadcastCategories() {
			return broadcastCategories;
		}

		public void setBroadcastCategories(Set<String> broadcastCategories) {
			this.broadcastCategories = broadcastCategories;
		}
	}
}
