package com.tark.harness.autoconfigure;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import com.tark.harness.context.domain.TextCompletionModel;
import com.tark.harness.context.adapters.SpringAiTextCompletionModel;
import com.tark.harness.engine.application.CognitiveOrchestrator;
import com.tark.harness.engine.adapters.EmbeddedAgentEngine;
import com.tark.harness.engine.application.PreflightPlanner;
import com.tark.harness.engine.adapters.TarkPromptRegistry;
import com.tark.harness.websocket.adapters.TarkChatModelObservationHandler;
import com.tark.harness.websocket.adapters.TarkWebSocketHandler;
import com.tark.harness.websocket.protocol.ClientMessage;
import com.tark.harness.websocket.protocol.ServerMessage;
import com.tark.harness.websocket.protocol.AgentAction.*;
import com.tark.harness.websocket.protocol.TarkProtocolExtension;
import io.modelcontextprotocol.client.McpSyncClient;
import tools.jackson.databind.jsontype.NamedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Wires up the embedded Tark agent harness as a set of Spring beans: the engine, its
 * WebSocket endpoint, observability, and tool discovery (both local {@code @Tool}/{@code
 * @McpTool} methods on host beans and any registered remote MCP servers), plus the shared
 * Jackson mapper that carries the WebSocket protocol's polymorphic message types.
 */
