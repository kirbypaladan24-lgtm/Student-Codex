package com.example.studentcodex.ui.guidance.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/** Slim vertical edge handle, sized to stay grabbable but unobtrusive. */
private val HandleWidth = 30.dp
private val HandleHeight = 96.dp

/**
 * How much of the handle's width hides off-screen while it is docked: the
 * resting handle shows only half of itself, the way the edge-panel tabs on
 * modern phones peek from the screen edge.
 */
private const val HandleTuckFraction = 0.5f

/** How far the handle must be pulled toward the middle to fly the panel open. */
private val OpenPullThreshold = 48.dp

/**
 * The Edge Panel handle - a slim pill half-tucked into a screen edge: it
 * rests with 50% of its width peeking on-screen and the other half hidden
 * off-screen, like the Edge Panels on modern phones.
 *
 * Behavior contract:
 *  * drag freely; on release it glides to the NEAREST horizontal screen
 *    edge and re-tucks half of its width off-screen (like the chat head
 *    tucking into its edge) while keeping its vertical position;
 *  * a tap on the visible half opens the assistant chat card - the SAME
 *    card the chat head opens, for one consistent guidance surface;
 *  * pulling the handle toward the middle of the screen (past
 *    [OpenPullThreshold]) also opens the card, mirroring the native
 *    edge-panel pull-out gesture.
 */
@Composable
fun EdgePanelHandle(
    containerSize: IntSize,
    anchor: OverlayAnchor,
    onReleased: (OverlayAnchor) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val handleWidthPx = with(density) { HandleWidth.roundToPx() }
    val handleHeightPx = with(density) { HandleHeight.roundToPx() }
    val openThresholdPx = with(density) { OpenPullThreshold.toPx() }
    val latestOnReleased by rememberUpdatedState(onReleased)
    val latestOnOpen by rememberUpdatedState(onOpen)

    // Docked = half-tucked: only (1 - HandleTuckFraction) of the handle's
    // width peeks on-screen; the rest hides past the screen edge.
    fun dockedX(side: OverlaySide): Float =
        if (side == OverlaySide.LEFT) {
            -handleWidthPx * HandleTuckFraction
        } else {
            containerSize.width - handleWidthPx * (1f - HandleTuckFraction)
        }

    fun clampPosition(value: Offset): Offset {
        // The clamp spans from fully-tucked-left to fully-tucked-right so
        // the handle can glide into (and out of) its half-hidden rest.
        val minX = -handleWidthPx * HandleTuckFraction
        val maxX = (containerSize.width - handleWidthPx * (1f - HandleTuckFraction))
            .coerceAtLeast(minX)
        return Offset(
            x = value.x.coerceIn(minX, maxX),
            y = value.y.coerceIn(0f, (containerSize.height - handleHeightPx).coerceAtLeast(0).toFloat()),
        )
    }

    fun yFraction(y: Float): Float =
        if (containerSize.height > handleHeightPx) {
            (y / (containerSize.height - handleHeightPx)).coerceIn(0f, 1f)
        } else {
            0.5f
        }

    fun nearestSide(x: Float): OverlaySide =
        if (x + handleWidthPx / 2f < containerSize.width / 2f) OverlaySide.LEFT else OverlaySide.RIGHT

    // Same ownership rule as the chat head: the persisted anchor seeds the
    // start position; drags own it afterwards.
    val position = remember(containerSize) {
        Animatable(
            initialValue = Offset(
                dockedX(anchor.side),
                anchor.yFraction * (containerSize.height - handleHeightPx).coerceAtLeast(0),
            ),
            typeConverter = Offset.VectorConverter,
        )
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = position.value.x.roundToInt(),
                    y = position.value.y.roundToInt(),
                )
            }
            // The handle lives INSIDE the screen-edge gesture zone: exclude
            // it from system gesture interception so dragging and pulling it
            // toward the middle belong to the app, not to back/home.
            .systemGestureExclusion()
            .pointerInput(containerSize) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { position.snapTo(clampPosition(position.value + dragAmount)) }
                    },
                    onDragEnd = {
                        val current = position.value
                        val side = nearestSide(current.x)
                        val restX = dockedX(side)
                        // Positive when the handle has traveled toward the
                        // middle of the screen away from its resting edge.
                        val pulledTowardCenter =
                            if (side == OverlaySide.LEFT) current.x - restX else restX - current.x

                        if (pulledTowardCenter >= openThresholdPx) {
                            // Pull-out gesture: open the panel and quietly
                            // re-dock the handle behind it.
                            latestOnOpen()
                            latestOnReleased(OverlayAnchor(side, yFraction(current.y)))
                            scope.launch {
                                position.animateTo(
                                    targetValue = Offset(restX, current.y),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                        } else {
                            // Ordinary drag: dock to the nearest edge and stay.
                            latestOnReleased(OverlayAnchor(side, yFraction(current.y)))
                            scope.launch {
                                position.animateTo(
                                    targetValue = Offset(restX, current.y),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { latestOnOpen() })
            },
    ) {
        // The chevron points toward the middle of the screen, hinting at the
        // pull-out gesture; it recomputes from the live position so it stays
        // correct even mid-glide. It hugs the VISIBLE half of the pill - the
        // half nearest the middle of the screen - and is sized to fit
        // inside the tucked sliver, so it never clips off-screen.
        val handleSide = nearestSide(position.value.x)
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
            modifier = Modifier.size(width = HandleWidth, height = HandleHeight),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp),
                contentAlignment = if (handleSide == OverlaySide.LEFT) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                },
            ) {
                Icon(
                    imageVector = if (handleSide == OverlaySide.LEFT) {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    } else {
                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft
                    },
                    contentDescription = stringResource(R.string.guidance_overlay_edge_handle_cd),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

