package com.example.studentcodex.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.studentcodex.R
import com.example.studentcodex.domain.model.ActivityType
import com.example.studentcodex.ui.theme.Spacing

/**
 * Shared UI state components.
 *
 * Loading = skeleton screens that mirror the shape of the content that will
 * appear (never a bare centered spinner). Empty = a short actionable prompt.
 * Error = what went wrong plus a recovery action. Pulse/shimmer animation is
 * reserved exclusively for loading placeholders - content never animates by
 * itself.
 */

/* ------------------------------------------------------------------ */
/* Skeletons                                                           */
/* ------------------------------------------------------------------ */

/** Pulse alpha shared by every bone in a skeleton screen. */
@Composable
private fun boneAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "boneAlpha",
    )
    return alpha
}

/** One placeholder rectangle ("bone"). */
@Composable
private fun Bone(modifier: Modifier, height: Dp, shape: androidx.compose.ui.graphics.Shape) {
    val alpha = boneAlpha()
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)),
    )
}

/** Announced to accessibility services as "loading" and hides its noise. */
private fun Modifier.skeletonSemantics(description: String): Modifier =
    clearAndSetSemantics { contentDescription = description }

/**
 * Skeleton for activity lists (Home upcoming rows, Beacon grouped rows).
 * Mirrors the flat row anatomy: leading type-icon square, title + metadata
 * lines, trailing due column.
 */
@Composable
fun ActivityListSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 7,
) {
    val description = stringResource(R.string.state_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .skeletonSemantics(description),
    ) {
        repeat(rowCount) {
            ActivityRowSkeleton()
            if (it < rowCount - 1) {
                SkeletonRowDivider()
            }
        }
    }
}

@Composable
private fun ActivityRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bone(modifier = Modifier.size(20.dp), height = 20.dp, shape = MaterialTheme.shapes.extraSmall)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Bone(
                modifier = Modifier.fillMaxWidth(0.62f),
                height = 14.dp,
                shape = MaterialTheme.shapes.extraSmall,
            )
            Spacer(Modifier.height(Spacing.sm))
            Bone(
                modifier = Modifier.fillMaxWidth(0.38f),
                height = 12.dp,
                shape = MaterialTheme.shapes.extraSmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Bone(
                modifier = Modifier.width(56.dp),
                height = 12.dp,
                shape = MaterialTheme.shapes.extraSmall,
            )
            Spacer(Modifier.height(Spacing.sm))
            Bone(
                modifier = Modifier.width(36.dp),
                height = 10.dp,
                shape = MaterialTheme.shapes.extraSmall,
            )
        }
    }
}

@Composable
private fun SkeletonRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

/**
 * Skeleton for the community feed. Mirrors one post card: author mark +
 * name/time lines, two text lines, engagement footer.
 */
@Composable
fun PostListSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 4,
) {
    val description = stringResource(R.string.state_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .skeletonSemantics(description),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(rowCount) { PostCardSkeleton() }
    }
}

@Composable
private fun PostCardSkeleton() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Bone(modifier = Modifier.size(32.dp), height = 32.dp, shape = CircleShape)
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Bone(
                        modifier = Modifier.width(96.dp),
                        height = 12.dp,
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Bone(
                        modifier = Modifier.width(64.dp),
                        height = 10.dp,
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Bone(
                modifier = Modifier.fillMaxWidth(),
                height = 12.dp,
                shape = MaterialTheme.shapes.extraSmall,
            )
            Spacer(Modifier.height(Spacing.sm))
            Bone(
                modifier = Modifier.fillMaxWidth(0.72f),
                height = 12.dp,
                shape = MaterialTheme.shapes.extraSmall,
            )
            Spacer(Modifier.height(Spacing.md))
            Bone(
                modifier = Modifier.width(72.dp),
                height = 10.dp,
                shape = MaterialTheme.shapes.extraSmall,
            )
        }
    }
}

/**
 * Skeleton for the profile screen: header block (avatar, name, affiliation
 * lines) followed by grouped setting rows.
 */
