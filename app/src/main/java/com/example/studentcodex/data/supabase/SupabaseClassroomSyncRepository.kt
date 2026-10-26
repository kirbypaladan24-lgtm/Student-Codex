package com.example.studentcodex.data.supabase

import android.util.Log
import com.example.studentcodex.data.repo.ClassroomSyncRepository
import com.example.studentcodex.data.repo.ClassroomSyncResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.append
import io.ktor.http.headers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Google Classroom sync through the `classroom-sync` Edge Function.
 *
 * The session JWT travels in the Authorization header automatically
 * (functions-kt); the Google access token rides in the request body. Both
 * are used in transit only — the function pulls the user's classes,
 * matches them to their sections by course code, and upserts the
 * coursework/announcements as 'imported' activities (idempotent by
 * source_key). See supabase/functions/classroom-sync/index.ts.
 */
class SupabaseClassroomSyncRepository(
    private val client: SupabaseClient,
) : ClassroomSyncRepository {

    override suspend fun sync(googleAccessToken: String): ClassroomSyncResult = try {
        val response: HttpResponse = client.functions.invoke(
            function = FUNCTION,
            body = buildJsonObject {
                put("googleAccessToken", googleAccessToken)
            },
            headers = headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            },
        )
        val text = response.bodyAsText()
        Log.i(TAG, "classroom-sync response: " + text.take(600))
        parseSummary(text)
    } catch (e: Exception) {
        Log.w(TAG, "classroom-sync invocation failed", e)
        ClassroomSyncResult(status = "failed", error = e.message ?: e.javaClass.simpleName)
    }

    /** Maps the function's JSON summary into the domain result. */
    private fun parseSummary(text: String): ClassroomSyncResult = runCatching {
        val obj = Json.parseToJsonElement(text).jsonObject

        fun intAt(key: String): Int =
            obj[key]?.jsonPrimitive?.int ?: 0

        fun nameListAt(key: String): List<String> =
            obj[key]?.jsonArray
                ?.mapNotNull { entry ->
                    runCatching {
                        entry.jsonObject["classroomName"]?.jsonPrimitive?.content
                    }.getOrNull()
                }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

        ClassroomSyncResult(
            status = obj["status"]?.jsonPrimitive?.content ?: "failed",
            imported = intAt("imported"),
            updated = intAt("updated"),
            classesTotal = intAt("classesTotal"),
            matchedClasses = nameListAt("matchedClasses"),
            skippedClasses = nameListAt("skippedClasses"),
            error = obj["error"]?.jsonPrimitive?.content,
            message = obj["message"]?.jsonPrimitive?.content,
        )
    }.getOrElse {
        ClassroomSyncResult(status = "failed", error = "unreadable sync response")
    }

    private companion object {
        private const val TAG = "ClassroomSync"
        private const val FUNCTION = "classroom-sync"
    }
}
