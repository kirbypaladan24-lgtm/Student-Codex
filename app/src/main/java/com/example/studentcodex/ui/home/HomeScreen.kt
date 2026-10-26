package com.example.studentcodex.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studentcodex.R
import com.example.studentcodex.di.AppContainer
import com.example.studentcodex.domain.model.Activity
import com.example.studentcodex.domain.model.ActivitySource
import com.example.studentcodex.domain.model.CommunityPost
import com.example.studentcodex.ui.components.ActivityListSkeleton
import com.example.studentcodex.ui.components.ClassroomBadge
import com.example.studentcodex.ui.components.ActivityTypeIcon
import com.example.studentcodex.ui.components.AuthorMark
import com.example.studentcodex.ui.components.EmptyState
import com.example.studentcodex.ui.components.ErrorState
import com.example.studentcodex.ui.components.SectionHeader
import com.example.studentcodex.ui.components.dueLabel
import com.example.studentcodex.ui.components.timeLabel
import com.example.studentcodex.ui.theme.Spacing
import java.time.Instant
import java.time.LocalTime

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenBeacon: () -> Unit,
    onOpenCommunity: () -> Unit,
    onAskGuidance: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel {
        HomeViewModel(container.activityRepository, container.communityRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        HomeUiState.Loading -> ActivityListSkeleton(modifier = Modifier.fillMaxSize())
        is HomeUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::retry)
        is HomeUiState.Content -> HomeContent(
            state = s,
            onOpenBeacon = onOpenBeacon,
            onOpenCommunity = onOpenCommunity,
            onAskGuidance = onAskGuidance,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    onOpenBeacon: () -> Unit,
    onOpenCommunity: () -> Unit,
    onAskGuidance: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xl,
        ),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = workloadSummary(state.dueSoonCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "upcoming-header") { SectionHeader(stringResource(R.string.home_section_upcoming)) }

        if (state.nextActivities.isEmpty()) {
            item(key = "upcoming-empty") {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
                )
            }
        } else {
            items(state.nextActivities.size, key = { state.nextActivities[it].id }) { index ->
                val activity = state.nextActivities[index]
                val last = index == state.nextActivities.lastIndex
                UpcomingActivityRow(activity, showDivider = !last)
            }
            item(key = "see-all") {
                TextButton(onClick = onOpenBeacon) {
                    Text(stringResource(R.string.action_all_activities))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        }

        item(key = "community-header") { SectionHeader(stringResource(R.string.home_section_community)) }

        if (state.recentPosts.isEmpty()) {
            item(key = "community-empty") {
                EmptyState(
                    title = stringResource(R.string.community_empty_title),
                    message = stringResource(R.string.community_empty_message),
                )
            }
        } else {
            items(state.recentPosts.size, key = { "post-${state.recentPosts[it].id}" }) { index ->
                val post = state.recentPosts[index]
                val last = index == state.recentPosts.lastIndex
                PostPreviewRow(post, onOpenCommunity = onOpenCommunity, showDivider = !last)
            }
        }

        item(key = "guidance-cta") {
            // Opens the floating guidance assistant's chat panel - the same
            // surface the chat head / edge handle opens.
            TextButton(onClick = onAskGuidance) {
                Text(stringResource(R.string.home_ask_guidance))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}

/**
 * A plain-language workload summary instead of a static greeting filler:
 * the count answers "how much is coming at me?" before any row is read.
 */
@Composable
private fun workloadSummary(dueSoonCount: Int): String =
    if (dueSoonCount == 0) {
        stringResource(R.string.home_subtitle_none)
    } else {
        pluralStringResource(R.plurals.home_subtitle_count, dueSoonCount, dueSoonCount)
    }

/**
 * Flat, dense deadline row (no card wrapper). Anatomy: monochrome type icon,
 * title + course/effort metadata, trailing due emphasis. Dividers separate
 * rows; nothing here is decorative.
 */
@Composable
private fun UpcomingActivityRow(activity: Activity, showDivider: Boolean) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityTypeIcon(type = activity.type)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaLine = buildList {
                    activity.course?.let { add(it.code) }
                    activity.effortMinutes?.let { add(stringResource(R.string.effort_minutes, it)) }
                }.joinToString(" · ")
                if (activity.source == ActivitySource.GOOGLE_CLASSROOM || metaLine.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Same green provenance chip as the Beacon rows: the
                        // home feed is where synced coursework shows up first.
                        if (activity.source == ActivitySource.GOOGLE_CLASSROOM) {
                            ClassroomBadge()
                            if (metaLine.isNotEmpty()) Spacer(Modifier.width(Spacing.xs))
                        }
                        if (metaLine.isNotEmpty()) {
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(Spacing.md))
            DueBlock(activity.dueAt)
        }
        if (showDivider) {
            ListDivider()
        }
    }
}

/** Trailing due emphasis: urgency label + exact local time. */
@Composable
private fun DueBlock(dueAt: Instant?) {
    if (dueAt == null) {
        Text(
            text = stringResource(R.string.no_deadline),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val overdue = !dueAt.isAfter(Instant.now())
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(min = 64.dp),
        ) {
            Text(
                text = dueLabel(dueAt),
                style = MaterialTheme.typography.labelMedium,
                color = if (overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
            )
            Text(
                text = timeLabel(dueAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Flat community post preview: author mark + name, two lines of text, and a
 * quiet engagement line. Tapping anywhere on the preview opens the feed.
 */
@Composable
private fun PostPreviewRow(
    post: CommunityPost,
    onOpenCommunity: () -> Unit,
    showDivider: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCommunity),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuthorMark(
                isAnonymous = post.isAnonymous,
                authorName = post.authorName.takeIf { !post.isAnonymous },
                markSize = 20.dp,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = if (post.isAnonymous) {
                    stringResource(R.string.anonymous_author)
                } else {
                    post.authorName
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = post.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = engagementLine(post),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
        )
        if (showDivider) {
            ListDivider()
        }
    }
}

@Composable
private fun engagementLine(post: CommunityPost): String =
    pluralStringResource(R.plurals.comment_count, post.commentCount, post.commentCount) +
        " · " +
        pluralStringResource(R.plurals.like_count, post.likeCount, post.likeCount)

/** Standard 1dp row separator used by every flat list in the app. */
@Composable
private fun ListDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> stringResource(R.string.greeting_morning)
        hour < 18 -> stringResource(R.string.greeting_afternoon)
        else -> stringResource(R.string.greeting_evening)
    }
}
