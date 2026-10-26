package com.example.studentcodex.data.fake

import com.example.studentcodex.domain.model.Activity
import com.example.studentcodex.domain.model.ActivitySource
import com.example.studentcodex.domain.model.ActivityType
import com.example.studentcodex.domain.model.Comment
import com.example.studentcodex.domain.model.CommunityPost
import com.example.studentcodex.domain.model.Course
import com.example.studentcodex.domain.model.StudentProfile
import com.example.studentcodex.domain.model.VerificationStatus
import java.time.Duration.ofHours
import java.time.Duration.ofMinutes
import java.time.Duration.ofDays
import java.time.Instant

/*
 * DEMO DATA - development/demo repository implementation.
 *
 * This file exists so the Phase 1 UI can be built and exercised without the
 * live backend. It is explicitly marked as demo data per the project rules:
 * no fake data may ship in production paths. Phase 2+ replaces the fake
 * repositories with Supabase-backed implementations behind the same
 * interfaces.
 */

private val now: Instant = Instant.now()

val demoCourses: List<Course> = listOf(
    Course(id = "c-cs210", code = "CS 210", title = "Data Structures and Algorithms"),
    Course(id = "c-math201", code = "MATH 201", title = "Calculus II"),
    Course(id = "c-eng102", code = "ENG 102", title = "Technical Writing"),
    Course(id = "c-phil115", code = "PHIL 115", title = "Ethics in Computing"),
)

val demoActivities: List<Activity> = listOf(
    Activity(
        id = "a-1",
        title = "Binary search tree implementation",
        description = "Implement insert, delete, and traversal for a BST with unit tests.",
        type = ActivityType.ASSIGNMENT,
        course = demoCourses[0],
        dueAt = now + ofHours(6),
        effortMinutes = 180,
        source = ActivitySource.GOOGLE_CLASSROOM,
        verification = VerificationStatus.IMPORTED,
    ),
    Activity(
        id = "a-2",
        title = "Problem set 7 - integration techniques",
        type = ActivityType.ASSIGNMENT,
        course = demoCourses[1],
        dueAt = now + ofDays(1),
        effortMinutes = 90,
        source = ActivitySource.GOOGLE_CLASSROOM,
        verification = VerificationStatus.IMPORTED,
    ),
    Activity(
        id = "a-3",
        title = "Midterm examination",
        description = "Covers chapters 1-5. Closed book, one cheat sheet allowed.",
        type = ActivityType.EXAM,
        course = demoCourses[1],
        dueAt = now + ofDays(4),
        source = ActivitySource.MANUAL,
        verification = VerificationStatus.STUDENT_REPORTED,
    ),
    Activity(
        id = "a-4",
        title = "Lab report: graph traversals",
        type = ActivityType.PROJECT,
        course = demoCourses[0],
        dueAt = now + ofDays(3),
        effortMinutes = 240,
        source = ActivitySource.MANUAL,
        verification = VerificationStatus.STUDENT_REPORTED,
    ),
    Activity(
        id = "a-5",
        title = "Draft of technical manual",
        description = "Peer review draft due in class (face-to-face session).",
        type = ActivityType.ASSIGNMENT,
        course = demoCourses[2],
        dueAt = now + ofDays(2),
        effortMinutes = null,
        source = ActivitySource.STUDENT_REPORT,
        verification = VerificationStatus.NEEDS_REVIEW,
    ),
    Activity(
        id = "a-6",
        title = "Reading quiz - professional codes of ethics",
        type = ActivityType.QUIZ,
        course = demoCourses[3],
        dueAt = now + ofDays(5),
        effortMinutes = 30,
        source = ActivitySource.GOOGLE_CLASSROOM,
        verification = VerificationStatus.IMPORTED,
    ),
    Activity(
        id = "a-7",
        title = "Renew library books",
        type = ActivityType.OTHER,
        course = null,
        dueAt = now + ofDays(1),
        source = ActivitySource.MANUAL,
        isPersonal = true,
        verification = VerificationStatus.TEACHER_CONFIRMED,
    ),
    Activity(
        id = "a-8",
        title = "Guest lecture: ethics of recommendation systems",
        type = ActivityType.ANNOUNCEMENT,
        course = demoCourses[3],
        dueAt = now + ofDays(6),
        source = ActivitySource.GOOGLE_CLASSROOM,
        verification = VerificationStatus.IMPORTED,
    ),
)

