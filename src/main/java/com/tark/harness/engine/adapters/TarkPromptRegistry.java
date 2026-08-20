package com.tark.harness.engine.adapters;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry that walks the host Spring ApplicationContext to discover, index,
 * and dynamically execute local {@link McpPrompt} methods.
 */
public class TarkPromptRegistry {

	private static final Logger log = LoggerFactory.getLogger(TarkPromptRegistry.class);
	private final Map<String, PromptRef> prompts = new HashMap<>();

	private record PromptRef(Object bean, Method method) {}

	public TarkPromptRegistry(ApplicationContext context) {
		String[] beanNames = context.getBeanDefinitionNames();
		log.info("Scanning host ApplicationContext for local @McpPrompt methods...");

		for (String beanName : beanNames) {
			try {
				Object bean = context.getBean(beanName);
				Class<?> targetClass = org.springframework.util.ClassUtils.getUserClass(bean);
				Method[] methods = targetClass.getMethods();

				for (Method method : methods) {
					McpPrompt ann = method.getAnnotation(McpPrompt.class);
					if (ann != null) {
						String promptName = ann.name().isEmpty() ? method.getName() : ann.name();
						prompts.put(promptName, new PromptRef(bean, method));
						log.info("Discovered local @McpPrompt '{}' in bean '{}'.", promptName, beanName);
					}
				}
			} catch (Exception e) {
			}
		}
	}

	/**
	 * Executes the requested local prompt dynamically.
	 * Resolves parameters using `@McpArg` or parameter reflection names.
	 *
	 * @param name Name of the MCP prompt.
	 * @param args Raw argument key-value mappings.
	 * @return The returned GetPromptResult containing formatted prompt instructions.
	 */
	public GetPromptResult executePrompt(String name, Map<String, String> args) {
		PromptRef ref = prompts.get(name);
		if (ref == null) {
			throw new IllegalArgumentException("MCP Prompt '" + name + "' is not registered.");
		}

		try {
			Method method = ref.method();
			Parameter[] parameters = method.getParameters();
			Object[] invokeArgs = new Object[parameters.length];

			for (int i = 0; i < parameters.length; i++) {
				Parameter param = parameters[i];
				McpArg argAnn = param.getAnnotation(McpArg.class);
				String argName = (argAnn != null && !argAnn.name().isEmpty()) ? argAnn.name() : param.getName();

				String value = args.get(argName);
				if (value == null && argAnn != null && argAnn.required()) {
					throw new IllegalArgumentException("Required MCP Prompt argument '" + argName + "' is missing.");
				}
				invokeArgs[i] = value;
			}

			return (GetPromptResult) method.invoke(ref.bean(), invokeArgs);
		} catch (Exception e) {
			log.error("Failed to execute MCP Prompt '{}'", name, e);
			throw new RuntimeException("MCP Prompt execution failed: " + e.getMessage(), e);
		}
	}
}
