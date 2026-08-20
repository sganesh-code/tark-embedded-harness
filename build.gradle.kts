plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.tark"
version = "0.1.0-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot dependencies used for building the library
	implementation("org.springframework.boot:spring-boot-autoconfigure")
	implementation("org.springframework.boot:spring-boot-starter-websocket")

	// Support compiling against reactive WebFlux and MCP server annotation packages
	compileOnly("org.springframework.boot:spring-boot-starter-webflux")
	compileOnly("org.springframework.ai:spring-ai-starter-mcp-server-webflux")

	// Spring AI ChatClient and Advisors (new in 2.0.0)
	implementation("org.springframework.ai:spring-ai-client-chat")

	// Spring AI Model abstractions (including ChatModel)
	implementation("org.springframework.ai:spring-ai-model")

	// Spring AI Model Context Protocol (MCP) integrations
	implementation("org.springframework.ai:spring-ai-mcp")

	// JSON support
	implementation("com.fasterxml.jackson.core:jackson-databind")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:2.0.0")
	}
}

// Configure as a library rather than an executable application
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	enabled = false
}

tasks.getByName<Jar>("jar") {
	enabled = true
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Xlint:deprecation")
}
