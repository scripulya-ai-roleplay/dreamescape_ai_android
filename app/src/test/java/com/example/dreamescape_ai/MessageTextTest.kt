package com.example.dreamescape_ai

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTextTest {

    @Test
    fun `streamingDisplayText returns plain prose verbatim`() {
        assertEquals("He looked at you", streamingDisplayText("He looked at you"))
    }

    @Test
    fun `streamingDisplayText hides partial fence scaffolding`() {
        // Only the opening code fence has streamed so far — show nothing.
        assertEquals("", streamingDisplayText("```jso"))
    }

    @Test
    fun `streamingDisplayText hides envelope before the text value begins`() {
        assertEquals("", streamingDisplayText("{\n    "))
        assertEquals("", streamingDisplayText("""{"text":"""))
        assertEquals("", streamingDisplayText("""{"text": """))
    }

    @Test
    fun `streamingDisplayText unwraps the text value incrementally`() {
        // Mid-stream: the inner value has begun but the closing quote hasn't arrived.
        val partial = "```json\n{\n  \"text\": \"He looked"
        assertEquals("He looked", streamingDisplayText(partial))

        val more = "```json\n{\n  \"text\": \"He looked at you"
        assertEquals("He looked at you", streamingDisplayText(more))
    }

    @Test
    fun `streamingDisplayText stops at the closing quote ignoring trailing brace`() {
        // The JSON is complete; the trailing `"` and `}` must not leak into the bubble.
        val complete = "```json\n{\n  \"text\": \"He looked at you\"\n}\n```"
        assertEquals("He looked at you", streamingDisplayText(complete))
    }

    @Test
    fun `streamingDisplayText unescapes embedded quotes while streaming`() {
        // Escaped quotes arrive as \" in the token stream; render them as plain quotes.
        val withEscapes = """{"text": "She said \"hi\" and"}"""
        assertEquals("She said \"hi\" and", streamingDisplayText(withEscapes))
    }

    @Test
    fun `streamingDisplayText handles escaped newlines`() {
        val withNewline = "{\"text\": \"line one\\nline two"
        assertEquals("line one\nline two", streamingDisplayText(withNewline))
    }

    @Test
    fun `streamingDisplayText unwraps an unfenced envelope`() {
        assertEquals("hi", streamingDisplayText("""{"text": "hi"}"""))
    }

    @Test
    fun `streamingDisplayText is empty for an empty buffer`() {
        assertEquals("", streamingDisplayText(""))
    }

    @Test
    fun `extractModelMessageText unwraps a complete persisted envelope`() {
        val persisted = "```json\n{\n  \"text\": \"Final reply\"\n}\n```"
        assertEquals("Final reply", extractModelMessageText(persisted))
    }

    @Test
    fun `substitutePlaceholders replaces user with persona name`() {
        assertEquals("Hello, Kael!", substitutePlaceholders("Hello, {{user}}!", "Kael"))
    }

    @Test
    fun `substitutePlaceholders defaults to You without a persona`() {
        assertEquals("Hello, You!", substitutePlaceholders("Hello, {{user}}!", null))
    }

    @Test
    fun `substitutePlaceholders blank persona falls back to You`() {
        assertEquals("You", substitutePlaceholders("{{user}}", "   "))
    }

    @Test
    fun `substitutePlaceholders replaces char with char name`() {
        assertEquals(
            "Aria greets Kael.",
            substitutePlaceholders("{{char}} greets {{user}}.", "Kael", "Aria")
        )
    }

    @Test
    fun `substitutePlaceholders char falls back to user`() {
        assertEquals("Kael nods.", substitutePlaceholders("{{char}} nods.", "Kael"))
    }

    @Test
    fun `substitutePlaceholders is case-insensitive and whitespace-tolerant`() {
        assertEquals("Kael and Aria", substitutePlaceholders("{{User}} and {{ char }}", "Kael", "Aria"))
    }

    @Test
    fun `substitutePlaceholders leaves plain text unchanged`() {
        assertEquals("Just prose.", substitutePlaceholders("Just prose.", "Kael"))
    }

    @Test
    fun `substitutePlaceholders replaces repeated occurrences`() {
        assertEquals("Kael said Kael would go.", substitutePlaceholders("{{user}} said {{user}} would go.", "Kael"))
    }
}
