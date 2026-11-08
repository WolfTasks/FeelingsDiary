package com.wolf.feelingsdiary.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /** A failed background job (e.g. one reschedule) is logged instead of crashing the app or its siblings. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + logFailures)

    private val logFailures = CoroutineExceptionHandler { _, e -> Log.e("FeelingsDiary", "Background job failed", e) }
}
