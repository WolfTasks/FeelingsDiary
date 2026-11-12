package com.wolf.feelingsdiary.di

import android.content.Context
import androidx.room.Room
import com.wolf.feelingsdiary.data.db.AppDatabase
import com.wolf.feelingsdiary.data.db.EntryDao
import com.wolf.feelingsdiary.data.db.FeelingDao
import com.wolf.feelingsdiary.data.db.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "feelings.db")
            .addCallback(AppDatabase.seedCallback())
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun provideFeelingDao(db: AppDatabase): FeelingDao = db.feelingDao()

    @Provides
    fun provideEntryDao(db: AppDatabase): EntryDao = db.entryDao()
}
