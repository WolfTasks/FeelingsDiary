package com.wolf.feelingsdiary.backup

import android.app.backup.BackupAgent
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.IOException

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
    fun onWithoutEncryptionDoesNotDelegate() {
        setBackup(true)
        every { output.transportFlags } returns 0
        val agent = agent()
        agent.onFullBackup(output)
        verify(exactly = 0) { agent.delegateFullBackup(any()) }
    }

    @Test
    fun onWithEncryptionDelegates() {
        setBackup(true)
        every { output.transportFlags } returns BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED
        val agent = agent()
        agent.onFullBackup(output)
        verify(exactly = 1) { agent.delegateFullBackup(output) }
    }

    @Test
    fun onWithDeviceTransferDelegates() {
        setBackup(true)
        every { output.transportFlags } returns BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER
        val agent = agent()
        agent.onFullBackup(output)
        verify(exactly = 1) { agent.delegateFullBackup(output) }
    }

    @Test
    fun unreadableSettingCountsAsOff() {
        setBackup(true)
        val agent = agent()
        every { agent.readBackupEnabled() } throws IOException("corrupt store")
        agent.onFullBackup(output)
        verify(exactly = 0) { agent.delegateFullBackup(any()) }
    }

    private val e2e = BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED
    private val d2d = BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER

    @Test
    fun mayLeaveDeviceRules() {
        assertFalse(mayLeaveDevice(false, 36, e2e or d2d))
        assertFalse(mayLeaveDevice(true, 36, 0))
        assertTrue(mayLeaveDevice(true, 36, e2e))
        assertTrue(mayLeaveDevice(true, 36, d2d))
        assertFalse(mayLeaveDevice(true, 27, e2e or d2d))
        assertTrue(mayLeaveDevice(true, 28, e2e))
    }
}
