package com.example.studentcodex.data.supabase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * PostgREST JSON parsing shared by the Supabase-backed repositories.
 *
 * Rows are parsed from the response's raw JSON with the RUNTIME
 * kotlinx-serialization API (Json.parseToJsonElement + JsonObject accessors)
 * rather than generated @Serializable serializers: the project builds with
 * AGP 9's built-in Kotlin compiler, which does not run the serialization
 * compiler plugin's codegen (verified — no *$serializer classes are ever
 * produced). This is the same style as the auth user-metadata parsing and
 * the school registry repository (documented in their KDocs).
 */
object PostgrestJson {

    /** Parses a PostgREST response body as an array of row objects. */
    fun parseRows(body: String): List<JsonObject> {
        val element = runCatching { Json.parseToJsonElement(body) }.getOrNull()
        return (element as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            .orEmpty()
    }

    /** String value, or null when absent/blank/JSON-null. */
    fun JsonObject.stringValue(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    /** Integer value, or null when absent/not a number. */
    fun JsonObject.intValue(key: String): Int? =
        get(key)?.jsonPrimitive?.intOrNull

    /** Boolean value, or null when absent. */
    fun JsonObject.boolValue(key: String): Boolean? =
        get(key)?.jsonPrimitive?.booleanOrNull

    /**
     * timestamptz value ("2026-10-24T16:00:00+00:00" or ISO instant),
     * or null when absent/blank. Invalid formats return null rather than
     * throwing: a malformed timestamp must never crash a whole list load.
     */
    fun JsonObject.instantValue(key: String): Instant? {
        val raw = stringValue(key) ?: return null
        return try {
            Instant.parse(raw)
        } catch (e: DateTimeParseException) {
            // PostgREST emits an offset form ("+00:00") that Instant.parse
            // handles, but be tolerant of the colon-less zone variant too.
            runCatching { Instant.parse(raw.replace("+0000", "+00:00")) }.getOrNull()
        }
    }
}
