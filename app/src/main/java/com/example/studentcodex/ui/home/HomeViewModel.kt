package com.example.studentcodex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studentcodex.data.repo.ActivityRepository
import com.example.studentcodex.data.repo.CommunityRepository
import com.example.studentcodex.domain.model.Activity
import com.example.studentcodex.domain.model.CommunityPost
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Home aggregates the next deadlines and a peek at the community feed. */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Error(val message: String) : HomeUiState

    data class Content(
        val nextActivities: List<Activity>,
        val recentPosts: List<CommunityPost>,
        /** Deadlines falling due within the next 7 days (all of them, not just the preview rows). */
        val dueSoonCount: Int,
    ) : HomeUiState
}

class HomeViewModel(
    private val activityRepository: ActivityRepository,
    private val communityRepository: CommunityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val now = Instant.now()
                val activities = activityRepository.getActivities()
                    .filter { it.dueAt != null }
                    .sortedBy { it.dueAt }
                val posts = communityRepository.getRecentPosts()
                val sevenDaysOut = now + Duration.ofDays(7)
                val dueSoonCount = activities.count { activity ->
                    activity.dueAt?.isBefore(sevenDaysOut) == true
                }
                val content = HomeUiState.Content(
                    nextActivities = activities.take(HomePreviewCount),
                    recentPosts = posts.take(CommunityPreviewCount),
                    dueSoonCount = dueSoonCount,
                )
                _uiState.value = content
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Could not load your overview.")
            }
        }
    }

    fun retry() = load()

    companion object {
        const val HomePreviewCount = 3
        const val CommunityPreviewCount = 2
    }
}