@Composable
fun ProfileSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.state_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .skeletonSemantics(description),
    ) {
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Bone(modifier = Modifier.size(56.dp), height = 56.dp, shape = CircleShape)
            Spacer(Modifier.width(Spacing.lg))
            Column {
                Bone(
                    modifier = Modifier.width(140.dp),
                    height = 16.dp,
                    shape = MaterialTheme.shapes.extraSmall,
                )
                Spacer(Modifier.height(Spacing.sm))
                Bone(
                    modifier = Modifier.width(190.dp),
                    height = 12.dp,
                    shape = MaterialTheme.shapes.extraSmall,
                )
                Spacer(Modifier.height(Spacing.xs))
                Bone(
                    modifier = Modifier.width(110.dp),
                    height = 10.dp,
                    shape = MaterialTheme.shapes.extraSmall,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        repeat(3) { section ->
            Bone(
                modifier = Modifier.width(120.dp),
                height = 14.dp,
                shape = MaterialTheme.shapes.extraSmall,
            )
            Spacer(Modifier.height(Spacing.md))
            repeat(if (section == 2) 1 else 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Bone(
                        modifier = Modifier.size(20.dp),
                        height = 20.dp,
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Bone(
                        modifier = Modifier.fillMaxWidth(0.5f),
                        height = 12.dp,
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/* ------------------------------------------------------------------ */
/* Empty / error                                                       */
/* ------------------------------------------------------------------ */

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.height(Spacing.md))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.state_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

/* ------------------------------------------------------------------ */
/* Small shared pieces                                                 */
/* ------------------------------------------------------------------ */

/** Small section header used across screens for consistent hierarchy. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = Spacing.sm),
    )
}

/**
 * Monochrome 20dp icon encoding an activity's type. Icons only appear where
 * they aid scanning; the color carries no meaning, so none is used.
 */
@Composable
fun ActivityTypeIcon(
    type: ActivityType,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = when (type) {
        ActivityType.ASSIGNMENT -> Icons.Rounded.Assignment
        ActivityType.QUIZ -> Icons.Rounded.Quiz
        ActivityType.EXAM -> Icons.Rounded.MenuBook
        ActivityType.PROJECT -> Icons.Rounded.Assignment
        ActivityType.ANNOUNCEMENT -> Icons.Rounded.Campaign
        ActivityType.OTHER -> Icons.Rounded.MenuBook
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(activityTypeLabelRes(type)),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(20.dp),
    )
}

private fun activityTypeLabelRes(type: ActivityType): Int = when (type) {
    ActivityType.ASSIGNMENT -> R.string.type_assignment
    ActivityType.QUIZ -> R.string.type_quiz
    ActivityType.EXAM -> R.string.type_exam
    ActivityType.PROJECT -> R.string.type_project
    ActivityType.ANNOUNCEMENT -> R.string.type_announcement
    ActivityType.OTHER -> R.string.type_other
}

/**
 * Deliberate author mark (not a name initial): a neutral person silhouette
 * for named authors and a "hidden" mark for anonymous ones, both inside a
 * quiet circular frame. Real photos replace the silhouette once profiles
 * ship (Phase 3); the frame and crop stay identical.
 */
@Composable
fun AuthorMark(
    isAnonymous: Boolean,
    authorName: String?,
    modifier: Modifier = Modifier,
    markSize: Dp = 32.dp,
) {
    val description = if (isAnonymous) {
        stringResource(R.string.avatar_anonymous_cd)
    } else {
        authorName?.let { stringResource(R.string.avatar_named_cd, it) }
    }
    Box(
        modifier = modifier
            .size(markSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isAnonymous) Icons.Rounded.VisibilityOff else Icons.Rounded.Person,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(markSize * 0.55f),
        )
    }
}

/** Renders "[n] minutes" effort estimates; hides itself when effort is unknown. */
@Composable
fun EffortLabel(effortMinutes: Int?, modifier: Modifier = Modifier) {
    if (effortMinutes == null) return
    Text(
        text = stringResource(R.string.effort_minutes, effortMinutes),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
