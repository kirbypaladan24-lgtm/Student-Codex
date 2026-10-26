package com.example.studentcodex.ui.guidance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The floating assistant's presentation state: mode choice, panel
 * open/closed, and docked positions, all persisted through an in-memory
 * [OverlayStore] fake (local unit tests run on the JVM, where the russhwolf
 * android Settings actual cannot be constructed).
 */
class GuidanceOverlayControllerTest {

    /** Plain map-backed store: captures exactly what gets persisted. */
    private class FakeOverlayStore : OverlayStore {
        val strings = mutableMapOf<String, String>()
        val floats = mutableMapOf<String, Float>()

        override fun getString(key: String, defaultValue: String): String =
            strings[key] ?: defaultValue

        override fun getFloat(key: String, defaultValue: Float): Float =
            floats[key] ?: defaultValue

        override fun putString(key: String, value: String) {
            strings[key] = value
        }

        override fun putFloat(key: String, value: Float) {
            floats[key] = value
        }
    }

    @Test
    fun `defaults to the chat head mode with docked anchors and a closed panel`() {
        val controller = GuidanceOverlayController(FakeOverlayStore())

        assertEquals(GuidanceOverlayMode.CHAT_HEAD, controller.mode.value)
        assertFalse(controller.expanded.value)
        assertEquals(OverlaySide.RIGHT, controller.headAnchor.value.side)
        assertEquals(0.40f, controller.headAnchor.value.yFraction, 0.001f)
        assertEquals(OverlaySide.RIGHT, controller.edgeAnchor.value.side)
        assertEquals(0.50f, controller.edgeAnchor.value.yFraction, 0.001f)
    }

    @Test
    fun `stored mode and anchors are restored on construction`() {
        val store = FakeOverlayStore()
        store.putString("guidance.overlay.mode", "EDGE_PANEL")
        store.putString("guidance.overlay.edge.side", "LEFT")
        store.putFloat("guidance.overlay.edge.y", 0.25f)

        val controller = GuidanceOverlayController(store)

        assertEquals(GuidanceOverlayMode.EDGE_PANEL, controller.mode.value)
        assertEquals(OverlaySide.LEFT, controller.edgeAnchor.value.side)
        assertEquals(0.25f, controller.edgeAnchor.value.yFraction, 0.001f)
        // The chat head anchor is untouched by edge-handle values.
        assertEquals(OverlaySide.RIGHT, controller.headAnchor.value.side)
    }

    @Test
    fun `corrupt or unknown stored mode falls back to the chat head`() {
        val store = FakeOverlayStore()
        store.putString("guidance.overlay.mode", "hologram")

        assertEquals(GuidanceOverlayMode.CHAT_HEAD, GuidanceOverlayController(store).mode.value)
    }

    @Test
    fun `stored y fractions outside the 0-1 range are clamped on read`() {
        val store = FakeOverlayStore()
        store.putFloat("guidance.overlay.head.y", 2.5f)

        assertEquals(1f, GuidanceOverlayController(store).headAnchor.value.yFraction, 0.001f)
    }

    @Test
    fun `selecting a mode persists it and collapses an open panel`() {
        val store = FakeOverlayStore()
        val controller = GuidanceOverlayController(store)
        controller.expand()

        controller.selectMode(GuidanceOverlayMode.EDGE_PANEL)

        assertEquals(GuidanceOverlayMode.EDGE_PANEL, controller.mode.value)
        assertFalse(controller.expanded.value)
        assertEquals("EDGE_PANEL", store.strings["guidance.overlay.mode"])
        // A controller built later on the same store sees the choice.
        assertEquals(GuidanceOverlayMode.EDGE_PANEL, GuidanceOverlayController(store).mode.value)
    }

    @Test
    fun `selecting the current mode is a no-op that keeps the panel open`() {
        val controller = GuidanceOverlayController(FakeOverlayStore())
        controller.expand()

        controller.selectMode(GuidanceOverlayMode.CHAT_HEAD)

        assertEquals(GuidanceOverlayMode.CHAT_HEAD, controller.mode.value)
        assertTrue(controller.expanded.value)
    }

    @Test
    fun `expand and collapse toggle the floating panel`() {
        val controller = GuidanceOverlayController(FakeOverlayStore())

        controller.expand()
        assertTrue(controller.expanded.value)

        controller.collapse()
        assertFalse(controller.expanded.value)
    }

    @Test
    fun `releasing the chat head docks and persists its anchor`() {
        val store = FakeOverlayStore()
        val controller = GuidanceOverlayController(store)

        controller.onHeadReleased(OverlayAnchor(OverlaySide.LEFT, 0.75f))

        assertEquals(OverlayAnchor(OverlaySide.LEFT, 0.75f), controller.headAnchor.value)
        assertEquals("LEFT", store.strings["guidance.overlay.head.side"])
        assertEquals(0.75f, store.floats["guidance.overlay.head.y"]!!, 0.001f)
        // The edge handle anchor is independent.
        assertEquals(OverlaySide.RIGHT, controller.edgeAnchor.value.side)
    }

    @Test
    fun `release fractions outside the 0-1 range are clamped before persisting`() {
        val store = FakeOverlayStore()
        val controller = GuidanceOverlayController(store)

        controller.onEdgeReleased(OverlayAnchor(OverlaySide.LEFT, -0.5f))
        assertEquals(0f, controller.edgeAnchor.value.yFraction, 0.001f)
        assertEquals(0f, store.floats["guidance.overlay.edge.y"]!!, 0.001f)

        controller.onHeadReleased(OverlayAnchor(OverlaySide.RIGHT, 1.5f))
        assertEquals(1f, controller.headAnchor.value.yFraction, 0.001f)
        assertEquals(1f, store.floats["guidance.overlay.head.y"]!!, 0.001f)
    }

    @Test
    fun `sanitized keeps the side and clamps only the fraction`() {
        assertEquals(
            OverlayAnchor(OverlaySide.RIGHT, 1f),
            OverlayAnchor(OverlaySide.RIGHT, 42f).sanitized(),
        )
        assertEquals(
            OverlayAnchor(OverlaySide.LEFT, 0f),
            OverlayAnchor(OverlaySide.LEFT, -7f).sanitized(),
        )
    }
}
