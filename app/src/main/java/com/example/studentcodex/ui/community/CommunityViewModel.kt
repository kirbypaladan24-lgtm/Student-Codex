package com.example.studentcodex.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studentcodex.data.repo.CommunityRepository
import com.example.studentcodex.data.repo.ContentReportRepository
import com.example.studentcodex.domain.model.Comment
import com.example.studentcodex.domain.model.CommunityPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CommunityUiState {
    data object Loading : CommunityUiState

    data class Error(val message: String) : CommunityUiState

    data class Content(val posts: List<CommunityPost>) : CommunityUiState
}

/**
 * State of the comments bottom sheet for one post. `null` means the sheet
 * is closed. Every variant carries the postId so retries can reload.
 */
sealed interface CommentsUiState {
    val postId: String

    data class Loading(override val postId: String) : CommentsUiState

    data class Error(
        override val postId: String,
        val message: String,
    ) : CommentsUiState

    data class Content(
        override val postId: String,
        val post: CommunityPost,
        val comments: List<Comment>,
        val draft: String = "",
        val isSending: Boolean = false,
        val sendError: String? = null,
    ) : CommentsUiState
}

/**
 * State of the create-post bottom sheet opened from the "What's on your
 * mind?" bar. `null` means the sheet is closed. [authorName] is who the
 * post will be attributed to (the signed-in user, or "You" for guests);
 * [isAnonymous] is the post-as-anonymous switch (immutable once posted).
 */
data class ComposerUiState(
    val authorName: String,
    val draft: String = "",
    /** Content-Uri string of the photo picked from the gallery, if any. */
    val imageUri: String? = null,
    /** True = post without showing the author's name. */
    val isAnonymous: Boolean = false,
    val isPosting: Boolean = false,
    val error: String? = null,
)

/**
 * State of the report dialog for one post (Phase 9 moderation).
 * `null` means the dialog is closed. [reason] uses the database's
 * check-constraint wire values.
 */
data class ReportPostUiState(
    val postId: String,
    val reason: String? = null,
    val details: String = "",
    val sending: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null,
)