@Configuration
public class TarkHarnessAutoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(TarkHarnessAutoConfiguration.class);

	/**
	 * The unified set of tools available to the agent, combining host-defined tools with any
	 * tools discovered from connected remote MCP servers.
	 */
	public record TarkToolRegistry(List<ToolCallback> tools) {}

	@Bean
	@ConditionalOnMissingBean
	public TarkHarnessProperties tarkHarnessProperties() {
		log.info("Auto-configuring TarkHarnessProperties bean with 'tark.harness' prefix...");
		return new TarkHarnessProperties();
	}

	@Bean
	@ConditionalOnMissingBean
	public TarkPromptRegistry tarkPromptRegistry(ApplicationContext context) {
		return new TarkPromptRegistry(context);
	}

	/**
	 * Pluggable chat memory persistence. Defaults to in-memory storage; host applications can
	 * override this bean (e.g. with a JDBC- or Redis-backed {@link ChatMemoryRepository}) to
	 * persist conversation state beyond the lifetime of the JVM process.
	 */
	@Bean
	@ConditionalOnMissingBean
	public ChatMemoryRepository tarkChatMemoryRepository() {
		log.info("Auto-configuring default in-memory ChatMemoryRepository. " +
				"Provide your own ChatMemoryRepository bean to override with a persistent store.");
		return new InMemoryChatMemoryRepository();
	}

	@Bean
	@ConditionalOnMissingBean
	public EmbeddedAgentEngine embeddedAgentEngine(
			ChatModel chatModel,
			TarkHarnessProperties properties,
			TarkPromptRegistry promptRegistry,
			ChatMemoryRepository chatMemoryRepository,
			ObjectMapper objectMapper) {
		log.info("Auto-configuring EmbeddedAgentEngine with target ChatModel...");
		return new EmbeddedAgentEngine(chatModel, properties, promptRegistry, chatMemoryRepository, objectMapper);
	}

	@Bean
	@ConditionalOnMissingBean
	public TarkChatModelObservationHandler tarkChatModelObservationHandler(
			ApplicationEventPublisher eventPublisher,
			TarkHarnessProperties properties) {
		log.info("Auto-configuring TarkChatModelObservationHandler for Micrometer telemetry...");
		com.tark.harness.websocket.protocol.ObservabilityConfig config =
				new com.tark.harness.websocket.protocol.ObservabilityConfig(
						properties.getObservability().isEnabled(),
						properties.getObservability().getBroadcastCategories()
				);
		return new TarkChatModelObservationHandler(eventPublisher, config);
	}

	@Bean
	@ConditionalOnMissingBean
	public CognitiveOrchestrator cognitiveOrchestrator(ChatModel chatModel, EmbeddedAgentEngine embeddedAgentEngine) {
		log.info("Auto-configuring CognitiveOrchestrator with pre-flight planning and verifications...");
		TextCompletionModel completionModel = new SpringAiTextCompletionModel(chatModel);
		return new CognitiveOrchestrator(new PreflightPlanner(completionModel), embeddedAgentEngine);
	}

	@Bean
	@ConditionalOnMissingBean
	public TarkWebSocketHandler tarkWebSocketHandler(
			CognitiveOrchestrator orchestrator,
			EmbeddedAgentEngine embeddedAgentEngine,
			TarkToolRegistry toolRegistry,
			ObjectMapper objectMapper) {
		log.info("Auto-configuring TarkWebSocketHandler with {} available tools...", toolRegistry.tools().size());
		return new TarkWebSocketHandler(orchestrator, embeddedAgentEngine, toolRegistry.tools(), objectMapper);
	}

	@Bean
	public TarkToolRegistry tarkToolRegistry(
			ApplicationContext context,
			ObjectProvider<List<McpSyncClient>> mcpClientsProvider) {

		List<ToolCallback> list = new ArrayList<>();
		list.addAll(scanLocalTools(context));

		List<McpSyncClient> mcpClients = mcpClientsProvider.getIfAvailable();
		if (mcpClients != null && !mcpClients.isEmpty()) {
			log.info("Discovered {} registered McpSyncClient beans. Adapting remote tools natively...", mcpClients.size());
			try {
				SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
						.mcpClients(mcpClients.toArray(McpSyncClient[]::new))
						.build();

				list.addAll(List.of(provider.getToolCallbacks()));
			} catch (Exception e) {
				log.error("Failed to adapt discovered MCP Clients to ToolCallbacks", e);
			}
		}

		log.info("Successfully registered a total of {} unified ToolCallbacks in TarkToolRegistry.", list.size());
		return new TarkToolRegistry(list);
	}

	private List<ToolCallback> scanLocalTools(ApplicationContext context) {
		List<ToolCallback> list = new ArrayList<>();
		String[] beanNames = context.getBeanDefinitionNames();

		log.info("Scanning host ApplicationContext for local @McpTool and @Tool methods...");
		for (String beanName : beanNames) {
			try {
				Object bean = context.getBean(beanName);
				Class<?> targetClass = org.springframework.util.ClassUtils.getUserClass(bean);
				Method[] methods = targetClass.getMethods();

				for (Method method : methods) {
					org.springframework.ai.mcp.annotation.McpTool mcpTool = method.getAnnotation(org.springframework.ai.mcp.annotation.McpTool.class);
					if (mcpTool != null) {
						String name = mcpTool.name().isEmpty() ? method.getName() : mcpTool.name();
						String desc = mcpTool.description();
						log.info("Discovered local @McpTool '{}' in bean '{}'. Adapting natively...", name, beanName);

						ToolCallback callback = org.springframework.ai.tool.method.MethodToolCallback.builder()
								.toolDefinition(org.springframework.ai.tool.support.ToolDefinitions.builder(method)
										.name(name)
										.description(desc)
										.build())
								.toolMethod(method)
								.toolObject(bean)
								.build();
						list.add(callback);
						continue;
					}

					org.springframework.ai.tool.annotation.Tool tool = method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
					if (tool != null) {
						String name = tool.name().isEmpty() ? method.getName() : tool.name();
						String desc = tool.description();
						log.info("Discovered local @Tool '{}' in bean '{}'. Adapting natively...", name, beanName);

						ToolCallback callback = org.springframework.ai.tool.method.MethodToolCallback.builder()
								.toolDefinition(org.springframework.ai.tool.support.ToolDefinitions.builder(method)
										.name(name)
										.description(desc)
										.build())
								.toolMethod(method)
								.toolObject(bean)
								.build();
						list.add(callback);
					}
				}
			} catch (Exception e) {
			}
		}

		return list;
	}

	/**
	 * Registers the WebSocket protocol's polymorphic message types (and any types host
	 * applications add via {@link TarkProtocolExtension}) on the shared Jackson mapper.
	 */
	@Bean
	public JsonMapperBuilderCustomizer tarkProtocolJacksonCustomizer(List<TarkProtocolExtension> extensions) {
		log.info("Configuring Jackson 3 Polymorphic Customizers. Extensions count: {}", extensions.size());
		return builder -> {
			builder.configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false);

			builder.registerSubtypes(
					ClientMessage.PromptMessage.class,
					ClientMessage.CancelMessage.class,
					ClientMessage.ConfigUpdateMessage.class,
					ClientMessage.McpToolToggleMessage.class,
					ClientMessage.CleanupMemoryMessage.class,
					ClientMessage.ChoiceResponseMessage.class,
					ClientMessage.InputResponseMessage.class
			);

			builder.registerSubtypes(
					ServerMessage.ActionMessage.class,
					ServerMessage.UsageMessage.class,
					ServerMessage.StateMessage.class
			);

			builder.registerSubtypes(
					StatusAction.class,
					DeltaAction.class,
					EndAction.class,
					ToolStartAction.class,
					ToolOutputAction.class,
					ToolEndAction.class,
					RequestChoiceAction.class,
					RequestInputAction.class,
					SystemAction.class,
					ClearAction.class,
					ExitAction.class
			);

			for (TarkProtocolExtension ext : extensions) {
				log.info("Registering custom host protocol extension: {}", ext.getClass().getSimpleName());
				builder.registerSubtypes(ext.registerClientMessages().toArray(new NamedType[0]));
				builder.registerSubtypes(ext.registerServerMessages().toArray(new NamedType[0]));
				builder.registerSubtypes(ext.registerAgentActions().toArray(new NamedType[0]));
			}
		};
	}
}
