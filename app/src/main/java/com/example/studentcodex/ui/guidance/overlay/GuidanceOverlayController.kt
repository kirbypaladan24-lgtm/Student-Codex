package com.example.studentcodex.ui.guidance.overlay

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the floating guidance assistant is presented on the pages around the
 * app.
 *
 * CHAT_HEAD  - a Messenger-style draggable bubble that snaps to the nearest
 *              screen edge when released and opens a chat card when tapped.
 * EDGE_PANEL - a slim draggable handle half-tucked into a screen edge
 *              (like the Edge Panels on modern phones). Tapping it - or
 *              pulling it toward the middle of the screen - opens the
 *              SAME chat card the chat head opens, scaling out from that
 *              edge.
 */
enum class GuidanceOverlayMode {
    CHAT_HEAD,
    EDGE_PANEL,
}

/** Which horizontal screen edge a floating element is docked to. */
enum class OverlaySide {
    LEFT,
    RIGHT,
}

/**
 * Docked position of a floating element: the edge it sticks to plus its
 * vertical position as a fraction of the usable height (0f = top edge,
 * 1f = bottom edge). Fractions - not pixels - are persisted so the position
 * survives rotation and screen-size changes.
 */
data class OverlayAnchor(
    val side: OverlaySide,
    val yFraction: Float,
)

/** Defensive copy used on every write/read path; never trusts stored math. */
internal fun OverlayAnchor.sanitized(): OverlayAnchor =
    copy(yFraction = yFraction.coerceIn(0f, 1f))

/**
 * The tiny slice of persistence the overlay controller needs, as an owned
 * seam: local unit tests run on the JVM, where the russhwolf Settings
 * android actual cannot be constructed, so the controller never touches
 * that type directly. Production wires [RusshwolfOverlayStore]; tests use
 * an in-memory fake.
 */
interface OverlayStore {
    fun getString(key: String, defaultValue: String): String
    fun getFloat(key: String, defaultValue: Float): Float
    fun putString(key: String, value: String)
    fun putFloat(key: String, value: Float)
}

/** Production [OverlayStore]: backed by the app's multiplatform Settings. */
class RusshwolfOverlayStore(private val settings: com.russhwolf.settings.Settings) : OverlayStore {
    override fun getString(key: String, defaultValue: String): String =
        settings.getString(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        settings.getFloat(key, defaultValue)

    override fun putString(key: String, value: String) {
        settings.putString(key, value)
    }

    override fun putFloat(key: String, value: Float) {
        settings.putFloat(key, value)
    }
}

/**
 * Owns the floating guidance assistant's presentation state: the chosen
 * mode (chat head or edge panel), whether its chat panel is currently open,
 * and where each floating element is docked. Every change is persisted
 * immediately, so the user's style choice and docked positions survive
 * restarts. The conversation itself deliberately does NOT live here - the
 * page and the floating overlay share one GuidanceViewModel transcript.
 */
class GuidanceOverlayController(private val store: OverlayStore) : ViewModel() {

    private val _mode = MutableStateFlow(readMode())
    /** Which presentation style the user picked (set from the Guidance page). */
    val mode: StateFlow<GuidanceOverlayMode> = _mode.asStateFlow()

    private val _expanded = MutableStateFlow(false)
    /** Whether the floating assistant's chat panel is open. */
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    private val _headAnchor = MutableStateFlow(readAnchor(KEY_HEAD_SIDE, KEY_HEAD_Y, DEFAULT_HEAD_Y))
    /** Where the chat head bubble is docked. */
    val headAnchor: StateFlow<OverlayAnchor> = _headAnchor.asStateFlow()

    private val _edgeAnchor = MutableStateFlow(readAnchor(KEY_EDGE_SIDE, KEY_EDGE_Y, DEFAULT_EDGE_Y))
    /** Where the edge panel handle is docked. */
    val edgeAnchor: StateFlow<OverlayAnchor> = _edgeAnchor.asStateFlow()

    /**
     * Switches presentation style. Persisted immediately; an open panel
     * closes so it can re-open anchored to the newly picked floating entry
     * point (bubble or edge handle).
     */
    fun selectMode(mode: GuidanceOverlayMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        store.putString(KEY_MODE, mode.name)
        _expanded.value = false
    }

    /** Opens the floating assistant panel (tap on the bubble or handle). */
    fun expand() {
        _expanded.value = true
    }

    /** Closes the floating assistant panel (scrim tap, close button). */
    fun collapse() {
        _expanded.value = false
    }

    /** Called when a chat head drag ends: re-docks the bubble and persists. */
    fun onHeadReleased(anchor: OverlayAnchor) {
        val safe = anchor.sanitized()
        _headAnchor.value = safe
        writeAnchor(KEY_HEAD_SIDE, KEY_HEAD_Y, safe)
    }

    /** Called when the edge handle drag ends: re-docks the handle and persists. */
    fun onEdgeReleased(anchor: OverlayAnchor) {
        val safe = anchor.sanitized()
        _edgeAnchor.value = safe
        writeAnchor(KEY_EDGE_SIDE, KEY_EDGE_Y, safe)
    }

    private fun readMode(): GuidanceOverlayMode {
        val stored = store.getString(KEY_MODE, GuidanceOverlayMode.CHAT_HEAD.name)
        return GuidanceOverlayMode.entries.firstOrNull { it.name == stored }
            ?: GuidanceOverlayMode.CHAT_HEAD
    }

    private fun readAnchor(sideKey: String, yKey: String, defaultY: Float): OverlayAnchor = OverlayAnchor(
        side = if (store.getString(sideKey, OverlaySide.RIGHT.name) == OverlaySide.LEFT.name) {
            OverlaySide.LEFT
        } else {
            OverlaySide.RIGHT
        },
        yFraction = store.getFloat(yKey, defaultY).coerceIn(0f, 1f),
    ).sanitized()

    private fun writeAnchor(sideKey: String, yKey: String, anchor: OverlayAnchor) {
        store.putString(sideKey, anchor.side.name)
        store.putFloat(yKey, anchor.yFraction)
    }

    private companion object {
        const val KEY_MODE = "guidance.overlay.mode"
        const val KEY_HEAD_SIDE = "guidance.overlay.head.side"
        const val KEY_HEAD_Y = "guidance.overlay.head.y"
        const val KEY_EDGE_SIDE = "guidance.overlay.edge.side"
        const val KEY_EDGE_Y = "guidance.overlay.edge.y"

        /** Default dock: right edge, a touch above the vertical middle. */
        const val DEFAULT_HEAD_Y = 0.40f

        /** Default dock for the edge handle: right edge, vertical middle. */
        const val DEFAULT_EDGE_Y = 0.50f
    }
}