class CommunityViewModel(
    private val communityRepository: CommunityRepository,
    private val reportRepository: ContentReportRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CommunityUiState>(CommunityUiState.Loading)
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private val _commentsSheet = MutableStateFlow<CommentsUiState?>(null)
    val commentsSheet: StateFlow<CommentsUiState?> = _commentsSheet.asStateFlow()

    private val _composerSheet = MutableStateFlow<ComposerUiState?>(null)
    val composerSheet: StateFlow<ComposerUiState?> = _composerSheet.asStateFlow()

    private val _reportSheet = MutableStateFlow<ReportPostUiState?>(null)
    val reportSheet: StateFlow<ReportPostUiState?> = _reportSheet.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = CommunityUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = CommunityUiState.Content(communityRepository.getRecentPosts())
            } catch (e: Exception) {
                _uiState.value = CommunityUiState.Error(e.message ?: "Could not load the community feed.")
            }
        }
    }

    fun retry() = load()

    // ---------------------------------------------------------------- composer

    /**
     * Opens the create-post sheet, posting as [authorName]. For guests the
     * sheet opens read-only-explainer mode: real writes need an account.
     */
    fun openComposer(authorName: String, isGuest: Boolean = false) {
        if (_composerSheet.value != null) return
        _composerSheet.value = ComposerUiState(
            authorName = authorName,
            error = if (isGuest) "Sign in to post — community posts need an account." else null,
        )
    }

    fun closeComposer() {
        if (_composerSheet.value?.isPosting == true) return
        _composerSheet.value = null
    }

    fun onComposerDraftChange(value: String) {
        val sheet = _composerSheet.value ?: return
        if (sheet.isPosting) return
        // Guests never had a writable draft; keep the explainer state clean.
        if (sheet.error?.startsWith("Sign in") == true && value.isNotEmpty()) return
        _composerSheet.value = sheet.copy(draft = value, error = null)
    }

    fun onComposerAnonymousChange(value: Boolean) {
        val sheet = _composerSheet.value ?: return
        if (sheet.isPosting) return
        _composerSheet.value = sheet.copy(isAnonymous = value)
    }

    /** Attaches the picked photo (or clears it when [uri] is null). */
    fun onComposerImagePicked(uri: String?) {
        val sheet = _composerSheet.value ?: return
        if (sheet.isPosting) return
        _composerSheet.value = sheet.copy(imageUri = uri, error = null)
    }

    fun removeComposerImage() = onComposerImagePicked(null)

    /**
     * Posts the draft (text, photo, or both) as the current user. The
     * created post lands at the TOP of the feed - newest first - and the
     * sheet closes; a failure keeps the draft and explains itself.
     */
    fun submitPost() {
        val sheet = _composerSheet.value ?: return
        if (sheet.isPosting) return
        val text = sheet.draft.trim()
        if (text.isEmpty() && sheet.imageUri == null) return
        _composerSheet.value = sheet.copy(isPosting = true, error = null)
        viewModelScope.launch {
            try {
                val created = communityRepository.createPost(text, sheet.imageUri)
                val feed = uiState.value as? CommunityUiState.Content
                _uiState.value = CommunityUiState.Content(
                    listOf(created) + (feed?.posts ?: emptyList()),
                )
                _composerSheet.value = null
            } catch (e: Exception) {
                // Keep the sheet, the draft and the photo; explain what failed.
                _composerSheet.value = _composerSheet.value?.copy(
                    isPosting = false,
                    error = e.message ?: "Could not post.",
                )
            }
        }
    }

    // ---------------------------------------------------------------- comments

    /** Toggle like; keeps the optimistic UI state consistent with the repository result. */
    fun toggleLike(postId: String) {
        val current = uiState.value as? CommunityUiState.Content ?: return
        viewModelScope.launch {
            try {
                val updated = communityRepository.toggleLike(postId)
                _uiState.value = CommunityUiState.Content(
                    current.posts.map { if (it.id == updated.id) updated else it },
                )
            } catch (e: Exception) {
                // Like failures are non-fatal: keep the feed, surface the reason.
                _uiState.value = CommunityUiState.Error(e.message ?: "Could not update the like.")
            }
        }
    }

    /** Opens the comments sheet for a post; loads the thread behind a loading state. */
    fun openComments(postId: String) {
        if (_commentsSheet.value?.postId == postId) return
        val post = (uiState.value as? CommunityUiState.Content)
            ?.posts?.firstOrNull { it.id == postId }
            ?: return
        _commentsSheet.value = CommentsUiState.Loading(postId)
        loadComments(postId, post)
    }

    fun retryComments() {
        val state = _commentsSheet.value ?: return
        val post = (uiState.value as? CommunityUiState.Content)
            ?.posts?.firstOrNull { it.id == state.postId }
            ?: return
        _commentsSheet.value = CommentsUiState.Loading(state.postId)
        loadComments(state.postId, post)
    }

    fun closeComments() {
        _commentsSheet.value = null
    }

    fun onDraftChange(value: String) {
        val content = _commentsSheet.value as? CommentsUiState.Content ?: return
        if (content.isSending) return
        _commentsSheet.value = content.copy(draft = value, sendError = null)
    }

    /** Sends the draft as a new comment; appends it and bumps the feed count. */
    fun addComment() {
        val content = _commentsSheet.value as? CommentsUiState.Content ?: return
        if (content.isSending) return
        val text = content.draft.trim()
        if (text.isEmpty()) return
        _commentsSheet.value = content.copy(isSending = true, sendError = null)
        viewModelScope.launch {
            try {
                val created = communityRepository.addComment(content.postId, text)
                val updatedPost = content.post.copy(commentCount = content.post.commentCount + 1)
                _commentsSheet.value = content.copy(
                    post = updatedPost,
                    comments = content.comments + created,
                    draft = "",
                    isSending = false,
                    sendError = null,
                )
                // Keep the feed's advertised count in step with the thread.
                val feed = uiState.value as? CommunityUiState.Content
                if (feed != null) {
                    _uiState.value = CommunityUiState.Content(
                        feed.posts.map { if (it.id == updatedPost.id) updatedPost else it },
                    )
                }
            } catch (e: Exception) {
                // Keep the thread and draft; explain what went wrong.
                _commentsSheet.value = content.copy(
                    isSending = false,
                    sendError = e.message ?: "Could not post the comment.",
                )
            }
        }
    }

    private fun loadComments(postId: String, post: CommunityPost) {
        viewModelScope.launch {
            try {
                _commentsSheet.value = CommentsUiState.Content(
                    postId = postId,
                    post = post,
                    comments = communityRepository.getComments(postId),
                )
            } catch (e: Exception) {
                _commentsSheet.value = CommentsUiState.Error(
                    postId = postId,
                    message = e.message ?: "Could not load the comments.",
                )
            }
        }
    }

    // ---------------------------------------------------------------- reports

    /** Opens the report dialog for a post (reason + optional details). */
    fun openReportSheet(postId: String) {
        if (_reportSheet.value?.sending == true) return
        _reportSheet.value = ReportPostUiState(postId = postId)
    }

    fun closeReportSheet() {
        if (_reportSheet.value?.sending == true) return
        _reportSheet.value = null
    }

    fun onReportReasonChange(reason: String) {
        updateReport { it.copy(reason = reason, error = null) }
    }

    fun onReportDetailsChange(details: String) {
        updateReport { it.copy(details = details.take(1000)) }
    }

    /**
     * Files the report to the moderation queue. When no report repository
     * is wired (guest mode / tests without one) the dialog still closes
     * honestly: reporting requires an account, same as posting.
     */
    fun submitReport() {
        val sheet = _reportSheet.value ?: return
        if (sheet.sending || sheet.submitted) return
        val reason = sheet.reason ?: return
        val repository = reportRepository ?: run {
            updateReport { it.copy(error = "Sign in to report posts.") }
            return
        }
        updateReport { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                repository.reportPost(sheet.postId, reason, sheet.details.trim().takeIf { d -> d.isNotEmpty() })
                updateReport { it.copy(sending = false, submitted = true) }
            } catch (e: Exception) {
                updateReport { it.copy(sending = false, error = e.message ?: "Could not submit the report.") }
            }
        }
    }

    private fun updateReport(update: (ReportPostUiState) -> ReportPostUiState) {
        val sheet = _reportSheet.value ?: return
        _reportSheet.value = update(sheet)
    }
}


