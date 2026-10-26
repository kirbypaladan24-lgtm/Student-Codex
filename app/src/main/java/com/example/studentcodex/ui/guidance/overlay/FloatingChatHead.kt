package com.example.studentcodex.ui.guidance.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.studentcodex.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Diameter of the floating bubble. */
private val HeadSize = 56.dp

/**
 * How much of the bubble hides off-screen while it is docked: 0.30 keeps
 * about 70% of the circle peeking on-screen, the tucked look of
 * Messenger-style chat heads.
 */
private const val TUCK_FRACTION = 0.30f

/** Diameter of the unread-replies badge. */
private val BadgeSize = 20.dp

/**
 * The Messenger-style floating guidance bubble, tucked into the screen edge
 * the way chat heads are: when released it glides to the NEAREST horizontal
 * edge and slides [TUCK_FRACTION] of the circle off-screen, leaving most of
 * the avatar peeking - the "pull me out" affordance.
 *
 * Behavior contract:
 *  * drag freely anywhere inside the safe drawing area (including fully
 *    onto the screen from the tucked position);
 *  * on release, glide to the nearest edge and tuck [TUCK_FRACTION] of the
 *    bubble off-screen (the "always stick to the side" rule);
 *  * a tap (no movement past the touch slop) opens the guidance chat panel;
 *  * while [unreadCount] is positive a red badge with the count shows on
 *    the visible top corner (unseen assistant replies).
 *
 * The docked position is reported to [onReleased] as a side plus a vertical
 * fraction, so it can be persisted size-independently. While a drag is in
 * flight the position is locally owned; the reported anchor only re-seeds
 * the position when the container size itself changes (rotation).
 */
@Composable
fun FloatingChatHead(
    containerSize: IntSize,
    anchor: OverlayAnchor,
    unreadCount: Int,
    onReleased: (OverlayAnchor) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val headSizePx = with(density) { HeadSize.roundToPx() }
    val tuckPx = headSizePx * TUCK_FRACTION
    val latestOnReleased by rememberUpdatedState(onReleased)
    val latestOnTap by rememberUpdatedState(onTap)

    /** Left x of the bubble when tucked on [side]. */
    fun dockedX(side: OverlaySide): Float =
        if (side == OverlaySide.LEFT) -tuckPx else (containerSize.width - headSizePx + tuckPx)

    fun clampPosition(value: Offset): Offset = Offset(
        x = value.x.coerceIn(dockedX(OverlaySide.LEFT), dockedX(OverlaySide.RIGHT)),
        y = value.y.coerceIn(0f, (containerSize.height - headSizePx).coerceAtLeast(0).toFloat()),
    )

    fun yFraction(y: Float): Float =
        if (containerSize.height > headSizePx) {
            (y / (containerSize.height - headSizePx)).coerceIn(0f, 1f)
        } else {
            0.5f
        }

    fun nearestSide(x: Float): OverlaySide =
        if (x + headSizePx / 2f < containerSize.width / 2f) OverlaySide.LEFT else OverlaySide.RIGHT

    // Keyed on the container size only: the anchor seeds the start position,
    // but once the user owns the bubble the position is local state, so a
    // release -> anchor update round-trip never stomps an in-flight glide.
    val position = remember(containerSize) {
        Animatable(
            initialValue = Offset(
                dockedX(anchor.side),
                anchor.yFraction * (containerSize.height - headSizePx).coerceAtLeast(0),
            ),
            typeConverter = Offset.VectorConverter,
        )
    }

    // Which half the bubble currently sits in: while tucked, the top corner
    // toward the screen middle is the one that stays on-screen, so that is
    // where the unread badge lives (and it flips once as a drag crosses the
    // center, following the edge the bubble will dock into when released).
    val inLeftHalf =
        position.value.x + headSizePx / 2f < containerSize.width / 2f

    Box(
        modifier = modifier
            .size(HeadSize)
            .offset {
                IntOffset(
                    x = position.value.x.roundToInt(),
                    y = position.value.y.roundToInt(),
                )
            }
            // Docked at a screen edge, an inward drag is exactly the system
            // back-gesture shape; exclude the bubble from system gesture
            // interception so users can pull it off the edge (within the
            // OS's per-side exclusion quota - a 56dp bubble is far inside).
            .systemGestureExclusion()
            .pointerInput(containerSize) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // snapTo is suspend; the documented drag pattern is one
                        // short launch per drag event (the animatable's mutex
                        // keeps them ordered, each resolves immediately).
                        scope.launch { position.snapTo(clampPosition(position.value + dragAmount)) }
                    },
                    onDragEnd = {
                        val current = position.value
                        val side = nearestSide(current.x)
                        val target = Offset(dockedX(side), current.y)
                        latestOnReleased(OverlayAnchor(side, yFraction(current.y)))
                        scope.launch {
                            position.animateTo(
                                targetValue = target,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { latestOnTap() })
            },
    ) {
        // The chat head itself: avatar-style circle with a surface ring.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
            shadowElevation = 8.dp,
            modifier = Modifier.size(HeadSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = stringResource(R.string.guidance_overlay_head_cd),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Unseen-reply badge on the top corner that stays on-screen while the
        // bubble is tucked (the inner corner, toward the screen middle).
        if (unreadCount > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(if (inLeftHalf) Alignment.TopEnd else Alignment.TopStart)
                    .offset(x = if (inLeftHalf) 4.dp else (-4).dp, y = (-4).dp)
                    .size(BadgeSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
