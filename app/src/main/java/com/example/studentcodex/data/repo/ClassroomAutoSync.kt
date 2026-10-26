package com.example.studentcodex.data.repo

import android.app.Activity
import com.example.studentcodex.data.auth.ClassroomAuthorizationOutcome
import com.example.studentcodex.data.auth.ClassroomAuthorizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The tiny slice of persistence the Classroom auto-sync needs, as an owned
 * seam (same pattern as the overlay store): local unit tests run on the
 * JVM, where the russhwolf Settings android actual cannot be constructed,
 * so the syncer never touches that type directly. Production wires
 * [RusshwolfClassroomAutoSyncStore]; tests use an in-memory fake.
 */
interface ClassroomAutoSyncStore {
    /** True when the student turned "Auto-sync school works" on. */
    fun isEnabled(): Boolean
    fun setEnabled(value: Boolean)
}

/** Production [ClassroomAutoSyncStore]: SharedPreferences via multiplatform-settings. */
class RusshwolfClassroomAutoSyncStore(
    private val settings: com.russhwolf.settings.Settings,
) : ClassroomAutoSyncStore {
    override fun isEnabled(): Boolean = settings.getBoolean(KEY_ENABLED, false)
    override fun setEnabled(value: Boolean) = settings.putBoolean(KEY_ENABLED, value)

    private companion object {
        private const val KEY_ENABLED = "classroomAutoSyncEnabled"
    }
}

/**
 * Runs the Google Classroom import AUTOMATICALLY in the background when
 * the student enabled the toggle: on EVERY app open / return from
 * background, and once right when the toggle is flipped on. There is no
 * throttle window: the sync is the SAME idempotent Edge Function the
 * manual "Sync now" button runs (upsert by source key), so an extra run
 * always converges the data instead of duplicating it.
 *
 * The token request is SILENT-only: an account that already granted the
 * scopes gets a token without any UI, and an account that would need the
 * consent sheet (revoked, expired, new device) is simply skipped —
 * auto-sync must never interrupt the student with a Google dialog.
 * Failures stay quiet too: the next app open retries.
 *
 * The imported rows surface through the Beacon on-resume refresh and the
 * connection card "last sync" note.
 */
class ClassroomAutoSyncer(
    private val store: ClassroomAutoSyncStore,
    private val connectionRepository: ClassroomConnectionRepository,
    private val syncRepository: ClassroomSyncRepository,
    private val authorizer: ClassroomAuthorizer,
) {

    /** Guards against overlapping runs (toggle-on + ON_START racing). */
    private val running = AtomicBoolean(false)

    /**
     * One background import attempt. Returns the sync summary when a run
     * actually happened, null when it was skipped (toggle off, not
     * connected, a run already in flight, or Google needs interactive
     * consent). Never throws.
     */
    suspend fun runIfDue(activity: Activity?): ClassroomSyncResult? {
        if (!store.isEnabled()) return null
        // Only a Connected account has anything to import; a student who
        // connects later gets a fresh sync on the very next app open.
        val state = runCatching { connectionRepository.getConnectionState() }.getOrNull()
        if (state !is ClassroomConnectionState.Connected) return null
        if (!running.compareAndSet(false, true)) return null
        return try {
            val token = (runCatching { authorizer.fetchSilentAccessToken(activity) }.getOrNull()
                as? ClassroomAuthorizationOutcome.Token)?.accessToken
                ?: return null
            runCatching { syncRepository.sync(token) }.getOrNull()
        } finally {
            running.set(false)
        }
    }
}
