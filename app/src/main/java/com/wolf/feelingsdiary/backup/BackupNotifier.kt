package com.wolf.feelingsdiary.backup

import android.app.backup.BackupManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Tells Android that there is something to back up, so the next scheduled backup includes it. */
class BackupNotifier
    @Inject
    constructor(
        @ApplicationContext private val ctx: Context,
    ) {
        fun dataChanged() = BackupManager(ctx).dataChanged()
    }
