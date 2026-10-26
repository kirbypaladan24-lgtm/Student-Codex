package com.example.studentcodex.data.supabase

import android.util.Log
import android.net.Uri
import com.example.studentcodex.data.repo.CommunityRepository
import com.example.studentcodex.data.repo.ContentReportRepository
import com.example.studentcodex.domain.model.Comment
import com.example.studentcodex.domain.model.CommunityPost
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * Supabase-backed Community repository (Phase 9).
 *
 * Reads go through the invoker-safe `feed_posts` / `feed_comments` views:
 * they null the author for anonymous content server-side (identity can
 * never leak), carry like counts + liked-by-me flags and ordered image
 * paths, and keep RLS active for the signed-in reader.
 *
 * Writes:
 *  - posts/comments insert into the base tables (ownership, anonymity
 *    immutability, depth limits and rate limits enforced by DB guards);
 *  - likes toggle a `post_likes` row (unique per user+post) and re-read
 *    the count from the view - no client-side arithmetic to drift;
 *  - images upload to the PRIVATE `community-images` bucket under
 *    {uid}/{postId}/... (folder-prefix enforced by storage RLS) and a
 *    `post_images` row records the path at its position; reads download
 *    the bytes back through the same authenticated bucket (the read
 *    policy grants every signed-in user SELECT, so the whole feed sees
 *    every post's image — the private bucket is never made public);
 *
 * Guests cannot post (the UI disables composer/comment actions for guest
 * sessions; repositories additionally reject a missing session).
 *
 * Row parsing uses the runtime JSON API (see [PostgrestJson] KDoc).
 */
class SupabaseCommunityRepository(
    private val client: SupabaseClient,
    private val imageBytesSource: PostImageSource = ContentResolverImageSource(),
) : CommunityRepository, ContentReportRepository {

    private val auth get() = client.auth

    override suspend fun getRecentPosts(): List<CommunityPost> {
        val body = client.postgrest.from("feed_posts")
            .select(columns = FEED_COLUMNS) {
                order("created_at", Order.DESCENDING)
                limit(FEED_PAGE_SIZE)
            }
            .data
        return PostgrestJson.parseRows(body).mapNotNull { it.toPost() }
    }

    override suspend fun toggleLike(postId: String): CommunityPost {
        val uid = auth.currentUserOrNull()?.id
            ?: error("Sign in to like posts")
        // Current state comes from the view (already RLS-checked).
        val current = fetchPost(postId) ?: error("Post not found")
        if (current.isLikedByMe) {
            client.postgrest.from("post_likes").delete {
                filter {
                    eq("post_id", postId)
                    eq("user_id", uid)
                }
            }
        } else {
            client.postgrest.from("post_likes").insert(
                buildJsonObject {
                    put("post_id", postId)
                    put("user_id", uid)
                },
            )
        }
        // Re-read the canonical row: counts come from the server, so a race
        // (someone else liking at the same moment) resolves to the truth.
        return fetchPost(postId) ?: error("Post not found")
    }

    override suspend fun getComments(postId: String): List<Comment> {
        val body = client.postgrest.from("feed_comments")
            .select(columns = COMMENT_COLUMNS) {
                filter { eq("post_id", postId) }
                order("created_at", Order.ASCENDING)
                limit(COMMENT_PAGE_SIZE)
            }
            .data
        return PostgrestJson.parseRows(body).mapNotNull { it.toComment() }
    }

    override suspend fun addComment(postId: String, text: String): Comment {
        val uid = auth.currentUserOrNull()?.id
            ?: error("Sign in to comment")
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Comment cannot be empty" }
        val body = client.postgrest.from("comments")
            .insert(
                buildJsonObject {
                    put("post_id", postId)
                    put("author_id", uid)
                    put("content", trimmed)
                    put("is_anonymous", false)
                },
            ) {
                select(Columns.list("id, post_id, content, is_anonymous, created_at"))
            }
            .data
        val row = PostgrestJson.parseRows(body).firstOrNull()
            ?: error("Server did not return the created comment")
        // Author name comes from the profiles row (the comment itself
        // carries no name). The insert response has the id; re-read the
        // canonical view row for the display name.
        val commentId = row.stringValue("id") ?: error("Comment id missing")
        val viewBody = client.postgrest.from("feed_comments")
            .select(columns = COMMENT_COLUMNS) {
                filter { eq("id", commentId) }
            }
            .data
        return PostgrestJson.parseRows(viewBody).firstOrNull()?.toComment()
            ?: Comment(
                id = commentId,
                authorName = "",
                postedAt = row.instantValue("created_at") ?: java.time.Instant.now(),
                text = trimmed,
                isAnonymous = false,
            )
    }

    override suspend fun createPost(text: String, imageUri: String?): CommunityPost {
        val uid = auth.currentUserOrNull()?.id
            ?: error("Sign in to post")
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() || imageUri != null) { "Post cannot be empty" }

        // 1) Insert the post row first (image rows need its id).
        val postBody = client.postgrest.from("posts")
            .insert(
                buildJsonObject {
                    put("author_id", uid)
                    put("content", trimmed)
                    put("is_anonymous", false)
                },
            ) {
                select(Columns.list("id, content, is_anonymous, created_at"))
            }
            .data
        val postRow = PostgrestJson.parseRows(postBody).firstOrNull()
            ?: error("Server did not return the created post")
        val postId = postRow.stringValue("id") ?: error("Post id missing")

        // 2) Upload the image (if any) and register its path + position.
        if (imageUri != null) {
            uploadPostImage(uid, postId, imageUri)
        }

        // 3) Read the created post back through the feed view so the card
        //    the UI prepends matches exactly what everyone else will see.
        return fetchPost(postId) ?: error("Created post is not visible")
    }

    /**
     * Uploads one image into the private community bucket under
     * {uid}/{postId}/{ts}.jpg and records the post_images row at position 1
     * (the app attaches a single photo per post; the schema allows 3).
     */
    private suspend fun uploadPostImage(uid: String, postId: String, imageUri: String) {
        val bytes = imageBytesSource.read(imageUri)
            ?: throw IOException("Could not read the selected photo")
        val path = "$uid/$postId/${System.currentTimeMillis()}.jpg"
        client.storage.from("community-images").upload(path, bytes) {
            upsert = false
        }
        client.postgrest.from("post_images").insert(
            buildJsonObject {
                put("post_id", postId)
                put("storage_path", path)
                put("position", 1)
            },
        )
        Log.i(TAG, "Uploaded community image ($bytes.size bytes) to $path")
    }

    /**
     * Downloads one post image's bytes from the private bucket by its
     * storage path. The caller renders or drops it; failures log and
     * return null so a missing object degrades to a text-only card
     * instead of crashing the feed.
     */
    override suspend fun downloadPostImage(path: String): ByteArray? = runCatching {
        // Private bucket: the authenticated download carries the session
        // (RLS grants every signed-in user read on community-images).
        client.storage.from("community-images").downloadAuthenticated(path)
    }.onFailure { e ->
        Log.w(TAG, "Community image download failed for $path: ${e.message}")
    }.getOrNull()

    /**
     * Files a content report against a post for the moderation queue
     * (content_reports; the reporter is the signed-in user, status 'open'
     * is forced by the insert policy + rate limit guard).
     */
    override suspend fun reportPost(postId: String, reason: String, details: String?) {
        val uid = auth.currentUserOrNull()?.id
            ?: error("Sign in to report posts")
        client.postgrest.from("content_reports").insert(
            buildJsonObject {
                put("reporter_id", uid)
                put("target_type", "post")
                put("target_id", postId)
                put("reason", reason)
                put("details", details)
                put("status", "open")
            },
        )
        Log.i(TAG, "Filed content report for post $postId ($reason)")
    }

    /** Re-reads one post through the feed view (RLS-checked). */
    private suspend fun fetchPost(postId: String): CommunityPost? {
        val body = client.postgrest.from("feed_posts")
            .select(columns = FEED_COLUMNS) {
                filter { eq("id", postId) }
            }
            .data
        return PostgrestJson.parseRows(body).firstOrNull()?.toPost()
    }

    // ------------------------------------------------------------- mapping

    private fun JsonObject.toPost(): CommunityPost? {
        val id = stringValue("id") ?: return null
        return CommunityPost(
            id = id,
            // The view nulls author fields for anonymous posts; locally we
            // render the display name (empty for anonymous).
            authorName = stringValue("author_name") ?: "",
            postedAt = instantValue("created_at") ?: java.time.Instant.now(),
            text = stringValue("content") ?: "",
            likeCount = intValue("like_count") ?: 0,
            isLikedByMe = boolValue("liked_by_me") ?: false,
            commentCount = intValue("comment_count") ?: 0,
            isAnonymous = boolValue("is_anonymous") ?: false,
            imageUri = firstImagePath("image_paths"),
        )
    }

    private fun JsonObject.toComment(): Comment? {
        val id = stringValue("id") ?: return null
        return Comment(
            id = id,
            authorName = stringValue("author_name") ?: "",
            postedAt = instantValue("created_at") ?: java.time.Instant.now(),
            text = stringValue("content") ?: return null,
            isAnonymous = boolValue("is_anonymous") ?: false,
        )
    }

    /** First path of the view's ordered image_paths JSON array. */
    private fun JsonObject.firstImagePath(key: String): String? {
        val array = get(key) as? JsonArray ?: return null
        val first = array.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return runCatching { first.content }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.stringValue(key: String) = PostgrestJson.run { stringValue(key) }
    private fun JsonObject.intValue(key: String) = PostgrestJson.run { intValue(key) }
    private fun JsonObject.boolValue(key: String) = PostgrestJson.run { boolValue(key) }
    private fun JsonObject.instantValue(key: String) = PostgrestJson.run { instantValue(key) }

    private companion object {
        const val TAG = "SupabaseCommunity"
        const val FEED_PAGE_SIZE = 50L
        const val COMMENT_PAGE_SIZE = 200L
        val FEED_COLUMNS = Columns.list(
            "id", "content", "is_anonymous", "author_name", "created_at",
            "like_count", "comment_count", "liked_by_me", "image_paths",
        )
        val COMMENT_COLUMNS = Columns.list(
            "id", "post_id", "parent_comment_id", "content", "is_anonymous",
            "author_name", "created_at", "like_count", "liked_by_me",
        )
    }
}

/**
 * Reads the bytes behind an image reference (content Uri in the app,
 * file path in tests). Returning null means "unreadable" - the caller
 * turns that into an upload error the user can act on.
 */
interface PostImageSource {
    suspend fun read(reference: String): ByteArray?
}

/** Production source: decodes through the ContentResolver. */
class ContentResolverImageSource : PostImageSource {
    override suspend fun read(reference: String): ByteArray? = runCatching {
        val context = AppContextHolder.get() ?: return null
        context.contentResolver.openInputStream(Uri.parse(reference))?.use { it.readBytes() }
    }.getOrNull()
}
