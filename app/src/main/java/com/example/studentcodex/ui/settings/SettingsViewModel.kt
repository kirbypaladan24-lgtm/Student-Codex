package com.example.studentcodex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studentcodex.data.auth.AuthRepository
import com.example.studentcodex.data.auth.SessionInfo
import com.example.studentcodex.data.repo.ClassroomAutoSyncStore
import com.example.studentcodex.data.repo.ClassroomConnectionRepository
import com.example.studentcodex.data.repo.ClassroomConnectionState
import com.example.studentcodex.data.repo.ClassroomSyncRepository
import com.example.studentcodex.data.repo.ClassroomSyncResult
import com.example.studentcodex.data.school.SchoolDirectoryRepository
import com.example.studentcodex.data.school.SchoolSelection
import com.example.studentcodex.data.school.looksLikeCourseCode
import com.example.studentcodex.data.school.programOfferedAt
import com.example.studentcodex.ui.auth.SchoolDirectoryState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why a save attempt was rejected; resolved to strings in the UI. */
enum class SettingsError {
    PASSWORD_LENGTH,
    PASSWORD_MISMATCH,

    /** One or more of the four school dropdowns is still unchosen. */
    SCHOOL,

    /** No course code in the list — a class list is what the manual
     * activity editor picks from, so it cannot be empty. */
    COURSES,
}

/**
 * The Settings page's editable state: the school-details form (prefilled
 * from the stored profile row) and the change-password form, each with its
 * own saving/saved feedback. The floating-assistant style is NOT here - it
 * is owned by the overlay controller, which the screen receives directly.
 */
data class SettingsUiState(
    /** Guests have no account section to manage - the UI hides it. */
    val isGuest: Boolean = false,

    /** Initial load (registry + stored prefill) in flight. */
    val loading: Boolean = true,

    /** The registry fetch failed; the school form shows an inline retry. */
    val loadFailed: Boolean = false,

    // School details form. Null = unchosen (or not stored on the profile).
    val programId: String? = null,
    val yearLevel: Int? = null,
    val sectionId: String? = null,
    val campusId: String? = null,
    val schoolSaving: Boolean = false,
    val schoolSaved: Boolean = false,

    // Course (subject) codes editor inside the school sheet: the codes of
    // the student's enrolled courses, prefilled from the account, picked
    // from the community-catalog rolldown (same interaction as the sign-up
    // form; unknown codes still accepted — the server creates them).
    val courseCodes: List<String> = emptyList(),
    /** Search text in the course picker field. */
    val courseCodeInput: String = "",
    /** True when the last add attempt could not be a course code. */
    val courseCodeError: Boolean = false,

    // Change-password form.
    val password: String = "",
    val confirmPassword: String = "",
    val passwordSaving: Boolean = false,
    val passwordSaved: Boolean = false,

    // Google Classroom connection (the card moved here from the Profile
    // page). NotSignedIn until loaded - and permanently for guests.
    val classroom: ClassroomConnectionState = ClassroomConnectionState.NotSignedIn,

    /** True while a Classroom disconnect request is in flight. */
    val classroomDisconnecting: Boolean = false,

    /** True while a Classroom sync runs (authorization + Edge Function). */
    val classroomConnecting: Boolean = false,

    /** Last completed sync's summary (rendered by the card with resources). */
    val classroomSyncResult: ClassroomSyncResult? = null,

    /** Last sync/authorization failure shown in the error color. */
    val classroomSyncError: String? = null,

    /** Whether background Classroom auto-sync is switched on (persisted). */
    val classroomAutoSyncEnabled: Boolean = false,

    /** Local validation failure for whichever form was submitted. */
    val error: SettingsError? = null,

    /** Remote failure message for whichever save was attempted. */
    val remoteError: String? = null,
)

