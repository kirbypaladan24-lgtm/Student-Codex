package com.example.studentcodex.data.fake

import com.example.studentcodex.data.repo.ActivityDraft
import com.example.studentcodex.data.repo.ActivityRepository
import com.example.studentcodex.data.repo.CommunityRepository
import com.example.studentcodex.data.repo.MySection
import com.example.studentcodex.data.repo.ProfileRepository
import com.example.studentcodex.domain.model.Activity
import com.example.studentcodex.domain.model.Comment
import com.example.studentcodex.domain.model.CommunityPost
import com.example.studentcodex.domain.model.StudentProfile
import kotlinx.coroutines.delay
import java.time.Instant

/*
 * DEMO REPOSITORIES - development/demo implementation.
 *
 * Simulated latency keeps the UI's loading states honest during development.
 * Production paths use the Supabase-backed implementations; these fakes stay
 * for unit tests and offline development. No production code path may use
 * these.
 */

class FakeActivityRepository : ActivityRepository {
    private val activities = demoActivities.toMutableList()
    private var nextActivityId = 1

    /** The draft of the most recent create call - for unit tests. */
    var lastDraft: ActivityDraft? = null
        private set

    /** Fake sections: what a signed-in BSCS 2-A student (who also tutors
     *  one section) sees - drives the student/teacher verification tests. */
    private val mySections = listOf(
        MySection(
            sectionId = "sec-cs101",
            sectionName = "BSCS 2-A",
            courseId = "crs-cs101",
            courseCode = "CS 101",
            courseTitle = "Introduction to Computing",
        ),
        MySection(
            sectionId = "sec-cs201",
            sectionName = "BSCS 2-A",
            courseId = "crs-cs201",
            courseCode = "CS 201",
            courseTitle = "Data Structures and Algorithms",
        ),
        MySection(
            sectionId = "sec-ge101",
            sectionName = "BSCS 2-A",
            courseId = "crs-ge101",
            courseCode = "GE 101",
            courseTitle = "Mathematics in the Modern World",
        ),
        MySection(
            sectionId = "sec-cs301",
            sectionName = "BSCS 3-A",
            courseId = "crs-cs301",
            courseCode = "CS 301",
            courseTitle = "Software Engineering",
            role = "teacher",
        ),
    )

    override suspend fun getActivities(): List<Activity> {
        delay(400)
        return activities.toList()
    }

    override suspend fun createActivity(draft: ActivityDraft): Activity {
        delay(250)
        lastDraft = draft
        val section = mySections.firstOrNull { it.sectionId == draft.sectionId }
        val created = Activity(
            id = "a-new-$nextActivityId",
            title = draft.title,
            description = draft.description,
            type = com.example.studentcodex.domain.model.ActivityType.entries
                .firstOrNull { it.name.lowercase() == draft.type }
                ?: com.example.studentcodex.domain.model.ActivityType.OTHER,
            course = section?.let {
                com.example.studentcodex.domain.model.Course(
                    id = it.courseId ?: "",
                    code = it.courseCode ?: "",
                    title = it.courseTitle ?: "",
                )
            },
            dueAt = draft.dueAt,
            effortMinutes = draft.effortMinutes,
            source = com.example.studentcodex.domain.model.ActivitySource.MANUAL,
            verification = if (draft.teacherConfirmed)
                com.example.studentcodex.domain.model.VerificationStatus.TEACHER_CONFIRMED
            else
                com.example.studentcodex.domain.model.VerificationStatus.STUDENT_REPORTED,
            isPersonal = draft.sectionId == null,
        )
        nextActivityId++
        activities.add(0, created)
        return created
    }

    override suspend fun updateActivity(id: String, draft: ActivityDraft): Activity {
        delay(250)
        val index = activities.indexOfFirst { it.id == id }
        require(index >= 0) { "Unknown activity id: $id" }
        val updated = activities[index].copy(
            title = draft.title,
            description = draft.description,
            dueAt = draft.dueAt,
            effortMinutes = draft.effortMinutes,
        )
        activities[index] = updated
        return updated
    }

    override suspend fun deleteActivity(id: String) {
        delay(200)
        activities.removeAll { it.id == id }
    }

    override suspend fun getMySections(): List<MySection> {
        delay(150)
        return mySections
    }
}

class FakeCommunityRepository : CommunityRepository {
    private val posts = demoPosts.toMutableList()
    private val comments = demoComments.toMutableMap()
    private var nextCommentId = 1
    private var nextPostId = 1

    override suspend fun getRecentPosts(): List<CommunityPost> {
        delay(400)
        return posts.sortedByDescending { it.postedAt }
    }

    override suspend fun toggleLike(postId: String): CommunityPost {
        delay(150)
        val index = posts.indexOfFirst { it.id == postId }
        require(index >= 0) { "Unknown post id: $postId" }
        val updated = posts[index].let {
            it.copy(
                isLikedByMe = !it.isLikedByMe,
                likeCount = it.likeCount + if (it.isLikedByMe) -1 else 1,
            )
        }
        posts[index] = updated
        return updated
    }

    override suspend fun getComments(postId: String): List<Comment> {
        delay(300)
        require(posts.any { it.id == postId }) { "Unknown post id: $postId" }
        // Oldest first: a comment thread reads top-to-bottom.
        return comments[postId].orEmpty().sortedBy { it.postedAt }
    }

    override suspend fun createPost(text: String, imageUri: String?): CommunityPost {
        delay(250)
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() || imageUri != null) { "Post cannot be empty" }
        val created = CommunityPost(
            id = "p-new-$nextPostId",
            authorName = "You",
            postedAt = Instant.now(),
            text = trimmed,
            likeCount = 0,
            isLikedByMe = false,
            commentCount = 0,
            isAnonymous = false,
            imageUri = imageUri,
        )
        nextPostId++
        posts.add(0, created)
        return created
    }

    override suspend fun addComment(postId: String, text: String): Comment {
        delay(200)
        require(posts.any { it.id == postId }) { "Unknown post id: $postId" }
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Comment cannot be empty" }
        val created = Comment(
            id = "c-new-$nextCommentId",
            authorName = "You",
            postedAt = Instant.now(),
            text = trimmed,
        )
        nextCommentId++
        comments[postId] = comments[postId].orEmpty() + created
        val index = posts.indexOfFirst { it.id == postId }
        posts[index] = posts[index].copy(commentCount = posts[index].commentCount + 1)
        return created
    }
}

class FakeProfileRepository : ProfileRepository {
    override suspend fun getProfile(): StudentProfile {
        delay(200)
        return demoProfile
    }
}
