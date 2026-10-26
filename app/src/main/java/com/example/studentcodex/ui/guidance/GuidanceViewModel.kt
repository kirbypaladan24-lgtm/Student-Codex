package com.example.studentcodex.ui.guidance

import androidx.lifecycle.ViewModel
import com.example.studentcodex.domain.guidance.GuidanceEngine
import com.example.studentcodex.domain.model.GuidanceMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * The guidance conversation. The engine is synchronous and deterministic -
 * there is deliberately no loading state between question and answer.
 */
class GuidanceViewModel(
    private val engine: GuidanceEngine,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<GuidanceMessage>>(emptyList())
    val messages: StateFlow<List<GuidanceMessage>> = _messages.asStateFlow()

    private val _unreadReplies = MutableStateFlow(0)

    /**
     * Assistant replies the user has not seen yet - the chat head shows
     * them as a red badge. A reply counts as seen once the full Guidance
     * page is on screen or the floating panel is open when it arrives.
     */
    val unreadReplies: StateFlow<Int> = _unreadReplies.asStateFlow()

    val suggestedTopics: List<String> get() = engine.suggestedTopics

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val answer = engine.respond(trimmed)
        _messages.value = _messages.value + listOf(
            GuidanceMessage(id = UUID.randomUUID().toString(), isFromUser = true, text = trimmed),
            GuidanceMessage(
                id = UUID.randomUUID().toString(),
                isFromUser = false,
                text = answer.text,
                suggestions = answer.suggestions,
            ),
        )
        _unreadReplies.value += 1
    }

    /** Marks every reply as seen (badge clears). */
    fun markSeen() {
        _unreadReplies.value = 0
    }

    /** Suggestion chips send their label as a normal question. */
    fun sendSuggestion(topic: String) = send(topic)

    /**
     * Empties the transcript. Invoked when the signed-in session goes away:
     * the shared conversation (page + floating overlay) must not outlive
     * the account that asked the questions.
     */
    fun clearConversation() {
        _messages.value = emptyList()
        _unreadReplies.value = 0
    }
}
