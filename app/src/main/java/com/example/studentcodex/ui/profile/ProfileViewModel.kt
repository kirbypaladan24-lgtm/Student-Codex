package com.example.studentcodex.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studentcodex.data.repo.ProfileRepository
import com.example.studentcodex.domain.model.StudentProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Error(val message: String) : ProfileUiState

    data class Content(
        val profile: StudentProfile,
    ) : ProfileUiState
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = ProfileUiState.Loading
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile()
                _uiState.value = ProfileUiState.Content(profile = profile)
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "Could not load your profile.")
            }
        }
    }

    fun retry() = load()
}
