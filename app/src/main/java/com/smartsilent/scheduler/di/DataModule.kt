package com.smartsilent.scheduler.di

import android.content.Context
import androidx.room.Room
import com.smartsilent.scheduler.data.local.AppSettingsDao
import com.smartsilent.scheduler.data.local.AutomationStateDao
import com.smartsilent.scheduler.data.local.CalendarRuleDao
import com.smartsilent.scheduler.data.local.ExceptionProfileDao
import com.smartsilent.scheduler.data.local.ExecutionLogDao
import com.smartsilent.scheduler.data.local.RuleDao
import com.smartsilent.scheduler.data.local.SmartSilentDatabase
import com.smartsilent.scheduler.data.local.TemporaryOverrideDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Only the database + DAOs live here. RuleRepository/OverrideRepository/etc
 * are NOT listed - each has its own @Inject constructor taking only a DAO,
 * so Hilt constructs them automatically once the DAO they need is provided
 * below; an explicit @Provides/@Binds entry per repository would be pure
 * boilerplate.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SmartSilentDatabase =
        Room.databaseBuilder(context, SmartSilentDatabase::class.java, "smartsilent.db").build()

    @Provides fun provideRuleDao(db: SmartSilentDatabase): RuleDao = db.ruleDao()
    @Provides fun provideTemporaryOverrideDao(db: SmartSilentDatabase): TemporaryOverrideDao = db.temporaryOverrideDao()
    @Provides fun provideCalendarRuleDao(db: SmartSilentDatabase): CalendarRuleDao = db.calendarRuleDao()
    @Provides fun provideExceptionProfileDao(db: SmartSilentDatabase): ExceptionProfileDao = db.exceptionProfileDao()
    @Provides fun provideAppSettingsDao(db: SmartSilentDatabase): AppSettingsDao = db.appSettingsDao()
    @Provides fun provideAutomationStateDao(db: SmartSilentDatabase): AutomationStateDao = db.automationStateDao()
    @Provides fun provideExecutionLogDao(db: SmartSilentDatabase): ExecutionLogDao = db.executionLogDao()
}
