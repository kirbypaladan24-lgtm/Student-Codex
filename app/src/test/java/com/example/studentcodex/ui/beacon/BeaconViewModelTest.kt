package com.example.studentcodex.ui.beacon

import com.example.studentcodex.data.fake.FakeActivityRepository
import com.example.studentcodex.data.fake.demoActivities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BeaconViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads all activities sorted by due date`() = runTest {
        val vm = BeaconViewModel(FakeActivityRepository())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is BeaconUiState.Content)
        state as BeaconUiState.Content

        assertEquals(BeaconFilter.ALL, state.selectedFilter)
        assertEquals(8, state.filtered.size)
        val dueDates = state.filtered.mapNotNull { it.dueAt }
        assertEquals(dueDates, dueDates.sorted())
        // The single activity without filtering must still be present.
        assertTrue(state.filtered.any { it.dueAt == null || it.isPersonal })
    }

    @Test
    fun `assignment filter shows only assignments`() = runTest {
        val vm = BeaconViewModel(FakeActivityRepository())
        advanceUntilIdle()

        vm.setFilter(BeaconFilter.ASSIGNMENTS)
        val state = vm.uiState.value as BeaconUiState.Content

        assertTrue(state.filtered.isNotEmpty())
        assertTrue(state.filtered.all {
            it.type == com.example.studentcodex.domain.model.ActivityType.ASSIGNMENT
        })
    }

    @Test
    fun `activity filter shows only generic activities`() = runTest {
        val vm = BeaconViewModel(FakeActivityRepository())
        advanceUntilIdle()

        vm.setFilter(BeaconFilter.ACTIVITY)
        val state = vm.uiState.value as BeaconUiState.Content

        assertTrue(state.filtered.isNotEmpty())
        assertTrue(state.filtered.all {
            it.type == com.example.studentcodex.domain.model.ActivityType.OTHER
        })
    }

    @Test
    fun `retry recovers after a failure`() = runTest {
        var failing = true
        val flakyRepo = object : com.example.studentcodex.data.repo.ActivityRepository {
            override suspend fun getActivities(): List<com.example.studentcodex.domain.model.Activity> {
                if (failing) error("network down")
                return demoActivities
            }
            override suspend fun createActivity(draft: com.example.studentcodex.data.repo.ActivityDraft) =
                error("not used in this test")
            override suspend fun updateActivity(id: String, draft: com.example.studentcodex.data.repo.ActivityDraft) =
                error("not used in this test")
            override suspend fun deleteActivity(id: String) = error("not used in this test")
            override suspend fun getMySections(): List<com.example.studentcodex.data.repo.MySection> =
                error("not used in this test")
        }
        val vm = BeaconViewModel(flakyRepo)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is BeaconUiState.Error)

        failing = false
        vm.retry()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue("Expected Content after retry, was $state", state is BeaconUiState.Content)
    }

    @Test
    fun `class report requires a section before saving`() = runTest {
        val repo = FakeActivityRepository()
        val vm = BeaconViewModel(repo)
        advanceUntilIdle()

        vm.openEditor()
        advanceUntilIdle()
        vm.onEditorTitleChange("Boardwork chapter 5")
        vm.saveEditor()
        advanceUntilIdle()

        val sheet = vm.editorSheet.value
        assertTrue("Expected SECTION_REQUIRED, was ${sheet?.error}", sheet?.error == "SECTION_REQUIRED")
    }

    @Test
    fun `saving a class report carries the enrolled section and course`() = runTest {
        val repo = FakeActivityRepository()
        val vm = BeaconViewModel(repo)
        advanceUntilIdle()

        vm.openEditor()
        advanceUntilIdle()
        vm.onEditorTitleChange("Boardwork chapter 5")
        advanceUntilIdle()
        vm.onEditorSectionChange("sec-cs201")
        vm.saveEditor()
        advanceUntilIdle()

        // Sheet closes on success; the draft carried the section cohort.
        assertTrue(vm.editorSheet.value == null)
        val draft = repo.lastDraft
        assertTrue("draft=$draft", draft != null)
        assertEquals("sec-cs201", draft?.sectionId)
        assertEquals("crs-cs201", draft?.courseId)
        assertEquals(false, draft?.isPersonal)
        assertEquals(false, draft?.teacherConfirmed) // enrolled as a student
    }

    @Test
    fun `a taught section saves as a teacher-confirmed class activity`() = runTest {
        val repo = FakeActivityRepository()
        val vm = BeaconViewModel(repo)
        advanceUntilIdle()

        vm.openEditor()
        advanceUntilIdle()
        vm.onEditorTitleChange("Prelim exam coverage")
        vm.onEditorSectionChange("sec-cs301") // the tutored section
        vm.saveEditor()
        advanceUntilIdle()

        assertTrue(vm.editorSheet.value == null)
        val draft = repo.lastDraft
        assertTrue("draft=$draft", draft != null)
        assertEquals("sec-cs301", draft?.sectionId)
        assertEquals("crs-cs301", draft?.courseId)
        assertEquals(false, draft?.isPersonal)
        assertEquals(true, draft?.teacherConfirmed) // taught -> teacher-confirmed
    }
}

