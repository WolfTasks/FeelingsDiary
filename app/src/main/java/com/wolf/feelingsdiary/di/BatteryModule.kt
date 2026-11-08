package com.wolf.feelingsdiary.di

import com.wolf.feelingsdiary.reminder.BatteryOptimization
import com.wolf.feelingsdiary.reminder.SystemBatteryOptimization
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BatteryModule {
    @Binds
    abstract fun bindBatteryOptimization(impl: SystemBatteryOptimization): BatteryOptimization
}