val demoPosts: List<CommunityPost> = listOf(
    CommunityPost(
        id = "p-1",
        authorName = "Miguel R.",
        postedAt = now - ofHours(2),
        text = "Midterm study group at the library, 3rd floor, Thursday 4pm. We are covering integration by parts and series convergence. All welcome.",
        likeCount = 14,
        isLikedByMe = false,
        commentCount = 3,
        isAnonymous = false,
    ),
    CommunityPost(
        id = "p-2",
        authorName = "Ana L.",
        postedAt = now - ofHours(5),
        text = "Tip for the BST assignment: write the traversal tests first, they catch most pointer mistakes early.",
        likeCount = 27,
        isLikedByMe = true,
        commentCount = 5,
        isAnonymous = false,
    ),
    CommunityPost(
        id = "p-3",
        authorName = "Anonymous",
        postedAt = now - ofDays(1),
        text = "How is everyone handling the technical writing workload this term? The drafts pile up fast and I keep losing track of peer review dates.",
        likeCount = 9,
        isLikedByMe = false,
        commentCount = 8,
        isAnonymous = true,
    ),
)

/** Compact builder so the demo thread reads like a conversation. */
private fun comment(
    id: String,
    author: String,
    minutesAgo: Long,
    text: String,
    anonymous: Boolean = false,
) = Comment(
    id = id,
    authorName = author,
    postedAt = now - ofMinutes(minutesAgo),
    text = text,
    isAnonymous = anonymous,
)

/*
 * Demo comment threads. Sizes deliberately match each post's commentCount so
 * the sheet is honest about the number it advertises.
 */
val demoComments: Map<String, List<Comment>> = mapOf(
    "p-1" to listOf(
        comment("c-1-1", "Ana L.", 115, "I'm in. I'll bring my series convergence cheat sheet."),
        comment("c-1-2", "Bea M.", 96, "Saving a seat near the whiteboards. See you all Thursday."),
        comment("c-1-3", "Carlo S.", 40, "Can someone post the exact room number? Third floor is a maze."),
    ),
    "p-2" to listOf(
        comment("c-2-1", "Miguel R.", 290, "This saved me an hour. The traversal tests caught my delete bug immediately."),
        comment("c-2-2", "Anonymous", 271, "Seconding this. Test the empty-tree case too.", anonymous = true),
        comment("c-2-3", "Dana P.", 186, "Professor allowed JUnit 5 parameterized tests if anyone wants to go further."),
        comment("c-2-4", "Erik T.", 120, "My insert tests pass but delete drops the wrong node. Any hints?"),
        comment("c-2-5", "Ana L.", 88, "Erik, check which subtree you attach after finding the successor."),
    ),
    "p-3" to listOf(
        comment("c-3-1", "Ana L.", 1425, "I keep a single spreadsheet of every peer review date now. One row per deadline."),
        comment("c-3-2", "Bea M.", 1400, "The drafts are fine. It's the revisions that stack up on you."),
        comment("c-3-3", "Carlo S.", 1350, "Writing center appointments help. Book them at the start of the term."),
        comment("c-3-4", "Dana P.", 1290, "Calendar blocks per draft stage. Ugly but it works."),
        comment("c-3-5", "Anonymous", 1100, "Honestly, just start the drafts earlier. Easier said than done.", anonymous = true),
        comment("c-3-6", "Erik T.", 980, "Our section moved peer review online, which made scheduling worse somehow."),
        comment("c-3-7", "Faye D.", 720, "Second the calendar blocks. Two 45-minute sessions beat one all-nighter."),
        comment("c-3-8", "Miguel R.", 300, "Tracking deadlines here instead of in my head is already helping."),
    ),
)

val demoProfile: StudentProfile = StudentProfile(
    displayName = "Demo Student",
    university = "Demo University",
    program = "BS Computer Science",
    yearLevel = "2nd year",
)
