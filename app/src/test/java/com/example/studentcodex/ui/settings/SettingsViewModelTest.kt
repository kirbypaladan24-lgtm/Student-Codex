package com.example.studentcodex.ui.settings

import com.example.studentcodex.data.auth.AuthRepository
import com.example.studentcodex.data.auth.SessionInfo
import com.example.studentcodex.data.auth.StoredSchoolSelection
import com.example.studentcodex.data.school.Campus
import com.example.studentcodex.data.school.SchoolDirectory
import com.example.studentcodex.data.school.SchoolDirectoryRepository
import com.example.studentcodex.data.school.SchoolProgram
import com.example.studentcodex.data.school.SchoolSelection
import com.example.studentcodex.data.school.YearLevel
import com.example.studentcodex.data.school.YearSection
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
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeAuth = FakeAuthRepository()
    private val fakeSchool = FakeSchoolDirectoryRepository()
    private val session = SessionInfo(userId = "u-1", email = "me@test.dev")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cold start loads the registry and prefills the stored school details`() = runTest {
        fakeAuth.storedSchool = StoredSchoolSelection("p-2", 3, "s-2", "c-2")
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.isGuest)
        assertEquals("p-2", state.programId)
        assertEquals(3, state.yearLevel)
        assertEquals("s-2", state.sectionId)
        assertEquals("c-2", state.campusId)
        assertTrue(vm.schoolDirectory.value is com.example.studentcodex.ui.auth.SchoolDirectoryState.Ready)
        assertEquals(1, fakeAuth.loadSchoolSelectionCalls)
    }

    @Test
    fun `guests skip account loading entirely`() = runTest {
        val vm = SettingsViewModel(fakeAuth, fakeSchool, SessionInfo(userId = null, email = null, isGuest = true))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isGuest)
        assertFalse(state.loading)
        assertEquals(0, fakeAuth.loadSchoolSelectionCalls)
        assertEquals(0, fakeSchool.loadCalls)
    }

    @Test
    fun `registry failure marks load failed and retry recovers`() = runTest {
        fakeSchool.failLoad = true
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loadFailed)
        assertEquals(0, fakeAuth.loadSchoolSelectionCalls)

        fakeSchool.failLoad = false
        vm.retryDirectory()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loadFailed)
        assertEquals(1, fakeAuth.loadSchoolSelectionCalls)
    }

    @Test
    fun `failed profile read is best-effort and still prefills nothing`() = runTest {
        fakeAuth.failLoadSchoolSelection = true
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.loadFailed)
        assertNull(state.programId)
    }

    @Test
    fun `unknown course codes are accepted as typed and junk is rejected`() = runTest {
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        // the fake account is prefilled with CS 101 (its enrollments)
        assertEquals(listOf("CS 101"), vm.uiState.value.courseCodes)

        // a REAL code the catalog does not know: accepted — the server
        // creates the course from it when the school sheet is saved
        vm.onCourseCodeInputChange("CC1")
        vm.addCourseCode()
        var state = vm.uiState.value
        assertEquals(listOf("CS 101", "CC1"), state.courseCodes)
        assertFalse(state.courseCodeError)
        assertFalse(state.schoolSaved)

        // junk that cannot be a code at all: rejected with the error flag
        vm.onCourseCodeInputChange("My subject!!!")
        vm.addCourseCode()
        state = vm.uiState.value
        assertEquals(listOf("CS 101", "CC1"), state.courseCodes)
        assertTrue(state.courseCodeError)

        // case/whitespace noise on a prefilled code: folds silently (the
        // Settings registry has an empty catalog, so this runs through the
        // case-insensitive duplicate check, not canonicalization)
        vm.onCourseCodeInputChange("cs   101")
        vm.addCourseCode()
        state = vm.uiState.value
        assertEquals(listOf("CS 101", "CC1"), state.courseCodes)
    }

    @Test
    fun `change password rejects a too-short password`() = runTest {
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        vm.onPasswordChange("abc")
        vm.onConfirmPasswordChange("abc")
        vm.savePassword()
        advanceUntilIdle()

        assertEquals(SettingsError.PASSWORD_LENGTH, vm.uiState.value.error)
        assertEquals(0, fakeAuth.changePasswordCalls)
    }

    @Test
    fun `change password rejects a mismatched confirmation`() = runTest {
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        vm.onPasswordChange("longenough")
        vm.onConfirmPasswordChange("different")
        vm.savePassword()
        advanceUntilIdle()

        assertEquals(SettingsError.PASSWORD_MISMATCH, vm.uiState.value.error)
        assertEquals(0, fakeAuth.changePasswordCalls)
    }

    @Test
    fun `change password saves, clears the fields and reports success`() = runTest {
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        vm.onPasswordChange("new-secret-1")
        vm.onConfirmPasswordChange("new-secret-1")
        vm.savePassword()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.error)
        assertNull(state.remoteError)
        assertTrue(state.passwordSaved)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertEquals("new-secret-1", fakeAuth.changedPassword)
        assertEquals(1, fakeAuth.changePasswordCalls)
    }

    @Test
    fun `change password surfaces remote failures`() = runTest {
        fakeAuth.failChangePassword = true
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        vm.onPasswordChange("new-secret-1")
        vm.onConfirmPasswordChange("new-secret-1")
        vm.savePassword()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.passwordSaved)
        assertTrue(vm.uiState.value.remoteError != null)
    }

    @Test
    fun `school save requires all four dropdowns`() = runTest {
        fakeAuth.storedSchool = StoredSchoolSelection("p-1", 2, "s-1", null)
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        vm.saveSchool()
        advanceUntilIdle()

        assertEquals(SettingsError.SCHOOL, vm.uiState.value.error)
        assertEquals(0, fakeAuth.updateSchoolCalls)

        vm.onCampusChange("c-1")
        vm.saveSchool()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.schoolSaved)
        assertEquals(
            SchoolSelection("p-1", 2, "s-1", "c-1"),
            fakeAuth.updatedSchool,
        )
    }

    @Test
    fun `school save surfaces remote failures`() = runTest {
        fakeAuth.failUpdateSchool = true
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        vm.onProgramChange("p-1")
        vm.onYearLevelChange(2)
        vm.onSectionChange("s-1")
        vm.onCampusChange("c-1")
        vm.saveSchool()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.schoolSaved)
        assertTrue(vm.uiState.value.remoteError != null)
    }

    @Test
    fun `editing a field clears the saved flag`() = runTest {
        fakeAuth.storedSchool = StoredSchoolSelection("p-1", 2, "s-1", "c-1")
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        vm.saveSchool()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.schoolSaved)

        vm.onYearLevelChange(3)
        assertFalse(vm.uiState.value.schoolSaved)
    }

    @Test
    fun `campus change drops a program the new campus does not offer`() = runTest {
        fakeAuth.storedSchool = StoredSchoolSelection("p-1", 2, "s-1", "c-1")
        val vm = SettingsViewModel(fakeAuth, fakeSchool, session)
        advanceUntilIdle()

        // Goa's BSIT row cannot stay selected once Tinambac is chosen.
        vm.onCampusChange("c-2")
        assertNull(vm.uiState.value.programId)
        assertEquals("c-2", vm.uiState.value.campusId)
        assertFalse(vm.uiState.value.schoolSaved)
    }
}

private val testDirectory = SchoolDirectory(
    programs = listOf(
        SchoolProgram("p-1", "Bachelor of Science in Information Technology (BSIT)", "c-1"),
        SchoolProgram("p-2", "Bachelor of Science in Accountancy (BSA)", "c-2"),
        SchoolProgram("p-3", "Bachelor of Science in Information Technology (BSIT)", "c-2"),
        SchoolProgram("p-4", "Bachelor of Science in Hospitality Management (BSHM)", "c-3"),
    ),
    campuses = listOf(
        Campus("c-1", "Goa Campus (Main Campus)"),
        Campus("c-2", "Tinambac Campus"),
        Campus("c-3", "San Jose Campus"),
    ),
    yearLevels = listOf(
        YearLevel(1, "1st"),
        YearLevel(2, "2nd"),
        YearLevel(3, "3rd"),
    ),
    yearSections = listOf(
        YearSection("s-1", "A"),
        YearSection("s-2", "B"),
    ),
)

private class FakeSchoolDirectoryRepository : SchoolDirectoryRepository {
    var failLoad = false
    var loadCalls = 0

    override suspend fun load(): SchoolDirectory {
        loadCalls++
        if (failLoad) error("network down")
        return testDirectory
    }
}

private class FakeAuthRepository : AuthRepository {
    var storedSession: SessionInfo? = null
    var storedSchool: StoredSchoolSelection? = null
    var updatedSchool: SchoolSelection? = null
    var changedPassword: String? = null
    var failLoadSchoolSelection = false
    var failUpdateSchool = false
    var failChangePassword = false
    var loadSchoolSelectionCalls = 0
    var loadMyCourseCodesCalls = 0
    var updateSchoolCalls = 0
    var changePasswordCalls = 0

    override suspend fun currentSession(): SessionInfo? = storedSession

    override suspend fun signIn(email: String, password: String): SessionInfo =
        SessionInfo(userId = "u-1", email = email)

    override suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        school: SchoolSelection,
        courseCodes: List<String>,
    ): com.example.studentcodex.data.auth.SignUpResult =
        com.example.studentcodex.data.auth.SignUpResult.SignedIn(SessionInfo(userId = "u-1", email = email))

    override suspend fun signInWithGoogleIdToken(idToken: String): com.example.studentcodex.data.auth.GoogleSignInResult =
        com.example.studentcodex.data.auth.GoogleSignInResult(
            session = SessionInfo(userId = "u-g", email = "student@gmail.com"),
        )

    override suspend fun completeGoogleProfile(
        firstName: String,
        lastName: String,
        password: String?,
        school: SchoolSelection,
        courseCodes: List<String>,
    ): SessionInfo = SessionInfo(userId = "u-g", email = "student@gmail.com")

    override suspend fun signOut() {
        storedSession = null
    }

    override suspend fun loadSchoolSelection(): StoredSchoolSelection? {
        loadSchoolSelectionCalls++
        if (failLoadSchoolSelection) error("network down")
        return storedSchool
    }

    override suspend fun updateSchoolDetails(
        school: SchoolSelection,
        courseCodes: List<String>,
    ) {
        updateSchoolCalls++
        if (failUpdateSchool) error("network down")
        updatedSchool = school
    }

    /** The account's current class list — prefills the course editor. */
    var myCourseCodes: List<String> = listOf("CS 101")

    override suspend fun loadMyCourseCodes(): List<String> {
        loadMyCourseCodesCalls++
        return myCourseCodes
    }

    override suspend fun changePassword(newPassword: String) {
        changePasswordCalls++
        if (failChangePassword) error("Password should be at least 6 characters")
        changedPassword = newPassword
    }
}