/**
 * The Settings page: account changes (password, school details) and the
 * Google Classroom connection card for the signed-in user. Loads the
 * school registry (the same anon-readable lists the sign-up form uses)
 * plus the stored school references for prefill, then saves through the
 * auth repository - school details travel as auth metadata so the
 * database's sync trigger applies them to the profile.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val schoolDirectoryRepository: SchoolDirectoryRepository,
    session: SessionInfo,
    // Nullable + defaulted LAST so existing three-positional-arg test call
    // sites keep compiling; the screen always passes the real repository.
    private val classroomRepository: ClassroomConnectionRepository? = null,
    // Same pattern for the sync repository (Phase 6 Edge Function).
    private val classroomSyncRepository: ClassroomSyncRepository? = null,
    // Auto-sync preference store (the auto-sync toggle), same pattern.
    private val classroomAutoSyncStore: ClassroomAutoSyncStore? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(isGuest = session.isGuest))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _schoolDirectory =
        MutableStateFlow<SchoolDirectoryState>(SchoolDirectoryState.Loading)
    val schoolDirectory: StateFlow<SchoolDirectoryState> = _schoolDirectory.asStateFlow()

    private var loadJob: Job? = null
    private var saveJob: Job? = null

    init {
        load()
    }

    /** (Re)loads the registry and the stored school details for prefill. */
    fun load() {
        if (_uiState.value.isGuest) {
            // Guests manage nothing account-related: no fetch, no spinner.
            _uiState.value = _uiState.value.copy(loading = false)
            return
        }
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(loading = true, loadFailed = false)
        loadJob = viewModelScope.launch {
            // The Classroom connection state is best-effort and independent
            // of the registry: the card must appear even when the registry
            // fetch below fails.
            val classroom = runCatching { classroomRepository?.getConnectionState() }
                .getOrNull() ?: ClassroomConnectionState.NotSignedIn
            // The auto-sync toggle is device-local state, not a server row:
            // read it beside the connection state so the card renders both.
            val autoSyncEnabled = classroomAutoSyncStore?.isEnabled() ?: false
            update { it.copy(classroom = classroom, classroomAutoSyncEnabled = autoSyncEnabled) }
            try {
                _schoolDirectory.value =
                    SchoolDirectoryState.Ready(schoolDirectoryRepository.load())
            } catch (e: Exception) {
                _schoolDirectory.value = SchoolDirectoryState.Failed
                _uiState.value = _uiState.value.copy(loading = false, loadFailed = true)
                return@launch
            }
            // Prefill is best-effort: a failed profile read still leaves a
            // fully working form (a later save attempt reports its own
            // error if the network is genuinely down).
            val stored = try {
                authRepository.loadSchoolSelection()
            } catch (e: Exception) {
                null
            }
            // The class list prefill is equally best-effort: an account with
            // no enrollments yet simply starts the editor empty (which is
            // exactly the state to fix here).
            val myCourseCodes = try {
                authRepository.loadMyCourseCodes()
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(
                loading = false,
                programId = stored?.programId,
                yearLevel = stored?.yearLevel,
                sectionId = stored?.sectionId,
                campusId = stored?.campusId,
                courseCodes = myCourseCodes,
            )
        }
    }

    /** Retry after the registry fetch failed. */
    fun retryDirectory() = load()

    fun onProgramChange(programId: String?) =
        update { it.copy(programId = programId, schoolSaved = false, error = null, remoteError = null) }

    fun onYearLevelChange(yearLevel: Int?) =
        update { it.copy(yearLevel = yearLevel, schoolSaved = false, error = null, remoteError = null) }

    fun onSectionChange(sectionId: String?) =
        update { it.copy(sectionId = sectionId, schoolSaved = false, error = null, remoteError = null) }

    /** Campus first: a program the new campus does not offer is dropped. */
    fun onCampusChange(campusId: String?) {
        val directory = (schoolDirectory.value as? SchoolDirectoryState.Ready)?.directory
        update {
            it.copy(
                campusId = campusId,
                programId = it.programId?.takeIf { id -> programOfferedAt(directory, campusId, id) },
                schoolSaved = false,
                error = null,
                remoteError = null,
            )
        }
    }

    fun onPasswordChange(password: String) =
        update { it.copy(password = password, passwordSaved = false, error = null, remoteError = null) }

    fun onConfirmPasswordChange(confirmPassword: String) =
        update { it.copy(confirmPassword = confirmPassword, passwordSaved = false, error = null, remoteError = null) }

    /** Search text typed into the school sheet's course picker field. */
    fun onCourseCodeInputChange(value: String) =
        update { it.copy(courseCodeInput = value, courseCodeError = false, schoolSaved = false) }

    /**
     * Adds a course code as a chip. KNOWN codes canonicalize against the
     * registry (case-insensitive, duplicates fold silently); UNKNOWN codes
     * are accepted as typed — the catalog can never list every subject a
     * real semester offers, so the server creates the course (and the
     * cohort section) from the code when the sheet is saved (migration
     * 0019). Only input that cannot be a code at all (letters first,
     * then letters/digits/spaces/hyphens) is rejected.
     *
     * [code] is an explicit pick from the course dropdown (a row tap);
     * null reads the field's typed search text (keyboard "done" path).
     */
    fun addCourseCode(code: String? = null) {
        val s = _uiState.value
        val trimmed = (code ?: s.courseCodeInput).trim()
        if (trimmed.isEmpty()) return
        val directory = (schoolDirectory.value as? SchoolDirectoryState.Ready)?.directory
        val resolved: String? = if (directory != null) {
            directory.normalizeCourseCode(trimmed)
        } else {
            trimmed.replace(Regex("\\s+"), " ").takeIf { looksLikeCourseCode(it) }
        }
        if (resolved == null) {
            update { it.copy(courseCodeError = true) }
            return
        }
        update { state ->
            if (state.courseCodes.any { it.equals(resolved, ignoreCase = true) }) {
                state.copy(courseCodeInput = "", courseCodeError = false)
            } else {
                state.copy(
                    courseCodes = state.courseCodes + resolved,
                    courseCodeInput = "",
                    courseCodeError = false,
                    schoolSaved = false,
                )
            }
        }
    }

    /** Removes a class chip (enrollment itself is append-only server-side). */
    fun removeCourseCode(code: String) =
        update {
            it.copy(
                courseCodes = it.courseCodes.filterNot { c -> c.equals(code, ignoreCase = true) },
                schoolSaved = false,
            )
        }

    /** Saves the four school references (and the class list) — the codes
     * (re-)run the idempotent enrollment door server-side. */
    fun saveSchool() {
        val s = _uiState.value
        if (saveJob?.isActive == true) return
        val programId = s.programId
        val yearLevel = s.yearLevel
        val sectionId = s.sectionId
        val campusId = s.campusId
        if (programId == null || yearLevel == null || sectionId == null || campusId == null) {
            _uiState.value = s.copy(error = SettingsError.SCHOOL)
            return
        }
        if (s.courseCodes.isEmpty()) {
            _uiState.value = s.copy(error = SettingsError.COURSES)
            return
        }
        val courseCodes = s.courseCodes
        saveJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(schoolSaving = true, error = null, remoteError = null)
            try {
                authRepository.updateSchoolDetails(
                    SchoolSelection(
                        programId = programId,
                        yearLevel = yearLevel,
                        sectionId = sectionId,
                        campusId = campusId,
                    ),
                    courseCodes,
                )
                _uiState.value = _uiState.value.copy(schoolSaving = false, schoolSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(schoolSaving = false, remoteError = e.message)
            }
        }
    }

    /** Sets a new password for the signed-in account. */
    fun savePassword() {
        val s = _uiState.value
        if (saveJob?.isActive == true) return
        when {
            s.password.length < PASSWORD_MIN ->
                _uiState.value = s.copy(error = SettingsError.PASSWORD_LENGTH)
            s.password != s.confirmPassword ->
                _uiState.value = s.copy(error = SettingsError.PASSWORD_MISMATCH)
            else -> {
                val newPassword = s.password
                saveJob = viewModelScope.launch {
                    _uiState.value =
                        _uiState.value.copy(passwordSaving = true, error = null, remoteError = null)
                    try {
                        authRepository.changePassword(newPassword)
                        _uiState.value = _uiState.value.copy(
                            passwordSaving = false,
                            passwordSaved = true,
                            // Never leave a just-saved password in the fields.
                            password = "",
                            confirmPassword = "",
                        )
                    } catch (e: Exception) {
                        _uiState.value =
                            _uiState.value.copy(passwordSaving = false, remoteError = e.message)
                    }
                }
            }
        }
    }

    /**
     * Runs one sync with a Google access token the screen just obtained
     * through the Authorization API consent sheet (Phase 6). The result
     * summary lands under the connection card; the card itself refreshes
     * from the registry so "Connected · last sync" updates immediately.
     */
    fun onClassroomAuthorized(googleAccessToken: String) {
        val repository = classroomSyncRepository ?: return
        val s = _uiState.value
        if (s.isGuest || s.classroomConnecting) return
        viewModelScope.launch {
            update {
                it.copy(
                    classroomConnecting = true,
                    classroomSyncResult = null,
                    classroomSyncError = null,
                )
            }
            val result = try {
                repository.sync(googleAccessToken)
            } catch (e: Exception) {
                ClassroomSyncResult(status = "failed", error = e.message ?: "network error")
            }
            update {
                it.copy(
                    classroomConnecting = false,
                    classroomSyncResult = result.takeIf { r -> r.status != "failed" },
                    classroomSyncError = if (result.status == "failed") {
                        result.message ?: result.error ?: "sync failed"
                    } else {
                        null
                    },
                )
            }
            refreshClassroomState()
        }
    }

    /**
     * Reports a failed Google authorization handshake (the consent sheet
     * refused or errored) so the card shows an actionable note instead of
     * silently ignoring the tap.
     */
    fun onClassroomAuthError(message: String) {
        update { it.copy(classroomSyncError = message, classroomSyncResult = null) }
    }

    /**
     * Flips the auto-sync preference (the switch on the connection card)
     * and persists it immediately. Turning it ON also arms the first
     * background run - the screen triggers that silent attempt itself
     * because the authorizer needs the live activity.
     */
    fun setClassroomAutoSync(enabled: Boolean) {
        classroomAutoSyncStore?.setEnabled(enabled)
        update { it.copy(classroomAutoSyncEnabled = enabled) }
    }

    /**
     * Re-reads the connection state after a sync that ran OUTSIDE this
     * ViewModel (the background auto-syncer), so the card shows a fresh
     * "last sync" stamp.
     */
    fun refreshClassroom() = refreshClassroomState()

    /** Re-reads only the connection state (used after each sync run). */
    private fun refreshClassroomState() {
        viewModelScope.launch {
            val classroom = runCatching { classroomRepository?.getConnectionState() }
                .getOrNull() ?: ClassroomConnectionState.NotSignedIn
            update { it.copy(classroom = classroom) }
        }
    }

    /**
     * Requests the Classroom disconnect (the only client-initiated
     * transition; the card moved here from the Profile page). On success
     * the card refreshes to the disconnected state.
     */
    fun disconnectClassroom() {
        val s = _uiState.value
        val repository = classroomRepository ?: return
        if (s.isGuest || s.classroomDisconnecting) return
        if (s.classroom !is ClassroomConnectionState.Connected) return
        viewModelScope.launch {
            update { it.copy(classroomDisconnecting = true) }
            try {
                repository.disconnect()
                update {
                    it.copy(
                        classroom = ClassroomConnectionState.Disconnected,
                        classroomDisconnecting = false,
                    )
                }
            } catch (e: Exception) {
                // Keep the connected state; a later reload reports fresh truth.
                update { it.copy(classroomDisconnecting = false) }
            }
        }
    }

    private fun update(block: (SettingsUiState) -> SettingsUiState) {
        _uiState.value = block(_uiState.value)
    }

    private companion object {
        /** Supabase's minimum password length (same as the sign-up form). */
        const val PASSWORD_MIN = 6
    }
}
