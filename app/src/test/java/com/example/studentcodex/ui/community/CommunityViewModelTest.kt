package com.example.studentcodex.ui.community

import com.example.studentcodex.data.repo.CommunityRepository
import com.example.studentcodex.domain.model.Comment
import com.example.studentcodex.domain.model.CommunityPost
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepo = FakeCommunityRepo()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------ feed

    @Test
    fun `load publishes the posts as content`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is CommunityUiState.Content)
        state as CommunityUiState.Content
        assertEquals(2, state.posts.size)
        assertEquals("p-1", state.posts[0].id)
    }

    @Test
    fun `load failure surfaces the error state`() = runTest {
        fakeRepo.failLoad = true
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is CommunityUiState.Error)
    }

    @Test
    fun `retry reloads the feed after an error`() = runTest {
        fakeRepo.failLoad = true
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CommunityUiState.Error)

        fakeRepo.failLoad = false
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CommunityUiState.Content)
    }

    @Test
    fun `toggle like updates the post in the feed`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()

        vm.toggleLike("p-1")
        advanceUntilIdle()

        val state = vm.uiState.value as CommunityUiState.Content
        val updated = state.posts.first { it.id == "p-1" }
        assertEquals(6, updated.likeCount)
        assertTrue(updated.isLikedByMe)
    }

    // -------------------------------------------------------------- composer

    @Test
    fun `open composer seeds the sheet with the author and a blank draft`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()

        vm.openComposer("Kirby Paladan")

        val sheet = vm.composerSheet.value
        assertTrue(sheet != null)
        sheet!!
        assertEquals("Kirby Paladan", sheet.authorName)
        assertEquals("", sheet.draft)
        assertNull(sheet.imageUri)
        assertFalse(sheet.isPosting)
    }

    @Test
    fun `close composer clears the sheet`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComposer("You")
        assertTrue(vm.composerSheet.value != null)

        vm.closeComposer()

        assertNull(vm.composerSheet.value)
    }

    @Test
    fun `submit post prepends the created post to the feed and closes the sheet`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComposer("You")
        vm.onComposerDraftChange("  Anyone up for calculus practice?  ")
        vm.submitPost()
        advanceUntilIdle()

        assertNull(vm.composerSheet.value)
        val feed = vm.uiState.value as CommunityUiState.Content
        assertEquals(3, feed.posts.size)
        val created = feed.posts.first()
        assertEquals("p-new-1", created.id)
        assertEquals("Anyone up for calculus practice?", created.text)
        assertEquals("You", created.authorName)
        assertEquals(0, created.likeCount)
        assertEquals(0, created.commentCount)
        // The demo feed is newest-first, so the new post leads it.
        assertEquals("p-new-1", feed.posts[0].id)
        assertEquals("p-1", feed.posts[1].id)
    }

    @Test
    fun `submit post keeps the picked photo on the created post`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComposer("You")
        vm.onComposerImagePicked("content://media/1")
        vm.submitPost()
        advanceUntilIdle()

        val feed = vm.uiState.value as CommunityUiState.Content
        assertEquals("content://media/1", feed.posts.first().imageUri)
        assertEquals("", feed.posts.first().text)
    }

    @Test
    fun `remove composer image clears the attachment`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComposer("You")
        vm.onComposerImagePicked("content://media/1")
        vm.removeComposerImage()

        assertNull(vm.composerSheet.value?.imageUri)
    }

    @Test
    fun `submit with a blank draft and no image does nothing`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComposer("You")
        vm.onComposerDraftChange("   ")
        vm.submitPost()
        advanceUntilIdle()

        assertEquals(0, fakeRepo.createPostCalls)
        assertTrue(vm.composerSheet.value != null)
    }

    @Test
    fun `submit post failure keeps the sheet, draft and surfaces the error`() = runTest {
        fakeRepo.failCreatePost = true
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComposer("You")
        vm.onComposerDraftChange("Will anyone bring printed notes?")
        vm.submitPost()
        advanceUntilIdle()

        val sheet = vm.composerSheet.value
        assertTrue(sheet is ComposerUiState)
        sheet!!
        assertEquals("Will anyone bring printed notes?", sheet.draft)
        assertFalse(sheet.isPosting)
        assertEquals("Post service unavailable", sheet.error)

        val feed = vm.uiState.value as CommunityUiState.Content
        assertEquals(2, feed.posts.size)
    }

    @Test
    fun `typing after a post failure clears the error`() = runTest {
        fakeRepo.failCreatePost = true
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComposer("You")
        vm.onComposerDraftChange("try once")
        vm.submitPost()
        advanceUntilIdle()
        assertTrue(vm.composerSheet.value?.error != null)

        vm.onComposerDraftChange("try once more")
        assertNull(vm.composerSheet.value?.error)
    }

    // -------------------------------------------------------------- comments

    @Test
    fun `open comments loads the thread for the post`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()

        vm.openComments("p-1")
        advanceUntilIdle()

        val sheet = vm.commentsSheet.value
        assertTrue(sheet is CommentsUiState.Content)
        sheet as CommentsUiState.Content
        assertEquals("p-1", sheet.postId)
        assertEquals(2, sheet.comments.size)
        // Oldest first: the thread reads top to bottom.
        assertTrue(sheet.comments[0].postedAt <= sheet.comments[1].postedAt)
    }

    @Test
    fun `open comments with an unknown post id keeps the sheet closed`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()

        vm.openComments("p-999")
        advanceUntilIdle()

        assertNull(vm.commentsSheet.value)
    }

    @Test
    fun `comment load failure shows the sheet error with retry`() = runTest {
        fakeRepo.failGetComments = true
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()

        vm.openComments("p-1")
        advanceUntilIdle()
        assertTrue(vm.commentsSheet.value is CommentsUiState.Error)

        fakeRepo.failGetComments = false
        vm.retryComments()
        advanceUntilIdle()
        assertTrue(vm.commentsSheet.value is CommentsUiState.Content)
    }

    @Test
    fun `close comments clears the sheet`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()

        vm.openComments("p-1")
        advanceUntilIdle()
        vm.closeComments()

        assertNull(vm.commentsSheet.value)
    }

    @Test
    fun `add comment appends the reply, clears the draft and bumps the feed count`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComments("p-1")
        advanceUntilIdle()

        vm.onDraftChange("  See you there!  ")
        vm.addComment()
        advanceUntilIdle()

        val sheet = vm.commentsSheet.value as CommentsUiState.Content
        assertEquals(3, sheet.comments.size)
        assertEquals("See you there!", sheet.comments.last().text)
        assertEquals("You", sheet.comments.last().authorName)
        assertEquals("", sheet.draft)
        assertFalse(sheet.isSending)

        val feed = vm.uiState.value as CommunityUiState.Content
        assertEquals(3, feed.posts.first { it.id == "p-1" }.commentCount)
    }

    @Test
    fun `add comment with a blank draft does nothing`() = runTest {
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComments("p-1")
        advanceUntilIdle()

        vm.onDraftChange("   ")
        vm.addComment()
        advanceUntilIdle()

        val sheet = vm.commentsSheet.value as CommentsUiState.Content
        assertEquals(2, sheet.comments.size)
        assertEquals(0, fakeRepo.addCommentCalls)
    }

    @Test
    fun `add comment failure keeps the thread, draft and surfaces the error`() = runTest {
        fakeRepo.failAddComment = true
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComments("p-1")
        advanceUntilIdle()

        vm.onDraftChange("Will anyone bring printed notes?")
        vm.addComment()
        advanceUntilIdle()

        val sheet = vm.commentsSheet.value as CommentsUiState.Content
        assertEquals(2, sheet.comments.size)
        assertEquals("Will anyone bring printed notes?", sheet.draft)
        assertFalse(sheet.isSending)
        assertEquals("Comment service unavailable", sheet.sendError)
    }

    @Test
    fun `typing after a send failure clears the error`() = runTest {
        fakeRepo.failAddComment = true
        val vm = CommunityViewModel(fakeRepo)
        advanceUntilIdle()
        vm.openComments("p-1")
        advanceUntilIdle()

        vm.onDraftChange("try once")
        vm.addComment()
        advanceUntilIdle()
        assertTrue((vm.commentsSheet.value as CommentsUiState.Content).sendError != null)

        vm.onDraftChange("try once more")
        assertNull((vm.commentsSheet.value as CommentsUiState.Content).sendError)
    }
}

