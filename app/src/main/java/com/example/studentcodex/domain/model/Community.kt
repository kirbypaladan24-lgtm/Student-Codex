package com.example.studentcodex.domain.model

import java.time.Instant

/**
 * Community models (Phase 1 shapes used for demo data; full module lands in
 * Phase 9 with posts, nested replies, likes, and images through Supabase).
 */

data class CommunityPost(
    val id: String,
    val authorName: String,
    val postedAt: Instant,
    val text: String,
    val likeCount: Int,
    val isLikedByMe: Boolean,
    val commentCount: Int,
    /** Anonymous means hidden from other students in the public interface. */
    val isAnonymous: Boolean,
    /**
     * Optional photo attached to the post. In the demo build this is a
     * content-Uri string picked from the device gallery (in-memory only);
     * Phase 9 replaces it with a Supabase Storage URL.
     */
    val imageUri: String? = null,
) {
    /** Name shown in the public feed. */
    val displayName: String
        get() = if (isAnonymous) "" else authorName
}

/**
 * A reply on a community post. Same shape the Phase 9 `comments` table
 * carries; the demo repositories hold them in memory.
 */
data class Comment(
    val id: String,
    val authorName: String,
    val postedAt: Instant,
    val text: String,
    /** Anonymous means hidden from other students in the public interface. */
    val isAnonymous: Boolean = false,
)

/** Minimal student profile (university, program, year level - no bio). */
data class StudentProfile(
    val displayName: String,
    val university: String,
    val program: String,
    val yearLevel: String,
)
