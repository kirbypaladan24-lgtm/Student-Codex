package com.example.studentcodex.data.supabase

import android.util.Log
import com.example.studentcodex.data.repo.ProfileRepository
import com.example.studentcodex.domain.model.StudentProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonObject

/**
 * Supabase-backed profile repository. Reads the signed-in student's own
 * profiles row plus the joined program/campus/section labels (one query;
 * RLS restricts the join to rows the student may read anyway).
 *
 * Row parsing uses the runtime JSON API (see [PostgrestJson] KDoc).
 */
class SupabaseProfileRepository(
    private val client: SupabaseClient,
) : ProfileRepository {

    private val auth get() = client.auth

    override suspend fun getProfile(): StudentProfile {
        val uid = auth.currentUserOrNull()?.id
            ?: return StudentProfile(displayName = "", university = "", program = "", yearLevel = "")
        val body = client.postgrest.from("profiles")
            .select(
                columns = Columns.list(
                    "first_name", "last_name", "username", "year_level",
                    "program:programs(program_name)",
                    "campus:campuses(name)",
                    "year_section:year_sections(name)",
                ),
            ) {
                filter { eq("id", uid) }
            }
            .data
        val row = PostgrestJson.parseRows(body).firstOrNull()
            ?: return StudentProfile(displayName = "", university = "", program = "", yearLevel = "")
        val firstName = row.stringValue("first_name")
        val lastName = row.stringValue("last_name")
        val displayName = listOfNotNull(firstName, lastName).joinToString(" ")
            .ifBlank { row.stringValue("username") ?: "" }
        val program = (row.get("program") as? JsonObject)?.let { it.stringValue("program_name") }
        val campus = (row.get("campus") as? JsonObject)?.let { it.stringValue("name") }
        val section = (row.get("year_section") as? JsonObject)?.let { it.stringValue("name") }
        val yearLevel = row.intValue("year_level")
        val yearLabel = when (yearLevel) {
            1 -> "1st year"; 2 -> "2nd year"; 3 -> "3rd year"; 4 -> "4th year"; 5 -> "5th year"
            else -> null
        }
        Log.i(TAG, "Profile loaded for $uid")
        return StudentProfile(
            displayName = displayName,
            university = campus ?: "Partido State University",
            program = program ?: "",
            yearLevel = listOfNotNull(yearLabel, section).joinToString(" · "),
        )
    }

    private fun JsonObject.stringValue(key: String) = PostgrestJson.run { stringValue(key) }
    private fun JsonObject.intValue(key: String) = PostgrestJson.run { intValue(key) }

    private companion object {
        const val TAG = "SupabaseProfile"
    }
}
