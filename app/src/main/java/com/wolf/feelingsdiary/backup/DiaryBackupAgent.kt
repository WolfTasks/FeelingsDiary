package com.wolf.feelingsdiary.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.data.settings.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Gate for Android Auto Backup and device-to-device transfer (both go through [onFullBackup]).
 * Only when the user turned backup on does it hand over to Auto Backup, which then copies what the
 * backup rules include; otherwise it writes nothing, so no byte leaves the device.
 *
 * No Hilt here: the system may start the agent without the normal app start, so it reads the
 * settings store directly.
 */
open class DiaryBackupAgent : BackupAgent() {
    // Key-value backup is not used (fullBackupOnly), so these stay empty.
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (backupEnabled()) delegateFullBackup(data)
    }

    /** Test seam: the actual Auto Backup of the included files. */
    internal open fun delegateFullBackup(data: FullBackupDataOutput) = super.onFullBackup(data)

    // Any failure to read the setting counts as off: when in doubt, nothing leaves the device.
    private fun backupEnabled(): Boolean =
        try {
            readBackupEnabled()
        } catch (error: Exception) {
            Log.w(TAG, "Backup setting unreadable, skipping backup", error)
            false
        }

    /** Test seam: the stored setting, read straight from the store (no Hilt). */
    internal open fun readBackupEnabled(): Boolean =
        runBlocking { SettingsRepository.backupEnabled(applicationContext.settingsDataStore.data.first()) }

    private companion object {
        const val TAG = "DiaryBackupAgent"
    }
}
