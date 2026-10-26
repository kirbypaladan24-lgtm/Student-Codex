package com.example.studentcodex.ui.home

import com.example.studentcodex.data.fake.FakeActivityRepository
import com.example.studentcodex.data.fake.FakeCommunityRepository
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
class HomeViewModelTest {

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
    fun `starts in loading state`() {
        val vm = HomeViewModel(FakeActivityRepository(), FakeCommunityRepository())
        assertEquals(HomeUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `loads content sorted by due date and limited to preview count`() = runTest {
        val vm = HomeViewModel(FakeActivityRepository(), FakeCommunityRepository())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Content, was $state", state is HomeUiState.Content)
        state as HomeUiState.Content

        assertEquals(HomeViewModel.HomePreviewCount, state.nextActivities.size)
        val dueDates = state.nextActivities.mapNotNull { it.dueAt }
        assertEquals(dueDates, dueDates.sorted())
        assertEquals(HomeViewModel.CommunityPreviewCount, state.recentPosts.size)
    }
}
