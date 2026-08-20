package com.tark.harness.engine.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptCommandParserTest {

	private final PromptCommandParser parser = new PromptCommandParser();

	@Test
	void isPromptCommandIsTrueForThePrefixedForm() {
		assertTrue(parser.isPromptCommand("/prompt explain_for_grade grade=10"));
	}

	@Test
	void isPromptCommandToleratesLeadingWhitespace() {
		assertTrue(parser.isPromptCommand("   /prompt explain_for_grade"));
	}

	@Test
	void isPromptCommandIsFalseForPlainText() {
		assertFalse(parser.isPromptCommand("What is the derivative of x^2?"));
	}

	@Test
	void isPromptCommandIsFalseForNullOrTextThatOnlyStartsWithThePrefixWord() {
		assertFalse(parser.isPromptCommand(null));
		assertFalse(parser.isPromptCommand("/promptsomethingelse"));
	}

	@Test
	void parsesNameOnlyCommand() {
		PromptCommand command = parser.parse("/prompt explain_for_grade");

		assertEquals("explain_for_grade", command.name());
		assertTrue(command.arguments().isEmpty());
	}

	@Test
	void parsesNameWithKeyValueArguments() {
		PromptCommand command = parser.parse("/prompt explain_for_grade grade=10 style=formal");

		assertEquals("explain_for_grade", command.name());
		assertEquals(Map.of("grade", "10", "style", "formal"), command.arguments());
	}

	@Test
	void valueContainingAnEqualsSignIsPreservedInFull() {
		PromptCommand command = parser.parse("/prompt calc expr=a=b+c");

		assertEquals("a=b+c", command.arguments().get("expr"));
	}

	@Test
	void tokensWithoutAnEqualsSignAreIgnored() {
		PromptCommand command = parser.parse("/prompt calc notanarg grade=10");

		assertEquals(Map.of("grade", "10"), command.arguments());
	}

	@Test
	void missingPromptNameThrows() {
		assertThrows(IllegalArgumentException.class, () -> parser.parse("/prompt "));
		assertThrows(IllegalArgumentException.class, () -> parser.parse("/prompt    "));
	}

	@Test
	void toleratesExtraWhitespace() {
		PromptCommand command = parser.parse("/prompt   explain_for_grade    grade=10   ");

		assertEquals("explain_for_grade", command.name());
		assertEquals("10", command.arguments().get("grade"));
	}
}
