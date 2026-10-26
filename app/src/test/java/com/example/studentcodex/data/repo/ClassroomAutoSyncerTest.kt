package com.example.studentcodex.data.repo

import android.app.Activity
import com.example.studentcodex.data.auth.ClassroomAuthorizationOutcome
import com.example.studentcodex.data.auth.ClassroomAuthorizer
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ClassroomAutoSyncer decision table: the background import must run
 * whenever the toggle is on, the registry says Connected, and Google
 * hands out a token WITHOUT any consent sheet — once per app open, no
 * throttle window. Everything else skips quietly and never throws.
 */
class ClassroomAutoSyncerTest {

    private class FakeStore : ClassroomAutoSyncStore {
        var enabledState = false
        override fun isEnabled(): Boolean = enabledState
        override fun setEnabled(value: Boolean) { enabledState = value }
    }

    private class FakeAuthorizer(var outcome: ClassroomAuthorizationOutcome) : ClassroomAuthorizer {
        var silentCalls = 0
        override suspend fun fetchAccessToken(activity: Activity?): ClassroomAuthorizationOutcome =
            error("interactive fetch is not part of auto-sync")
        override suspend fun fetchSilentAccessToken(activity: Activity?): ClassroomAuthorizationOutcome {
            silentCalls++
            return outcome
        }
    }

    private class FakeConnection(var state: ClassroomConnectionState) : ClassroomConnectionRepository {
        override suspend fun getConnectionState(): ClassroomConnectionState = state
        override suspend fun disconnect() = error("not used")
    }

    private class FakeSync : ClassroomSyncRepository {
        var calls = 0
        var lastToken: String? = null
        override suspend fun sync(googleAccessToken: String): ClassroomSyncResult {
            calls++
            lastToken = googleAccessToken
            return ClassroomSyncResult(status = "ok", imported = 2)
        }
    }

    private fun connected() = ClassroomConnectionState.Connected(
        googleEmail = "kirbypaladan24@gmail.com",
        lastSyncAt = Instant.parse("2026-10-25T08:00:00Z"),
        lastSyncStatus = "ok",
        lastSyncError = null,
    )

    @Test
    fun `runs a silent sync when enabled and connected`() = runTest {
        val store = FakeStore().apply { enabledState = true }
        val authorizer = FakeAuthorizer(ClassroomAuthorizationOutcome.Token("token-1"))
        val sync = FakeSync()
        val syncer = ClassroomAutoSyncer(store, FakeConnection(connected()), sync, authorizer)

        val result = syncer.runIfDue(activity = null)

        assertEquals(2, result?.imported)
        assertEquals(1, sync.calls)
        assertEquals("token-1", sync.lastToken)
    }

    @Test
    fun `does nothing while the toggle is off`() = runTest {
        val store = FakeStore().apply { enabledState = false }
        val authorizer = FakeAuthorizer(ClassroomAuthorizationOutcome.Token("token-1"))
        val sync = FakeSync()
        val syncer = ClassroomAutoSyncer(store, FakeConnection(connected()), sync, authorizer)

        assertNull(syncer.runIfDue(null))
        assertEquals(0, authorizer.silentCalls)
        assertEquals(0, sync.calls)
    }

    @Test
    fun `every app open syncs again - no throttle window`() = runTest {
        val store = FakeStore().apply { enabledState = true }
        val authorizer = FakeAuthorizer(ClassroomAuthorizationOutcome.Token("token-1"))
        val sync = FakeSync()
        val syncer = ClassroomAutoSyncer(store, FakeConnection(connected()), sync, authorizer)

        assertEquals(2, syncer.runIfDue(null)?.imported)
        // The very next app open (seconds later) runs again, not throttled.
        assertEquals(2, syncer.runIfDue(null)?.imported)
        assertEquals(2, sync.calls)
    }

    @Test
    fun `not connected skips quietly and connects later`() = runTest {
        val store = FakeStore().apply { enabledState = true }
        val authorizer = FakeAuthorizer(ClassroomAuthorizationOutcome.Token("token-1"))
        val sync = FakeSync()
        val never = ClassroomAutoSyncer(
            store, FakeConnection(ClassroomConnectionState.NeverConnected), sync, authorizer,
        )

        assertNull(never.runIfDue(null))
        assertEquals(0, sync.calls)

        // Connecting later still syncs immediately.
        val retry = ClassroomAutoSyncer(store, FakeConnection(connected()), sync, authorizer)
        assertEquals(2, retry.runIfDue(null)?.imported)
    }

    @Test
    fun `silent unavailable means no sync and no crash`() = runTest {
        val store = FakeStore().apply { enabledState = true }
        val authorizer = FakeAuthorizer(ClassroomAuthorizationOutcome.SilentUnavailable)
        val sync = FakeSync()
        val syncer = ClassroomAutoSyncer(store, FakeConnection(connected()), sync, authorizer)

        assertNull(syncer.runIfDue(null))
        assertEquals(1, authorizer.silentCalls)
        assertEquals(0, sync.calls)
    }

    @Test
    fun `a throwing sync is swallowed into a null result`() = runTest {
        val store = FakeStore().apply { enabledState = true }
        val authorizer = FakeAuthorizer(ClassroomAuthorizationOutcome.Token("token-1"))
        val flaky = object : ClassroomSyncRepository {
            override suspend fun sync(googleAccessToken: String): ClassroomSyncResult =
                error("network down")
        }
        val syncer = ClassroomAutoSyncer(store, FakeConnection(connected()), flaky, authorizer)

        assertNull(syncer.runIfDue(null))
        assertTrue(store.enabledState)
    }

    @Test
    fun `overlapping runs are guarded - only one sync at a time`() = runTest {
        val store = FakeStore().apply { enabledState = true }
        val authorizer = FakeAuthorizer(ClassroomAuthorizationOutcome.Token("token-1"))
        var calls = 0
        val slow = object : ClassroomSyncRepository {
            override suspend fun sync(googleAccessToken: String): ClassroomSyncResult {
                delay(1_000)
                calls++
                return ClassroomSyncResult(status = "ok", imported = 1)
            }
        }
        val syncer = ClassroomAutoSyncer(store, FakeConnection(connected()), slow, authorizer)

        val first = async { syncer.runIfDue(null) }
        val second = async { syncer.runIfDue(null) }

        assertEquals(1, first.await()?.imported)
        assertNull(second.await())
        assertEquals(1, calls)
    }
}
