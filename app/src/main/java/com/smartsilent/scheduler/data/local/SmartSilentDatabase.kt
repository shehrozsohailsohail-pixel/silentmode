package com.smartsilent.scheduler.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Still schema version 1: the Increment-1 build was never actually compiled
 * or installed anywhere (see project README), so there is no real prior
 * database to migrate away from yet - bumping the version now for a schema
 * nobody has would mean writing a Migration that migrates from a database
 * that has never existed. The first migration gets written the first time a
 * real installed version needs one.
 */
@Database(
    entities = [
        RuleEntity::class,
        TemporaryOverrideEntity::class,
        CalendarRuleEntity::class,
        ExceptionProfileEntity::class,
        AppSettingEntity::class,
        AutomationStateEntity::class,
        ExecutionLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SmartSilentDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun temporaryOverrideDao(): TemporaryOverrideDao
    abstract fun calendarRuleDao(): CalendarRuleDao
    abstract fun exceptionProfileDao(): ExceptionProfileDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun automationStateDao(): AutomationStateDao
    abstract fun executionLogDao(): ExecutionLogDao
}
