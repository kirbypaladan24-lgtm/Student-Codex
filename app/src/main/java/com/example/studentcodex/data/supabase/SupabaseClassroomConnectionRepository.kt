package com.example.studentcodex.data.supabase

import android.util.Log
import com.example.studentcodex.data.repo.ClassroomConnectionRepository
import com.example.studentcodex.data.repo.ClassroomConnectionState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

/**
 * The signed-in user's Google Classroom CONNECTION STATE (Phase 6-ready).
 *
 * This reads/writes only the `google_connections` STATE registry — tokens
 * never appear here or anywhere in the app: the plan (Phase 6) keeps OAuth
 * credentials exclusively in the Edge Function's server-side store. Until
 * the Classroom integration is enabled there is simply no row (or a
 * 'disconnected' one), which the UI renders as an honest "not connected
 * yet" state. The disconnect write is the only client path the database
 * allows (the guard trigger enforces it).
 */
class SupabaseClassroomConnectionRepository(
    private val client: SupabaseClient,
) : ClassroomConnectionRepository {

    private val auth get() = client.auth

    override suspend fun getConnectionState(): ClassroomConnectionState {
        val uid = auth.currentUserOrNull()?.id ?: return ClassroomConnectionState.NotSignedIn
        val body = client.postgrest.from("google_connections")
            .select(columns = Columns.list("status", "google_email", "last_sync_at", "last_sync_status", "last_sync_error")) {
                filter { eq("user_id", uid) }
            }
            .data
        val row = PostgrestJson.parseRows(body).firstOrNull()
            ?: return ClassroomConnectionState.NeverConnected
        return when (row.stringValue("status")) {
            "connected" -> ClassroomConnectionState.Connected(
                googleEmail = row.stringValue("google_email"),
                lastSyncAt = row.instantValue("last_sync_at"),
                lastSyncStatus = row.stringValue("last_sync_status"),
                lastSyncError = row.stringValue("last_sync_error"),
            )
            "revoked" -> ClassroomConnectionState.Revoked
            "expired" -> ClassroomConnectionState.Expired
            else -> ClassroomConnectionState.Disconnected
        }
    }

    override suspend fun disconnect() {
        val uid = auth.currentUserOrNull()?.id
            ?: error("Cannot disconnect while signed out")
        client.postgrest.from("google_connections")
            .update(
                buildJsonObject {
                    put("status", "disconnected")
                },
            ) {
                filter { eq("user_id", uid) }
            }
        Log.i(TAG, "Google Classroom connection marked disconnected for $uid")
    }

    private fun kotlinx.serialization.json.JsonObject.stringValue(key: String) =
        PostgrestJson.run { stringValue(key) }

    private fun kotlinx.serialization.json.JsonObject.instantValue(key: String) =
        PostgrestJson.run { instantValue(key) }

    private companion object {
        const val TAG = "ClassroomConnection"
    }
}
