package com.tark.harness.engine.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * Recognizes and parses "/prompt &lt;name&gt; [key=value ...]" calibration commands.
 */
public class PromptCommandParser {

	private static final String PREFIX = "/prompt ";

	/** True if the given text is a "/prompt " calibration command rather than a plain user prompt. */
	public boolean isPromptCommand(String text) {
		return text != null && text.trim().startsWith(PREFIX);
	}

	/** Callers must already have confirmed {@link #isPromptCommand} before calling this. */
	public PromptCommand parse(String commandText) {
		String trimmedCommand = commandText.trim();
		String rest = trimmedCommand.length() > PREFIX.length()
				? trimmedCommand.substring(PREFIX.length()).trim()
				: "";
		String[] tokens = rest.isEmpty() ? new String[0] : rest.split("\\s+");
		if (tokens.length == 0 || tokens[0].trim().isEmpty()) {
			throw new IllegalArgumentException("No MCP Prompt name provided. Usage: /prompt <name> [key=value ...]");
		}
		String name = tokens[0].trim();

		Map<String, String> arguments = new HashMap<>();
		for (int i = 1; i < tokens.length; i++) {
			String[] parts = tokens[i].split("=", 2);
			if (parts.length == 2) {
				arguments.put(parts[0].trim(), parts[1].trim());
			}
		}

		return new PromptCommand(name, arguments);
	}
}
