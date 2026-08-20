plugins {
	`java-library`
	id("com.vanniktech.maven.publish") version "0.37.0"
}

// Namespace verified via GitHub account (sganesh-code) for Maven Central publishing.
// The Java package (com.tark.harness) is unrelated and stays as-is.
group = "io.github.sganesh-code"
version = "0.1.0-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

val springBootBom = "org.springframework.boot:spring-boot-dependencies:4.1.0"
val springAiBom = "org.springframework.ai:spring-ai-bom:2.0.0"

dependencies {
	// Import BOMs as native Gradle platforms (not io.spring.dependency-management) so that
	// resolved versions make it into the published POM / Gradle module metadata.
	implementation(platform(springBootBom))
	implementation(platform(springAiBom))
	compileOnly(platform(springBootBom))
	compileOnly(platform(springAiBom))

	// Autoconfiguration annotations only (ConditionalOnClass, etc.) — not part of the public API.
	implementation("org.springframework.boot:spring-boot-autoconfigure")

	// TarkWebSocketHandler/TarkWebSocketAutoConfiguration expose WebSocketHandler/WebSocketConfigurer
	// types in their public API, so consumers need this at compile time too.
	api("org.springframework.boot:spring-boot-starter-websocket")

	// Support compiling against reactive WebFlux and MCP server annotation packages
	compileOnly("org.springframework.boot:spring-boot-starter-webflux")
	compileOnly("org.springframework.ai:spring-ai-starter-mcp-server-webflux")

	// ChatClient/CallAdvisor/StreamAdvisor types appear in public advisor APIs.
	api("org.springframework.ai:spring-ai-client-chat")

	// ChatModel/ChatMemory types appear in EmbeddedAgentEngine's public API.
	api("org.springframework.ai:spring-ai-model")

	// Spring AI Model Context Protocol (MCP) integrations — internal use only.
	implementation("org.springframework.ai:spring-ai-mcp")

	// Jackson annotations (@JsonTypeInfo etc.) are used directly on the public, host-extensible
	// protocol types (AgentAction/ClientMessage/ServerMessage), so hosts need them too.
	api("com.fasterxml.jackson.core:jackson-annotations")

	// Jackson 3 databind: TarkProtocolExtension and TarkWebSocketHandler expose
	// ObjectMapper/NamedType in their public API.
	api("tools.jackson.core:jackson-databind")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Xlint:deprecation")
}

mavenPublishing {
	publishToMavenCentral()
	signAllPublications()

	coordinates(group.toString(), "tark-embedded-harness", version.toString())

	pom {
		name.set("Tark Embedded Harness")
		description.set(
			"A Spring Boot library that embeds a Spring AI-driven cognitive agent directly into a " +
				"host application, exposing a WebSocket endpoint for streaming agent turns, managing " +
				"conversation context, and letting the host register its own tools and protocol messages."
		)
		inceptionYear.set("2026")
		url.set("https://github.com/sganesh-code/tark-embedded-harness")

		licenses {
			license {
				name.set("MIT License")
				url.set("https://opensource.org/license/mit")
				distribution.set("https://opensource.org/license/mit")
			}
		}

		developers {
			developer {
				id.set("sganesh-code")
				name.set("Senthil Ganesh")
				url.set("https://github.com/sganesh-code")
			}
		}

		scm {
			url.set("https://github.com/sganesh-code/tark-embedded-harness")
			connection.set("scm:git:git://github.com/sganesh-code/tark-embedded-harness.git")
			developerConnection.set("scm:git:ssh://git@github.com/sganesh-code/tark-embedded-harness.git")
		}
	}
}
