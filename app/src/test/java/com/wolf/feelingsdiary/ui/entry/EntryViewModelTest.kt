package com.wolf.feelingsdiary.ui.entry

import androidx.lifecycle.SavedStateHandle
import com.wolf.feelingsdiary.data.DiaryRepository
import com.wolf.feelingsdiary.domain.Entry
import com.wolf.feelingsdiary.domain.EntryTime
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.reminder.ReminderNotifier
import com.wolf.feelingsdiary.ui.FeelingLabeler
import com.wolf.feelingsdiary.ui.LocaleSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {
    private val happy = Feeling(1, "happy", null, 0, 2, false, null)
    private val stressed = Feeling(2, "stressed", null, 0, -1, false, null)
    private val sad = Feeling(3, "sad", null, 0, -2, false, null)
    private val hiddenBored = Feeling(4, "bored", null, 0, -1, true, null)

    private val feelings = MutableStateFlow(listOf(happy, stressed, sad, hiddenBored))
    private val repo = mockk<DiaryRepository>()
    private val notifier = mockk<ReminderNotifier>(relaxed = true)
    private val labels = mapOf("happy" to "Glücklich", "stressed" to "Gestresst", "sad" to "Traurig", "bored" to "Gelangweilt")
    private val labeler = FeelingLabeler { it.customName ?: labels.getValue(it.baseKey!!) }
    private val localeSource = LocaleSource().apply { update(Locale.GERMAN) }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { repo.observeFeelings() } returns feelings
        coEvery { repo.addEntry(any(), any()) } returns 1L
        coEvery { repo.addCustomFeeling(any(), any(), any()) } returns 99L
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(handle: SavedStateHandle = SavedStateHandle()): EntryViewModel {
        val vm = EntryViewModel(repo, labeler, localeSource, handle, notifier)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    @Test
    fun toggleKeepsSelectionOrderAndPromotesNextPrimary() =
        runTest {
            val vm = vm()
            listOf(1L, 2L, 3L, 1L).forEach { vm.toggle(it) }
            runCurrent()
            assertEquals(listOf(2L, 3L), vm.state.value.selectedIds)
        }

    @Test
    fun saveRequiresFeelingAndPeople() =
        runTest {
            val vm = vm()
            runCurrent()
            assertFalse(vm.state.value.canSave)
            vm.toggle(1)
            runCurrent()
            assertFalse(vm.state.value.canSave)
            vm.setPeople(0)
            runCurrent()
            assertTrue(vm.state.value.canSave)
            vm.save()
            runCurrent()
            coVerify { repo.addEntry(0, listOf(1L)) }
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun saveCancelsReminderNotification() =
        runTest {
            val vm = vm()
            vm.toggle(1)
            vm.setPeople(0)
            vm.save()
            runCurrent()
            coVerify { repo.addEntry(0, listOf(1L)) }
            verify(exactly = 1) { notifier.cancel() }
        }

    @Test
    fun createFeelingWithExistingNameSelectsExisting() =
        runTest {
            val vm = vm()
            vm.createFeeling("gestresst", 0, -1)
            runCurrent()
            coVerify(exactly = 0) { repo.addCustomFeeling(any(), any(), any()) }
            assertTrue(2L in vm.state.value.selectedIds)
        }

    @Test
    fun createFeelingWithHiddenNameSelectsHiddenWithoutInsert() =
        runTest {
            val vm = vm()
            vm.createFeeling("  Gelangweilt ", 0, -1)
            runCurrent()
            coVerify(exactly = 0) { repo.addCustomFeeling(any(), any(), any()) }
            assertEquals(listOf(4L), vm.state.value.selectedIds)
            assertTrue(feelings.value.first { it.id == 4L }.hidden)
        }

    @Test
    fun createFeelingWithNewNameInsertsSelectsLastAndClearsQuery() =
        runTest {
            val vm = vm()
            vm.toggle(1)
            vm.onQueryChange("Wehmut")
            vm.createFeeling("Wehmut", 0x123, -1)
            runCurrent()
            coVerify(exactly = 1) { repo.addCustomFeeling("Wehmut", 0x123, -1) }
            assertEquals(listOf(1L, 99L), vm.state.value.selectedIds)
            assertEquals("", vm.state.value.query)
        }

    @Test
    fun stateSurvivesProcessDeath() =
        runTest {
            val vm = vm(SavedStateHandle(mapOf("selected" to longArrayOf(2, 1), "people" to 3)))
            assertEquals(listOf(2L, 1L), vm.state.value.selectedIds)
            assertEquals(3, vm.state.value.people)
        }

    @Test
    fun setPeopleRejectsOutOfRange() =
        runTest {
            val vm = vm()
            vm.setPeople(2)
            vm.setPeople(1000)
            vm.setPeople(-1)
            runCurrent()
            assertEquals(2, vm.state.value.people)
        }

    @Test
    fun saveTwiceWhileInFlightAddsOnlyOneEntry() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            coEvery { repo.addEntry(any(), any()) } coAnswers {
                gate.await()
                1L
            }
            val vm = vm(SavedStateHandle(mapOf("selected" to longArrayOf(1), "people" to 0)))
            vm.save()
            vm.save()
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            vm.save()
            runCurrent()
            coVerify(exactly = 1) { repo.addEntry(0, listOf(1L)) }
        }

    @Test
    fun saveWorksWithoutStateCollector() =
        runTest {
            val vm =
                EntryViewModel(repo, labeler, localeSource, SavedStateHandle(mapOf("selected" to longArrayOf(1), "people" to 0)), notifier)
            vm.save()
            runCurrent()
            coVerify(exactly = 1) { repo.addEntry(0, listOf(1L)) }
        }

    private val stored = Entry(5, EntryTime(29_000_000, 60), 2, listOf(2L, 1L), listOf(-1, 2))

    @Test
    fun editLoadsEntryInOrder() =
        runTest {
            coEvery { repo.getEntry(5) } returns stored
            val vm = vm(SavedStateHandle(mapOf("entryId" to 5L)))
            runCurrent()
            assertEquals(listOf(2L, 1L), vm.state.value.selectedIds)
            assertEquals(2, vm.state.value.people)
            assertNotNull(vm.state.value.editingTime)
        }

    @Test
    fun editSaveUpdatesInsteadOfAdding() =
        runTest {
            coEvery { repo.getEntry(5) } returns stored
            coEvery { repo.updateEntry(any(), any(), any()) } returns Unit
            val vm = vm(SavedStateHandle(mapOf("entryId" to 5L)))
            runCurrent()
            vm.save()
            runCurrent()
            coVerify { repo.updateEntry(5, 2, listOf(2L, 1L)) }
            coVerify(exactly = 0) { repo.addEntry(any(), any()) }
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun editDoesNotOverwriteRestoredSelection() =
        runTest {
            coEvery { repo.getEntry(5) } returns stored
            val vm = vm(SavedStateHandle(mapOf("entryId" to 5L, "selected" to longArrayOf(3), "people" to 7)))
            runCurrent()
            coVerify(exactly = 0) { repo.getEntry(any()) }
            assertEquals(listOf(3L), vm.state.value.selectedIds)
        }

    @Test
    fun editSaveOfVanishedEntryCloses() =
        runTest {
            coEvery { repo.getEntry(5) } returns stored
            coEvery { repo.updateEntry(any(), any(), any()) } throws IllegalArgumentException("gone")
            val vm = vm(SavedStateHandle(mapOf("entryId" to 5L)))
            runCurrent()
            vm.save()
            runCurrent()
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun editOfMissingEntryCloses() =
        runTest {
            coEvery { repo.getEntry(5) } returns null
            val vm = vm(SavedStateHandle(mapOf("entryId" to 5L)))
            runCurrent()
            assertTrue(vm.state.value.saved)
        }

    @Test
    fun labelsFollowLocaleChange() =
        runTest {
            localeSource.update(Locale.ENGLISH)
            val english = mapOf("happy" to "Happy", "stressed" to "Stressed", "sad" to "Sad", "bored" to "Bored")
            val localized =
                FeelingLabeler {
                    val names = if (localeSource.locale.value.language == "de") labels else english
                    names.getValue(it.baseKey!!)
                }
            val vm = EntryViewModel(repo, localized, localeSource, SavedStateHandle(), notifier)
            backgroundScope.launch { vm.state.collect {} }
            vm.onQueryChange("glück")
            runCurrent()
            assertEquals(emptyList<Feeling>(), vm.state.value.suggestions.others)
            // No new repository emission: the locale change alone must re-match the query.
            localeSource.update(Locale.GERMAN)
            runCurrent()
            assertEquals(listOf(happy), vm.state.value.suggestions.others)
        }
}
