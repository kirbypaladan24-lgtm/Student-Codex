package com.example.studentcodex.ui.auth

import android.app.Activity
import com.example.studentcodex.data.auth.AuthRepository
import com.example.studentcodex.data.auth.DuplicateAccountException
import com.example.studentcodex.data.auth.GoogleAuthManager
import com.example.studentcodex.data.auth.GoogleAuthOutcome
import com.example.studentcodex.data.auth.GoogleSignInResult
import com.example.studentcodex.data.auth.SessionInfo
import com.example.studentcodex.data.auth.SignUpResult
import com.example.studentcodex.data.auth.StoredSchoolSelection
import com.example.studentcodex.data.school.Campus
import com.example.studentcodex.data.school.CourseInfo
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
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeAuth = FakeAuthRepository()
    private val fakeGoogle = FakeGoogleAuthManager()
    private val fakeSchool = FakeSchoolDirectoryRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cold start without stored session shows empty sign-in form`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.SignedOut)
        state as AuthUiState.SignedOut
        assertEquals(AuthMode.SIGN_IN, state.mode)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals("", state.firstName)
        assertEquals("", state.lastName)
        assertNull(state.programId)
        assertNull(state.yearLevel)
        assertNull(state.sectionId)
        assertNull(state.campusId)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.confirmationRequired)
        assertFalse(state.showGoogleSetup)
    }

    @Test
    fun `cold start with stored session enters the app directly`() = runTest {
        fakeAuth.storedSession = SessionInfo(userId = "u-1", email = "me@test.dev")
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.SignedIn)
        state as AuthUiState.SignedIn
        assertEquals("me@test.dev", state.session.email)
        assertFalse(state.session.isGuest)
    }

    @Test
    fun `restored session without a completed profile opens the completion form`() = runTest {
        fakeAuth.storedSession = SessionInfo(
            userId = "u-1",
            email = "me@test.dev",
            profileCompleted = false,
            hasEmailCredential = true,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.CompletingProfile)
        state as AuthUiState.CompletingProfile
        assertEquals("me@test.dev", state.email)
        assertTrue(state.hasPasswordCredential)
    }

    @Test
    fun `restore failure falls back to the sign-in form`() = runTest {
        fakeAuth.failCurrentSession = true
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is AuthUiState.SignedOut)
    }

    @Test
    fun `sign in success publishes the signed-in session`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("student@campus.edu")
        vm.onPasswordChange("hunter22")
        vm.signIn()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.SignedIn)
        state as AuthUiState.SignedIn
        assertEquals("student@campus.edu", state.session.email)
        assertEquals(1, fakeAuth.signInCalls)
    }

    @Test
    fun `sign in failure surfaces a remote error and stops loading`() = runTest {
        fakeAuth.failSignIn = true
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("student@campus.edu")
        vm.onPasswordChange("hunter22")
        vm.signIn()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.SignedOut)
        state as AuthUiState.SignedOut
        assertFalse(state.isLoading)
        assertTrue(state.error is AuthError.Remote)
        assertEquals("Invalid login credentials", (state.error as AuthError.Remote).message)
    }

    @Test
    fun `invalid email is rejected without touching the repository`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("not-an-email")
        vm.onPasswordChange("hunter22")
        vm.signIn()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.Validation.EMAIL, state.error)
        assertEquals(0, fakeAuth.signInCalls)
    }

    @Test
    fun `too short password is rejected before any network call`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("student@campus.edu")
        vm.onPasswordChange("abc")
        vm.signIn()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.Validation.PASSWORD_LENGTH, state.error)
        assertEquals(0, fakeAuth.signInCalls)
    }

    @Test
    fun `sign up missing a name is rejected locally`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("student@campus.edu")
        vm.onFirstNameChange("Juan")
        vm.onPasswordChange("hunter22")
        vm.onConfirmPasswordChange("hunter22")
        vm.signUp()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.Validation.NAME, state.error)
        assertEquals(0, fakeAuth.signUpCalls)
    }

    @Test
    fun `sign up with mismatched passwords is rejected locally`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("student@campus.edu")
        vm.onFirstNameChange("Juan")
        vm.onLastNameChange("Dela Cruz")
        vm.onPasswordChange("hunter22")
        vm.onConfirmPasswordChange("hunter23")
        vm.selectSchool()
        vm.signUp()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.Validation.PASSWORD_MISMATCH, state.error)
        assertEquals(0, fakeAuth.signUpCalls)
    }

    @Test
    fun `sign up missing school details is rejected locally`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("student@campus.edu")
        vm.onFirstNameChange("Juan")
        vm.onLastNameChange("Dela Cruz")
        vm.onPasswordChange("hunter22")
        vm.onConfirmPasswordChange("hunter22")
        vm.signUp()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.Validation.SCHOOL, state.error)
        assertEquals(0, fakeAuth.signUpCalls)
    }

    @Test
    fun `sign up with only some school fields chosen is rejected locally`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("student@campus.edu")
        vm.onFirstNameChange("Juan")
        vm.onLastNameChange("Dela Cruz")
        vm.onPasswordChange("hunter22")
        vm.onConfirmPasswordChange("hunter22")
        vm.onSchoolProgramChange("p-1")
        vm.onSchoolYearLevelChange(2)
        // Section and campus left unchosen.
        vm.signUp()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.Validation.SCHOOL, state.error)
        assertEquals(0, fakeAuth.signUpCalls)
    }

    @Test
    fun `sign up that signs in directly enters the app`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("newbie@campus.edu")
        vm.onFirstNameChange("Juan")
        vm.onLastNameChange("Dela Cruz")
        vm.onPasswordChange("hunter22")
        vm.onConfirmPasswordChange("hunter22")
        vm.selectSchool()
        vm.signUp()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.SignedIn)
        state as AuthUiState.SignedIn
        assertEquals("newbie@campus.edu", state.session.email)
    }

    @Test
    fun `sign up passes the names and school selection to the repository`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("newbie@campus.edu")
        vm.onFirstNameChange("  Juan ")
        vm.onLastNameChange("Dela Cruz")
        vm.onPasswordChange("hunter22")
        vm.onConfirmPasswordChange("hunter22")
        vm.selectSchool(programId = "p-2", yearLevel = 3, sectionId = "s-2", campusId = "c-2")
        vm.signUp()
        advanceUntilIdle()

        assertEquals(1, fakeAuth.signUpCalls)
        assertEquals("Juan", fakeAuth.signUpFirstName)
        assertEquals("Dela Cruz", fakeAuth.signUpLastName)
        assertEquals(
            SchoolSelection(programId = "p-2", yearLevel = 3, sectionId = "s-2", campusId = "c-2"),
            fakeAuth.signUpSchool,
        )
    }

    @Test
    fun `duplicate email on sign-up maps to the email-taken error`() = runTest {
        fakeAuth.failSignUpDuplicate = true
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("taken@campus.edu")
        vm.onFirstNameChange("Juan")
        vm.onLastNameChange("Dela Cruz")
        vm.onPasswordChange("hunter22")
        vm.onConfirmPasswordChange("hunter22")
        vm.selectSchool()
        vm.signUp()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.EmailTaken, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `sign up awaiting email confirmation shows notice and switches to sign-in`() = runTest {
        fakeAuth.signUpRequiresConfirmation = true
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("newbie@campus.edu")
        vm.onFirstNameChange("Juan")
        vm.onLastNameChange("Dela Cruz")
        vm.onPasswordChange("hunter22")
        vm.onConfirmPasswordChange("hunter22")
        vm.selectSchool()
        vm.signUp()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertTrue(state.confirmationRequired)
        assertEquals(AuthMode.SIGN_IN, state.mode)
        assertFalse(state.isLoading)
        // Credentials other than the email are cleared for the next attempt.
        assertEquals("", state.password)
    }

    @Test
    fun `guest bypass creates an in-memory guest session without repo calls`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInAsGuest()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.SignedIn)
        state as AuthUiState.SignedIn
        assertTrue(state.session.isGuest)
        assertNull(state.session.email)
        assertEquals(
            0,
            fakeAuth.signInCalls + fakeAuth.signUpCalls + fakeAuth.googleSignInCalls,
        )
    }

    @Test
    fun `signing out a real session returns to the gate and revokes remotely`() = runTest {
        fakeAuth.storedSession = SessionInfo(userId = "u-1", email = "me@test.dev")
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signOut()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is AuthUiState.SignedOut)
        assertEquals(1, fakeAuth.signOutCalls)
        assertNull(fakeAuth.storedSession)
    }

    @Test
    fun `signing out a guest never calls the repository`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInAsGuest()
        vm.signOut()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is AuthUiState.SignedOut)
        assertEquals(0, fakeAuth.signOutCalls)
    }

    @Test
    fun `switching mode clears the confirmation notice`() = runTest {
        fakeAuth.signUpRequiresConfirmation = true
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onEmailChange("newbie@campus.edu")
        vm.onFirstNameChange("Juan")
        vm.onLastNameChange("Dela Cruz")
        vm.onPasswordChange("hunter22")
        vm.onConfirmPasswordChange("hunter22")
        vm.selectSchool()
        vm.signUp()
        advanceUntilIdle()
        assertTrue((vm.uiState.value as AuthUiState.SignedOut).confirmationRequired)

        vm.switchMode()
        assertFalse((vm.uiState.value as AuthUiState.SignedOut).confirmationRequired)
        assertEquals(AuthMode.SIGN_UP, (vm.uiState.value as AuthUiState.SignedOut).mode)
    }

    // ---------------------------------------------------- school directory

    @Test
    fun `school directory loads at cold start and is ready for the forms`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        val directory = vm.schoolDirectory.value
        assertTrue(directory is SchoolDirectoryState.Ready)
        directory as SchoolDirectoryState.Ready
        assertEquals(4, directory.directory.programs.size)
        assertEquals(3, directory.directory.campuses.size)
        assertEquals(1, fakeSchool.loadCalls)
    }

    @Test
    fun `directory load failure surfaces the retryable failed state`() = runTest {
        fakeSchool.failLoad = true
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        assertTrue(vm.schoolDirectory.value is SchoolDirectoryState.Failed)
        assertEquals(1, fakeSchool.loadCalls)

        // Recovery: the failure flag clears and the user taps Try again.
        fakeSchool.failLoad = false
        vm.retrySchoolDirectory()
        advanceUntilIdle()

        assertTrue(vm.schoolDirectory.value is SchoolDirectoryState.Ready)
        assertEquals(2, fakeSchool.loadCalls)
    }

    @Test
    fun `retry while a load is already running never duplicates the fetch`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()
        assertEquals(1, fakeSchool.loadCalls)

        vm.retrySchoolDirectory()
        vm.retrySchoolDirectory()
        advanceUntilIdle()

        // The second/third calls find a completed (cached-repository) load;
        // the ViewModel only guards against concurrent double starts.
        assertTrue(vm.schoolDirectory.value is SchoolDirectoryState.Ready)
    }

    // ------------------------------------------------------------- Google

    @Test
    fun `google sign-in without a configured client shows the setup notice`() = runTest {
        val google = FakeGoogleAuthManager(isConfigured = false)
        val vm = AuthViewModel(fakeAuth, google, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertTrue(state.showGoogleSetup)
        assertEquals(0, google.fetchCalls)
        assertEquals(0, fakeAuth.googleSignInCalls)
    }

    @Test
    fun `google token exchange signs a returning user straight in`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.SignedIn)
        state as AuthUiState.SignedIn
        assertEquals("student@gmail.com", state.session.email)
        assertEquals("google-id-token", fakeAuth.googleIdToken)
        assertEquals(1, fakeAuth.googleSignInCalls)
        assertEquals(1, fakeGoogle.fetchCalls)
    }

    @Test
    fun `first google sign-in opens the completion form prefilled from the google name`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = false,
        )
        fakeAuth.googleSuggestedName = "Juan Dela Cruz"
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.CompletingProfile)
        state as AuthUiState.CompletingProfile
        assertEquals("student@gmail.com", state.email)
        assertFalse(state.hasPasswordCredential)
        assertEquals("Juan", state.firstName)
        assertEquals("Dela Cruz", state.lastName)
        assertNull(state.error)
    }

    @Test
    fun `first google sign-in on an already linked account hides the password fields`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = true,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.CompletingProfile
        assertTrue(state.hasPasswordCredential)
    }

    @Test
    fun `duplicate email on google maps to the email-taken error`() = runTest {
        fakeAuth.failGoogleSignInDuplicate = true
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.EmailTaken, state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `rejected google token surfaces a remote error`() = runTest {
        fakeAuth.failGoogleSignIn = true
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertFalse(state.isLoading)
        assertTrue(state.error is AuthError.Remote)
    }

    @Test
    fun `cancelled account picker leaves the form untouched`() = runTest {
        val google = FakeGoogleAuthManager(outcome = GoogleAuthOutcome.Cancelled)
        val vm = AuthViewModel(fakeAuth, google, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertNull(state.error)
        assertFalse(state.isLoading)
        assertEquals(0, fakeAuth.googleSignInCalls)
        assertEquals(1, google.fetchCalls)
    }

    @Test
    fun `device-side google failure shows the friendly google error`() = runTest {
        val google = FakeGoogleAuthManager(outcome = GoogleAuthOutcome.Error("play services missing"))
        val vm = AuthViewModel(fakeAuth, google, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.Google(GoogleFailure.GENERIC, "play services missing"), state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `no google account on the device maps to the add-account error`() = runTest {
        val google = FakeGoogleAuthManager(outcome = GoogleAuthOutcome.NoAccount)
        val vm = AuthViewModel(fakeAuth, google, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.Google(GoogleFailure.NO_ACCOUNT), state.error)
        assertFalse(state.isLoading)
        assertEquals(0, fakeAuth.googleSignInCalls)
    }

    @Test
    fun `rejected google request maps to the registration hint with the code`() = runTest {
        val google = FakeGoogleAuthManager(outcome = GoogleAuthOutcome.Rejected("13"))
        val vm = AuthViewModel(fakeAuth, google, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(AuthError.Google(GoogleFailure.REJECTED, "13"), state.error)
        assertFalse(state.isLoading)
        assertEquals(0, fakeAuth.googleSignInCalls)
    }

    @Test
    fun `dismiss setup notice clears the flag`() = runTest {
        val google = FakeGoogleAuthManager(isConfigured = false)
        val vm = AuthViewModel(fakeAuth, google, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()
        assertTrue((vm.uiState.value as AuthUiState.SignedOut).showGoogleSetup)

        vm.dismissGoogleSetup()
        assertFalse((vm.uiState.value as AuthUiState.SignedOut).showGoogleSetup)
    }

    // ------------------------------------------------- profile completion

    @Test
    fun `completion submit saves names, password and school selection and signs in`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = false,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        vm.onProfileFirstNameChange(" Juan ")
        vm.onProfileLastNameChange("Dela Cruz")
        vm.onProfilePasswordChange("hunter22")
        vm.onProfileConfirmPasswordChange("hunter22")
        vm.selectSchool(programId = "p-2", yearLevel = 3, sectionId = "s-2", campusId = "c-2")
        vm.submitProfileCompletion()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.SignedIn)
        state as AuthUiState.SignedIn
        assertEquals("Juan Dela Cruz", state.session.displayName)
        assertEquals(1, fakeAuth.completionCalls)
        assertEquals("Juan", fakeAuth.completionFirstName)
        assertEquals("Dela Cruz", fakeAuth.completionLastName)
        assertEquals("hunter22", fakeAuth.completionPassword)
        assertEquals(
            SchoolSelection(programId = "p-2", yearLevel = 3, sectionId = "s-2", campusId = "c-2"),
            fakeAuth.completionSchool,
        )
    }

    @Test
    fun `completion on a linked account sends no password`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = true,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        vm.onProfileFirstNameChange("Juan")
        vm.onProfileLastNameChange("Dela Cruz")
        vm.selectSchool()
        vm.submitProfileCompletion()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is AuthUiState.SignedIn)
        assertEquals(1, fakeAuth.completionCalls)
        assertNull(fakeAuth.completionPassword)
    }

    @Test
    fun `completion with blank names is rejected locally`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = true,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        vm.onProfileLastNameChange("Dela Cruz")
        vm.submitProfileCompletion()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.CompletingProfile
        assertEquals(AuthError.Validation.NAME, state.error)
        assertEquals(0, fakeAuth.completionCalls)
    }

    @Test
    fun `completion missing school details is rejected locally`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = false,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        vm.onProfileFirstNameChange("Juan")
        vm.onProfileLastNameChange("Dela Cruz")
        vm.onProfilePasswordChange("hunter22")
        vm.onProfileConfirmPasswordChange("hunter22")
        vm.submitProfileCompletion()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.CompletingProfile
        assertEquals(AuthError.Validation.SCHOOL, state.error)
        assertEquals(0, fakeAuth.completionCalls)
    }

    @Test
    fun `completion rejects a too-short password when one is required`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = false,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        vm.onProfileFirstNameChange("Juan")
        vm.onProfileLastNameChange("Dela Cruz")
        vm.onProfilePasswordChange("abc")
        vm.onProfileConfirmPasswordChange("abc")
        vm.selectSchool()
        vm.submitProfileCompletion()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.CompletingProfile
        assertEquals(AuthError.Validation.PASSWORD_LENGTH, state.error)
        assertEquals(0, fakeAuth.completionCalls)
    }

    @Test
    fun `completion rejects mismatched passwords when one is required`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = false,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        vm.onProfileFirstNameChange("Juan")
        vm.onProfileLastNameChange("Dela Cruz")
        vm.onProfilePasswordChange("hunter22")
        vm.onProfileConfirmPasswordChange("hunter23")
        vm.selectSchool()
        vm.submitProfileCompletion()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.CompletingProfile
        assertEquals(AuthError.Validation.PASSWORD_MISMATCH, state.error)
        assertEquals(0, fakeAuth.completionCalls)
    }

    @Test
    fun `completion remote failure keeps the form with the error`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = false,
        )
        fakeAuth.failCompletion = true
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()

        vm.onProfileFirstNameChange("Juan")
        vm.onProfileLastNameChange("Dela Cruz")
        vm.onProfilePasswordChange("hunter22")
        vm.onProfileConfirmPasswordChange("hunter22")
        vm.selectSchool()
        vm.submitProfileCompletion()
        advanceUntilIdle()

        val state = vm.uiState.value as AuthUiState.CompletingProfile
        assertFalse(state.isLoading)
        assertTrue(state.error is AuthError.Remote)
        // The draft survives so the user can retry without retyping.
        assertEquals("Juan", state.firstName)
        assertEquals("hunter22", state.password)
        assertEquals("p-1", state.programId)
    }

    @Test
    fun `cancelling the completion signs out and revokes the session`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            profileCompleted = false,
            hasEmailCredential = false,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.signInWithGoogle(null)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is AuthUiState.CompletingProfile)

        vm.cancelProfileCompletion()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is AuthUiState.SignedOut)
        assertEquals(1, fakeAuth.signOutCalls)
        assertEquals(0, fakeAuth.completionCalls)
    }

    // ------------------------------------------- campus-first program filtering

    @Test
    fun `choosing a campus drops a program it does not offer`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        // Goa's BSIT picked from the all-campus list, then Tinambac chosen.
        vm.onSchoolProgramChange("p-1")
        vm.onSchoolCampusChange("c-2")

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals("c-2", state.campusId)
        assertNull(state.programId)
    }

    @Test
    fun `choosing a campus keeps a program it offers`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.onSchoolProgramChange("p-3") // Tinambac's own BSIT row
        vm.onSchoolCampusChange("c-2")

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals("c-2", state.campusId)
        assertEquals("p-3", state.programId)
    }

    @Test
    fun `clearing the campus keeps the chosen program`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        vm.selectSchool()
        vm.onSchoolCampusChange(null)

        val state = vm.uiState.value as AuthUiState.SignedOut
        assertNull(state.campusId)
        assertEquals("p-1", state.programId)
    }

    @Test
    fun `campus change on the completion form drops the invalid program too`() = runTest {
        fakeAuth.googleSession = SessionInfo(
            userId = "u-1",
            email = "g@example.com",
            profileCompleted = false,
            hasEmailCredential = false,
        )
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()
        vm.signInWithGoogle(null)
        advanceUntilIdle()

        vm.onSchoolProgramChange("p-1")
        vm.onSchoolCampusChange("c-2")

        val state = vm.uiState.value as AuthUiState.CompletingProfile
        assertNull(state.programId)
        assertEquals("c-2", state.campusId)
    }

    @Test
    fun `programsForCampus lists campus offerings or all programs`() {
        assertEquals(4, testDirectory.programsForCampus(null).size)
        assertEquals(listOf("p-1"), testDirectory.programsForCampus("c-1").map { it.id })
        assertEquals(listOf("p-2", "p-3"), testDirectory.programsForCampus("c-2").map { it.id })
        assertTrue(testDirectory.programsForCampus("c-404").isEmpty())
    }

    @Test
    fun `programLabel disambiguates duplicated names only in the all-campus list`() {
        val bsitGoa = testDirectory.programs.first { it.id == "p-1" }
        val bsitTinambac = testDirectory.programs.first { it.id == "p-3" }
        val bsa = testDirectory.programs.first { it.id == "p-2" }
        val bshm = testDirectory.programs.first { it.id == "p-4" }

        assertEquals(
            "Bachelor of Science in Information Technology (BSIT) — Goa Campus (Main Campus)",
            testDirectory.programLabel(bsitGoa, null),
        )
        assertEquals(
            "Bachelor of Science in Information Technology (BSIT) — Tinambac Campus",
            testDirectory.programLabel(bsitTinambac, null),
        )
        assertEquals("Bachelor of Science in Accountancy (BSA)", testDirectory.programLabel(bsa, null))
        assertEquals("Bachelor of Science in Hospitality Management (BSHM)", testDirectory.programLabel(bshm, null))
        assertEquals("Bachelor of Science in Information Technology (BSIT)", testDirectory.programLabel(bsitGoa, "c-1"))
    }

    @Test
    fun `unknown course codes are accepted as typed and junk is rejected`() = runTest {
        val vm = AuthViewModel(fakeAuth, fakeGoogle, fakeSchool)
        advanceUntilIdle()

        // a REAL code the catalog does not know: accepted (the server
        // creates the course from it — the catalog is never complete)
        vm.onCourseCodeInputChange("CC1")
        vm.addCourseCode()
        var state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(listOf("CC1"), state.courseCodes)
        assertFalse(state.courseCodeError)

        // junk that cannot be a code at all: rejected with the error flag
        vm.onCourseCodeInputChange("!!!")
        vm.addCourseCode()
        state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(listOf("CC1"), state.courseCodes)
        assertTrue(state.courseCodeError)

        // known code with case/whitespace noise: canonicalized
        vm.onCourseCodeInputChange("it   201")
        vm.addCourseCode()
        state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(listOf("CC1", "IT 201"), state.courseCodes)

        // case-variant duplicate of an unknown code: folds silently
        vm.onCourseCodeInputChange("cc1")
        vm.addCourseCode()
        state = vm.uiState.value as AuthUiState.SignedOut
        assertEquals(listOf("CC1", "IT 201"), state.courseCodes)
    }

    /** Fills all four school dropdowns and adds one course code. */
    private fun AuthViewModel.selectSchool(
        programId: String = "p-1",
        yearLevel: Int = 2,
        sectionId: String = "s-1",
        campusId: String = "c-1",
    ) {
        onSchoolProgramChange(programId)
        onSchoolYearLevelChange(yearLevel)
        onSchoolSectionChange(sectionId)
        onSchoolCampusChange(campusId)
        onCourseCodeInputChange("CS 101")
        addCourseCode()
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
    courses = listOf(
        CourseInfo("k-1", "CS 101", "Introduction to Computing"),
        CourseInfo("k-2", "IT 201", "Web Systems"),
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
    var failCurrentSession = false
    var failSignIn = false
    var signUpRequiresConfirmation = false
    var failSignUpDuplicate = false
    var failGoogleSignIn = false
    var failGoogleSignInDuplicate = false
    var failCompletion = false
    var signInCalls = 0
    var signUpCalls = 0
    var googleSignInCalls = 0
    var completionCalls = 0
    var signOutCalls = 0
    var googleIdToken: String? = null
    var signUpFirstName: String? = null
    var signUpLastName: String? = null
    var signUpSchool: SchoolSelection? = null
    var signUpCourseCodes: List<String> = emptyList()
    var completionFirstName: String? = null
    var completionLastName: String? = null
    var completionPassword: String? = null
    var completionSchool: SchoolSelection? = null
    var completionCourseCodes: List<String> = emptyList()

    // Settings-page support (see SettingsViewModelTest).
    var storedSchool: StoredSchoolSelection? = null
    var updatedSchool: SchoolSelection? = null
    var changedPassword: String? = null
    var failLoadSchoolSelection = false
    var failUpdateSchool = false
    var failChangePassword = false
    var loadSchoolSelectionCalls = 0
    var updateSchoolCalls = 0
    var changePasswordCalls = 0

    /** What signInWithGoogleIdToken reports for the account's session. */
    var googleSession: SessionInfo = SessionInfo(userId = "u-google", email = "student@gmail.com")

    /** Google profile name mirrored into user metadata, for prefill. */
    var googleSuggestedName: String? = null

    override suspend fun currentSession(): SessionInfo? {
        if (failCurrentSession) error("storage unavailable")
        return storedSession
    }

    override suspend fun signIn(email: String, password: String): SessionInfo {
        signInCalls++
        if (failSignIn) throw IllegalStateException("Invalid login credentials")
        val session = SessionInfo(userId = "u-$email", email = email)
        storedSession = session
        return session
    }

    override suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        school: SchoolSelection,
        courseCodes: List<String>,
    ): SignUpResult {
        signUpCalls++
        if (failSignUpDuplicate) throw DuplicateAccountException("User already registered")
        signUpFirstName = firstName
        signUpLastName = lastName
        signUpSchool = school
        signUpCourseCodes = courseCodes
        if (signUpRequiresConfirmation) return SignUpResult.ConfirmationRequired
        val session = SessionInfo(
            userId = "u-$email",
            email = email,
            displayName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
        )
        storedSession = session
        return SignUpResult.SignedIn(session)
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): GoogleSignInResult {
        googleSignInCalls++
        if (failGoogleSignInDuplicate) throw DuplicateAccountException("User already registered")
        if (failGoogleSignIn) throw IllegalStateException("Google token rejected")
        googleIdToken = idToken
        storedSession = googleSession
        return GoogleSignInResult(session = googleSession, suggestedName = googleSuggestedName)
    }

    override suspend fun completeGoogleProfile(
        firstName: String,
        lastName: String,
        password: String?,
        school: SchoolSelection,
        courseCodes: List<String>,
    ): SessionInfo {
        completionCalls++
        if (failCompletion) throw IllegalStateException("Password should be at least 6 characters")
        completionFirstName = firstName
        completionLastName = lastName
        completionPassword = password
        completionSchool = school
        completionCourseCodes = courseCodes
        val session = SessionInfo(
            userId = "u-google",
            email = "student@gmail.com",
            displayName = "$firstName $lastName",
            profileCompleted = true,
            hasEmailCredential = true,
        )
        storedSession = session
        return session
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

    override suspend fun loadMyCourseCodes(): List<String> = emptyList()

    override suspend fun changePassword(newPassword: String) {
        changePasswordCalls++
        if (failChangePassword) error("Password should be at least 6 characters")
        changedPassword = newPassword
    }

    override suspend fun signOut() {
        signOutCalls++
        storedSession = null
    }
}

private class FakeGoogleAuthManager(
    override val isConfigured: Boolean = true,
    private val outcome: GoogleAuthOutcome = GoogleAuthOutcome.Token("google-id-token"),
) : GoogleAuthManager {
    var fetchCalls = 0

    override suspend fun fetchIdToken(activity: Activity?): GoogleAuthOutcome {
        fetchCalls++
        return outcome
    }
}
