package com.smartsilent.scheduler.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules WHERE enabled = 1")
    fun observeEnabledRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1")
    suspend fun getEnabledRules(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY updatedAt DESC")
    fun observeAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY updatedAt DESC")
    suspend fun getAllRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: Long): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TemporaryOverrideDao {
    @Query("SELECT * FROM temporary_overrides WHERE enabled = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveOverride(): TemporaryOverrideEntity?

    @Query("SELECT * FROM temporary_overrides WHERE enabled = 1 ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveOverride(): Flow<TemporaryOverrideEntity?>

    @Insert
    suspend fun insertRaw(override: TemporaryOverrideEntity): Long

    @Query("UPDATE temporary_overrides SET enabled = 0 WHERE enabled = 1")
    suspend fun disableAllActive()

    /** Framework v7.0 Section 66 - at most one override is ever active. A
     * Room @Transaction default method, so "disable the old one, insert the
     * new one" can never be interrupted halfway (e.g. by a process death)
     * and leave two rows enabled. */
    @Transaction
    suspend fun replaceActive(override: TemporaryOverrideEntity): Long {
        disableAllActive()
        return insertRaw(override)
    }
}

@Dao
interface CalendarRuleDao {
    @Query("SELECT * FROM calendar_rules WHERE enabled = 1")
    suspend fun getEnabledCalendarRules(): List<CalendarRuleEntity>

    @Query("SELECT * FROM calendar_rules WHERE enabled = 1")
    fun observeEnabledCalendarRules(): Flow<List<CalendarRuleEntity>>

    @Query("SELECT * FROM calendar_rules ORDER BY updatedAt DESC")
    fun observeAllCalendarRules(): Flow<List<CalendarRuleEntity>>

    @Query("SELECT * FROM calendar_rules ORDER BY updatedAt DESC")
    suspend fun getAllCalendarRules(): List<CalendarRuleEntity>

    @Insert
    suspend fun insert(rule: CalendarRuleEntity): Long

    @Update
    suspend fun update(rule: CalendarRuleEntity)

    @Query("DELETE FROM calendar_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExceptionProfileDao {
    @Query("SELECT * FROM exception_profiles ORDER BY name")
    fun observeAll(): Flow<List<ExceptionProfileEntity>>

    @Query("SELECT * FROM exception_profiles WHERE id = :id")
    suspend fun getById(id: Long): ExceptionProfileEntity?

    @Insert
    suspend fun insert(profile: ExceptionProfileEntity): Long

    @Update
    suspend fun update(profile: ExceptionProfileEntity)

    @Query("DELETE FROM exception_profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: AppSettingEntity)
}

@Dao
interface AutomationStateDao {
    @Query("SELECT * FROM automation_state WHERE id = 1")
    suspend fun get(): AutomationStateEntity?

    @Query("SELECT * FROM automation_state WHERE id = 1")
    fun observe(): Flow<AutomationStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AutomationStateEntity)
}

@Dao
interface ExecutionLogDao {
    @Insert
    suspend fun insert(entry: ExecutionLogEntity)

    @Query("SELECT * FROM execution_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<ExecutionLogEntity>>

    /** Framework Section 46 - logs are bounded via rolling retention. */
    @Query("DELETE FROM execution_log WHERE timestamp < :cutoffEpochMillis")
    suspend fun pruneOlderThan(cutoffEpochMillis: Long)

    @Query("DELETE FROM execution_log")
    suspend fun clearAll()
}
