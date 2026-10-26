package com.example.studentcodex.ui.guidance.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.studentcodex.R
import com.example.studentcodex.ui.guidance.GuidanceViewModel
import com.example.studentcodex.ui.guidance.MessageBubble
import com.example.studentcodex.ui.guidance.SuggestedTopics
import com.example.studentcodex.ui.components.EmptyState
import com.example.studentcodex.ui.theme.Spacing

/**
 * The guidance conversation as a floating panel: a compact header, the
 * shared transcript, starter topics while the conversation is short, and an
 * input row. Both floating styles (the chat-head card and the edge sheet)
 * render this exact content, driven by the SAME GuidanceViewModel the full
 * Guidance page uses - asking from a bubble continues the conversation from
 * the page, and vice versa.
 */
@Composable
fun GuidanceChatPanel(
    viewModel: GuidanceViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the newest message visible as the conversation grows.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val submitQuestion: () -> Unit = {
        if (input.isNotBlank()) {
            viewModel.send(input)
            input = ""
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, top = Spacing.sm, end = Spacing.xs, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = stringResource(R.string.guidance_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.guidance_overlay_panel_close_cd),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (messages.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.guidance_empty_title),
                message = stringResource(R.string.guidance_empty_message),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onSuggestion = viewModel::sendSuggestion,
                    )
                }
            }
        }

        if (messages.size <= 1) {
            SuggestedTopics(
                topics = viewModel.suggestedTopics,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                onTopicSelected = { topic ->
                    viewModel.sendSuggestion(topic)
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.guidance_input_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitQuestion() }),
            )
            Spacer(Modifier.width(Spacing.sm))
            FilledIconButton(
                onClick = submitQuestion,
                enabled = input.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(R.string.guidance_send_cd),
                )
            }
        }
    }
}
