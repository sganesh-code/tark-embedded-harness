package com.tark.harness.engine.domain;

import java.util.Map;

/**
 * A parsed "/prompt &lt;name&gt; [key=value ...]" calibration command. Produced by
 * {@link PromptCommandParser}.
 */
public record PromptCommand(String name, Map<String, String> arguments) {
}
