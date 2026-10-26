package com.example.studentcodex.domain.guidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoGuidanceEngineTest {

    private val engine = DemoGuidanceEngine()

    @Test
    fun `matches activity beacon keywords with precedence over generic study advice`() {
        val answer = engine.respond("How does the activity beacon show my deadlines?")
        assertTrue(answer.text.contains("Activity Beacon"))
    }

    @Test
    fun `matches classroom connection questions`() {
        val answer = engine.respond("How do I connect Google Classroom?")
        assertTrue(answer.text.contains("read-only"))
    }

    @Test
    fun `normalization handles punctuation and case`() {
        val normalized = engine.respond("HOW!!! do   I... add-an-activity???")
        assertTrue(normalized.text.contains("Activity Beacon"))
    }

    @Test
    fun `unknown questions get an honest fallback, never an invented answer`() {
        val answer = engine.respond("What is the university refund policy for dropped courses?")
        assertTrue(answer.text.contains("do not have a reliable answer"))
        assertFalse(answer.text.contains("refund"))
    }

    @Test
    fun `empty input gets a starter prompt`() {
        val answer = engine.respond("   ")
        assertTrue(answer.suggestions.isNotEmpty())
    }

    @Test
    fun `suggested topics themselves match real rules`() {
        for (topic in engine.suggestedTopics) {
            val answer = engine.respond(topic)
            assertFalse(
                "Suggested topic should match a rule: $topic",
                answer.text.contains("do not have a reliable answer"),
            )
        }
    }

    @Test
    fun `answers are deterministic`() {
        val question = "study tips for exams"
        assertEquals(engine.respond(question).text, engine.respond(question).text)
    }
}