private class FakeCommunityRepo : CommunityRepository {
    var failLoad = false
    var failGetComments = false
    var failAddComment = false
    var failCreatePost = false
    var addCommentCalls = 0
    var createPostCalls = 0
    private var nextPostId = 1

    private val posts = mutableListOf(
        CommunityPost(
            id = "p-1",
            authorName = "Miguel R.",
            postedAt = Instant.parse("2026-01-01T10:00:00Z"),
            text = "Study group Thursday 4pm.",
            likeCount = 5,
            isLikedByMe = false,
            commentCount = 2,
            isAnonymous = false,
        ),
        CommunityPost(
            id = "p-2",
            authorName = "Ana L.",
            postedAt = Instant.parse("2026-01-01T08:00:00Z"),
            text = "Write traversal tests first.",
            likeCount = 9,
            isLikedByMe = true,
            commentCount = 0,
            isAnonymous = false,
        ),
    )

    private val comments = mutableMapOf(
        "p-1" to mutableListOf(
            Comment(
                id = "c-1",
                authorName = "Ana L.",
                postedAt = Instant.parse("2026-01-01T10:30:00Z"),
                text = "I'm in.",
            ),
            Comment(
                id = "c-2",
                authorName = "Bea M.",
                postedAt = Instant.parse("2026-01-01T11:00:00Z"),
                text = "Saving a seat.",
            ),
        ),
    )

    override suspend fun getRecentPosts(): List<CommunityPost> {
        if (failLoad) error("Feed unavailable")
        return posts.sortedByDescending { it.postedAt }
    }

    override suspend fun toggleLike(postId: String): CommunityPost {
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

    override suspend fun createPost(text: String, imageUri: String?): CommunityPost {
        createPostCalls++
        if (failCreatePost) error("Post service unavailable")
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

    override suspend fun getComments(postId: String): List<Comment> {
        if (failGetComments) error("Comments unavailable")
        return comments[postId].orEmpty().sortedBy { it.postedAt }
    }

    override suspend fun addComment(postId: String, text: String): Comment {
        addCommentCalls++
        if (failAddComment) error("Comment service unavailable")
        val created = Comment(
            id = "c-new-$addCommentCalls",
            authorName = "You",
            postedAt = Instant.now(),
            text = text.trim(),
        )
        comments.getOrPut(postId) { mutableListOf() }.add(created)
        val index = posts.indexOfFirst { it.id == postId }
        if (index >= 0) {
            posts[index] = posts[index].copy(commentCount = posts[index].commentCount + 1)
        }
        return created
    }
}
