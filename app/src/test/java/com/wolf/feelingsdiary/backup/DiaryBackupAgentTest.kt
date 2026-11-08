package com.wolf.feelingsdiary.backup

import android.app.backup.FullBackupDataOutput
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.data.settings.settingsDataStore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiaryBackupAgentTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    // The DataStore delegate is process-wide, so every test starts and ends with an empty store.
    private fun clearSettings() {
        runBlocking { ctx.settingsDataStore.edit { it.clear() } }
    }

    @Before
    fun setUp() = clearSettings()

    @After
    fun tearDown() = clearSettings()

    private fun setBackup(enabled: Boolean) {
        runBlocking { SettingsRepository(ctx.settingsDataStore).update { it.copy(backupEnabled = enabled) } }
    }

    private val output = mockk<FullBackupDataOutput>(relaxed = true)

    private fun agent(): DiaryBackupAgent {
        val agent = spyk(Robolectric.buildBackupAgent(DiaryBackupAgent::class.java).create().get())
        every { agent.delegateFullBackup(any()) } just Runs
        return agent
    }

    @Test
    fun defaultIsOff() {
        val agent = agent()
        agent.onFullBackup(output)
        verify(exactly = 0) { agent.delegateFullBackup(any()) }
    }

    @Test
    fun offDoesNotDelegate() {
        setBackup(false)
        val agent = agent()
        agent.onFullBackup(output)
        verify(exactly = 0) { agent.delegateFullBackup(any()) }
    }

    @Test
    fun onDelegates() {
        setBackup(true)
        val agent = agent()
        agent.onFullBackup(output)
        verify(exactly = 1) { agent.delegateFullBackup(output) }
    }
}
