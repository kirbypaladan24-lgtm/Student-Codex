package com.example.studentcodex.ui.guidance

import com.example.studentcodex.domain.guidance.DemoGuidanceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceViewModelTest {

    private val viewModel = GuidanceViewModel(DemoGuidanceEngine())

    @Test
    fun `cold start shows an empty conversation`() {
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `send appends the question and the engine's answer`() {
        viewModel.send("How do I add an activity?")

        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertEquals("How do I add an activity?", messages[0].text)
        assertTrue(messages[0].isFromUser)
        assertFalse(messages[1].isFromUser)
        assertTrue(messages[1].text.isNotBlank())
    }

    @Test
    fun `send ignores blank input`() {
        viewModel.send("   ")

        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `sendSuggestion routes its label through send`() {
        viewModel.sendSuggestion("Community rules")

        assertEquals(2, viewModel.messages.value.size)
        assertEquals("Community rules", viewModel.messages.value[0].text)
    }

    @Test
    fun `suggested topics come from the engine`() {
        assertTrue(viewModel.suggestedTopics.isNotEmpty())
    }

    @Test
    fun `clearConversation empties the transcript`() {
        viewModel.send("How does the Activity Beacon work?")
        viewModel.send("Study tips for exams")

        viewModel.clearConversation()

        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `each reply adds one unread until it is seen`() {
        assertEquals(0, viewModel.unreadReplies.value)

        viewModel.send("How do I add an activity?")
        viewModel.send("Study tips for exams")

        assertEquals(2, viewModel.unreadReplies.value)

        viewModel.markSeen()
        assertEquals(0, viewModel.unreadReplies.value)
    }

    @Test
    fun `markSeen before any conversation is a no-op`() {
        viewModel.markSeen()

        assertEquals(0, viewModel.unreadReplies.value)
    }

    @Test
    fun `clearConversation also clears unread replies`() {
        viewModel.send("How does the Activity Beacon work?")

        viewModel.clearConversation()

        assertEquals(0, viewModel.unreadReplies.value)
    }
}
