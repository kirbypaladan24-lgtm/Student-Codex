package com.example.studentcodex.ui.guidance.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.studentcodex.ui.guidance.GuidanceViewModel

/**
 * The floating guidance assistant, mounted above every page of the main app.
 * Renders whichever style the user picked on the Settings page:
 *
 *  CHAT_HEAD  - a draggable Messenger-style bubble; tapping it opens the
 *               guidance chat card, scaling out from the bubble's docked
 *               corner.
 *  EDGE_PANEL - a draggable edge handle that rests half-tucked into its
 *               edge (50% of the handle peeks on-screen, the other half
 *               hides off-screen); tapping or pulling it toward the middle
 *               opens the SAME guidance chat card, scaling out from the
 *               handle's docked corner.
 *
 * One chat panel presentation for both styles - a consistent architecture:
 * whichever entry point the user taps (bubble, handle, or the Home page's
 * ask-a-question button), the identical card opens, driven by the shared
 * [viewModel] transcript.
 */
@Composable
fun GuidanceOverlay(
    controller: GuidanceOverlayController,
    viewModel: GuidanceViewModel,
    modifier: Modifier = Modifier,
) {
    val mode by controller.mode.collectAsStateWithLifecycle()
    val expanded by controller.expanded.collectAsStateWithLifecycle()
    val headAnchor by controller.headAnchor.collectAsStateWithLifecycle()
    val edgeAnchor by controller.edgeAnchor.collectAsStateWithLifecycle()
    val unreadReplies by viewModel.unreadReplies.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    // An open panel is a seen conversation: the chat head's red badge clears
    // as soon as the panel opens, and again whenever a reply lands while it
    // stays open.
    LaunchedEffect(expanded, messages.size) {
        if (expanded) viewModel.markSeen()
    }

    // Above every screen, but inside the safe drawing area: the bubble and
    // handle never dock under the status bar or the gesture bar, and the
    // inset-aware bounds (which include the IME) keep the panel's input row
    // above the keyboard.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)

        when (mode) {
            GuidanceOverlayMode.CHAT_HEAD -> {
                if (!expanded) {
                    FloatingChatHead(
                        containerSize = containerSize,
                        anchor = headAnchor,
                        unreadCount = unreadReplies,
                        onReleased = controller::onHeadReleased,
                        onTap = controller::expand,
                    )
                }
                ChatHeadPanel(
                    visible = expanded,
                    anchor = headAnchor,
                    onCollapse = controller::collapse,
                ) {
                    GuidanceChatPanel(
                        viewModel = viewModel,
                        onClose = controller::collapse,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            GuidanceOverlayMode.EDGE_PANEL -> {
                if (!expanded) {
                    EdgePanelHandle(
                        containerSize = containerSize,
                        anchor = edgeAnchor,
                        onReleased = controller::onEdgeReleased,
                        onOpen = controller::expand,
                    )
                }
                // The edge handle opens the SAME chat card the chat head
                // opens - one guidance panel, two entry styles.
                ChatHeadPanel(
                    visible = expanded,
                    anchor = edgeAnchor,
                    onCollapse = controller::collapse,
                ) {
                    GuidanceChatPanel(
                        viewModel = viewModel,
                        onClose = controller::collapse,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * The floating assistant's panel: a scrim plus a chat card centered over
 * the page. The card scales in from the corner where the entry element
 * (chat head bubble or edge handle) is docked, the way Messenger's
 * conversation grows out of its chat head.
 */
@Composable
private fun ChatHeadPanel(
    visible: Boolean,
    anchor: OverlayAnchor,
    onCollapse: () -> Unit,
    content: @Composable () -> Unit,
) {
    val latestOnCollapse by rememberUpdatedState(onCollapse)
    val origin = TransformOrigin(
        pivotFractionX = if (anchor.side == OverlaySide.LEFT) 0f else 1f,
        pivotFractionY = anchor.yFraction,
    )

    // One full-size Box hosts both the scrim and the centered card.
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim: tap anywhere outside the card to put it away.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { latestOnCollapse() })
                    },
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.55f,
                animationSpec = tween(220),
                transformOrigin = origin,
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.55f,
                animationSpec = tween(160),
                transformOrigin = origin,
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.72f),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxSize(),
            ) {
                content()
            }
        }
    }
}
