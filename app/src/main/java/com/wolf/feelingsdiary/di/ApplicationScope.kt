package com.wolf.feelingsdiary.di

import javax.inject.Qualifier

/** Process-wide scope for work that must outlive any screen (reminder scheduling). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
