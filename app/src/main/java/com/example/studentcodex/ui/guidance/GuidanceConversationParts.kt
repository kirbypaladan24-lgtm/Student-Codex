package com.example.studentcodex.ui.guidance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.studentcodex.R
import com.example.studentcodex.domain.model.GuidanceMessage
import com.example.studentcodex.ui.theme.Spacing

/**
 * Conversation pieces shared by the two guidance surfaces: the full
 * Guidance page and the floating assistant overlay. Both render the same
 * bubbles, follow-up suggestions, and starter-topic chips, and both are
 * driven by the same GuidanceViewModel transcript, so the assistant feels
 * like one continuous conversation regardless of where it is opened from.
 */

/**
 * Chat bubble: user turns in the accent, assistant turns on the single
 * raised-paper surface step (surfaceContainerLow - the same sheet-of-paper
 * elevation as community post cards). Compact 12/10dp padding keeps the
 * transcript scannable.
 */
@Composable
internal fun MessageBubble(
    message: GuidanceMessage,
    onSuggestion: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (message.isFromUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = if (message.isFromUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (message.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        message.suggestions.forEach { suggestion ->
                            SuggestionLink(suggestion, onSuggestion = onSuggestion)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tappable follow-up questions inside assistant replies. Tapping sends the
 * suggestion straight into the conversation, so the affordance is real
 * behavior rather than decorative colored text.
 */
@Composable
internal fun SuggestionLink(text: String, onSuggestion: (String) -> Unit) {
    Text(
        text = stringResource(R.string.guidance_suggestion_prefix, text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClickLabel = stringResource(R.string.guidance_suggestion_ask)) {
                onSuggestion(text)
            }
            .padding(vertical = Spacing.xs),
    )
}

/**
 * Starter-topic chips shown while the conversation is still short, so first
 * contact with the assistant never requires typing a question from scratch.
 */
@Composable
internal fun SuggestedTopics(
    topics: List<String>,
    modifier: Modifier = Modifier,
    onTopicSelected: (String) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.guidance_suggested_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        topics.forEach { topic ->
            AssistChip(onClick = { onTopicSelected(topic) }, label = { Text(topic) })
        }
    }
}
