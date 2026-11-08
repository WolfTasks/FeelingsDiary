package com.wolf.feelingsdiary.ui.feelings

import com.wolf.feelingsdiary.data.DiaryRepository
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.ui.FeelingLabeler
import com.wolf.feelingsdiary.ui.LocaleSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class FeelingsViewModelTest {
    private val happy = Feeling(1, "happy", null, 0, 2, false, null)
    private val sad = Feeling(2, "sad", null, 0, -2, false, null)
    private val customHappy = Feeling(10, null, "Happy", 0, 1, false, null)
    private val wehmut = Feeling(11, null, "Wehmut", 0, -1, false, null)
    private val hiddenAngst = Feeling(12, null, "Angst", 0, -2, true, null)

    private val feelings = MutableStateFlow(listOf(wehmut, sad, hiddenAngst, customHappy, happy))
    private val repo = mockk<DiaryRepository>()
    private val labels = mapOf("happy" to "fröhlich", "sad" to "traurig")
    private val labeler = FeelingLabeler { it.customName ?: labels.getValue(it.baseKey!!) }
    private val localeSource = LocaleSource().apply { update(Locale.GERMAN) }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { repo.observeFeelings() } returns feelings
        coEvery { repo.updateFeeling(any(), any(), any(), any()) } returns Unit
        coEvery { repo.addCustomFeeling(any(), any(), any()) } returns 99L
        coEvery { repo.setHidden(any(), any()) } answers {
            val id = firstArg<Long>()
            val hidden = secondArg<Boolean>()
            feelings.update { list -> list.map { if (it.id == id) it.copy(hidden = hidden) else it } }
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): FeelingsViewModel = FeelingsViewModel(repo, labeler, localeSource).also { runCurrent() }

    @Test
    fun hiddenFeelingsStayListed() =
        runTest {
            val vm = vm()
            // Base list order: base feelings by id, then custom ones alphabetically, hidden ones included.
            assertEquals(listOf(1L, 2L, 12L, 10L, 11L), vm.state.value.map { it.feeling.id })
        }

    @Test
    fun renameToExistingNameIsRejected() =
        runTest {
            val vm = vm()
            assertFalse(vm.save(11, "HAPPY", 5, 0))
            assertFalse(vm.save(11, "fröhlich", 5, 0))
            assertFalse(vm.save(11, " Angst ", 5, 0))
            runCurrent()
            coVerify(exactly = 0) { repo.updateFeeling(any(), any(), any(), any()) }
        }

    @Test
    fun renameToOwnNameDifferentCaseIsAllowed() =
        runTest {
            val vm = vm()
            assertTrue(vm.save(11, " WEHMUT ", 5, 1))
            runCurrent()
            coVerify(exactly = 1) { repo.updateFeeling(11, "WEHMUT", 5, 1) }
        }

    @Test
    fun baseFeelingSaveKeepsName() =
        runTest {
            val vm = vm()
            assertTrue(vm.save(1, "fröhlich", 7, 1))
            runCurrent()
            coVerify(exactly = 1) { repo.updateFeeling(1, null, 7, 1) }
        }

    @Test
    fun addRejectsDuplicateAndAddsNewName() =
        runTest {
            val vm = vm()
            assertFalse(vm.add("Traurig", 3, -2))
            assertTrue(vm.add(" Sehnsucht ", 3, -1))
            runCurrent()
            coVerify(exactly = 1) { repo.addCustomFeeling(any(), any(), any()) }
            coVerify { repo.addCustomFeeling("Sehnsucht", 3, -1) }
        }

    @Test
    fun toggleHiddenHidesAndShows() =
        runTest {
            val vm = vm()
            vm.toggleHidden(2)
            runCurrent()
            assertTrue(
                vm.state.value
                    .first { it.feeling.id == 2L }
                    .feeling.hidden,
            )
            vm.toggleHidden(12)
            runCurrent()
            assertFalse(
                vm.state.value
                    .first { it.feeling.id == 12L }
                    .feeling.hidden,
            )
        }

    @Test
    fun lastVisibleFeelingCannotBeHidden() =
        runTest {
            val vm = vm()
            // Quick taps, none awaited before the next: each check must still see the newest state.
            listOf(1L, 2L, 10L, 11L).forEach { vm.toggleHidden(it) }
            runCurrent()
            assertEquals(
                listOf(11L),
                vm.state.value
                    .filter { !it.feeling.hidden }
                    .map { it.feeling.id },
            )
            coVerify(exactly = 0) { repo.setHidden(11, true) }
        }

    @Test
    fun labelsFollowLocaleChange() =
        runTest {
            localeSource.update(Locale.ENGLISH)
            val english = mapOf("happy" to "Happy", "sad" to "Sad")
            val localized =
                FeelingLabeler {
                    it.customName ?: (if (localeSource.locale.value.language == "de") labels else english).getValue(it.baseKey!!)
                }
            val vm = FeelingsViewModel(repo, localized, localeSource)
            runCurrent()
            assertEquals(
                "Happy",
                vm.state.value
                    .first()
                    .label,
            )
            // In English, base "Happy" and custom "Happy" collide.
            assertFalse(vm.save(10, "Happy", 0, 1))
            // No new repository emission: the locale change alone must relabel the rows.
            localeSource.update(Locale.GERMAN)
            runCurrent()
            assertEquals(
                "fröhlich",
                vm.state.value
                    .first()
                    .label,
            )
            assertTrue(vm.save(10, "Happy", 0, 1))
        }
}
