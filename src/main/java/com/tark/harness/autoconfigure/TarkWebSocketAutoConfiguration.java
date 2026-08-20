package com.tark.harness.autoconfigure;

import tools.jackson.databind.ObjectMapper;
import com.tark.harness.websocket.adapters.TarkWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Auto-configuration for the Tark WebSocket Server endpoint.
 * Binds the path, enables Cross-Origin connection matching,
 * and registers the TarkWebSocketHandler bean based on TarkHarnessProperties.
 */
@Configuration
@ConditionalOnClass({WebSocketConfigurer.class, ObjectMapper.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@AutoConfigureAfter(TarkHarnessAutoConfiguration.class)
@EnableWebSocket
public class TarkWebSocketAutoConfiguration implements WebSocketConfigurer {

	private static final Logger log = LoggerFactory.getLogger(TarkWebSocketAutoConfiguration.class);

	private final TarkWebSocketHandler webSocketHandler;
	private final TarkHarnessProperties properties;

	public TarkWebSocketAutoConfiguration(@Lazy TarkWebSocketHandler webSocketHandler, TarkHarnessProperties properties) {
		this.webSocketHandler = webSocketHandler;
		this.properties = properties;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		String path = properties.getWebsocketPath();
		String[] origins = properties.getAllowedOrigins().toArray(new String[0]);

		log.info("Registering Embedded Tark WebSocket Handler under route '{}' with allowed origins: {}", path, properties.getAllowedOrigins());
		registry.addHandler(webSocketHandler, path)
				.setAllowedOrigins(origins);
	}
}
