package com.smartsilent.scheduler.data.repository

import com.smartsilent.scheduler.data.local.AutomationStateDao
import com.smartsilent.scheduler.data.local.AutomationStateEntity
import com.smartsilent.scheduler.data.local.CalendarRuleDao
import com.smartsilent.scheduler.data.local.CalendarRuleEntity
import com.smartsilent.scheduler.data.local.ExecutionLogDao
import com.smartsilent.scheduler.data.local.ExecutionLogEntity
import com.smartsilent.scheduler.data.local.ExceptionProfileDao
import com.smartsilent.scheduler.data.local.ExceptionProfileEntity
import com.smartsilent.scheduler.data.local.RuleDao
import com.smartsilent.scheduler.data.local.RuleEntity
import com.smartsilent.scheduler.data.local.TemporaryOverrideDao
import com.smartsilent.scheduler.data.local.TemporaryOverrideEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepository @Inject constructor(private val dao: RuleDao) {
    fun observeAll(): Flow<List<RuleEntity>> = dao.observeAllRules()
    suspend fun getAll(): List<RuleEntity> = dao.getAllRules()
    suspend fun getEnabled(): List<RuleEntity> = dao.getEnabledRules()
    suspend fun getById(id: Long): RuleEntity? = dao.getById(id)

    suspend fun save(rule: RuleEntity): Long =
        if (rule.id == 0L) dao.insert(rule) else { dao.update(rule); rule.id }

    suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class OverrideRepository @Inject constructor(private val dao: TemporaryOverrideDao) {
    suspend fun getActiveOverride(): TemporaryOverrideEntity? = dao.getActiveOverride()
    fun observeActiveOverride(): Flow<TemporaryOverrideEntity?> = dao.observeActiveOverride()

    /** Framework v7.0 Section 66 - delegates to the DAO's @Transaction method
     * so disable-old + insert-new is atomic. */
    suspend fun replaceActiveOverride(override: TemporaryOverrideEntity): Long = dao.replaceActive(override)

    suspend fun disableActiveOverride() = dao.disableAllActive()
}

@Singleton
class CalendarRuleRepository @Inject constructor(private val dao: CalendarRuleDao) {
    fun observeAll(): Flow<List<CalendarRuleEntity>> = dao.observeAllCalendarRules()
    suspend fun getAll(): List<CalendarRuleEntity> = dao.getAllCalendarRules()
    suspend fun getEnabled(): List<CalendarRuleEntity> = dao.getEnabledCalendarRules()

    suspend fun save(rule: CalendarRuleEntity): Long =
        if (rule.id == 0L) dao.insert(rule) else { dao.update(rule); rule.id }

    suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class ExceptionProfileRepository @Inject constructor(private val dao: ExceptionProfileDao) {
    fun observeAll(): Flow<List<ExceptionProfileEntity>> = dao.observeAll()
    suspend fun getById(id: Long): ExceptionProfileEntity? = dao.getById(id)

    suspend fun save(profile: ExceptionProfileEntity): Long =
        if (profile.id == 0L) dao.insert(profile) else { dao.update(profile); profile.id }

    suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class AutomationStateRepository @Inject constructor(private val dao: AutomationStateDao) {
    suspend fun get(): AutomationStateEntity? = dao.get()
    fun observe(): Flow<AutomationStateEntity?> = dao.observe()

    suspend fun upsertAfterReconciliation(
        lastAppliedPolicy: String,
        lastAppliedStateHash: String,
        newGeneration: Long,
        reconciledAtEpochMillis: Long
    ) {
        val existing = dao.get()
        dao.upsert(
            AutomationStateEntity(
                id = 1,
                lastAppliedPolicy = lastAppliedPolicy,
                lastAppliedStateHash = lastAppliedStateHash,
                automationSessionId = existing?.automationSessionId ?: UUID.randomUUID().toString(),
                generation = newGeneration,
                lastReconciliation = reconciledAtEpochMillis,
                preAutomationDndMode = existing?.preAutomationDndMode,
                preAutomationNotificationPolicy = existing?.preAutomationNotificationPolicy
            )
        )
    }

    /** Framework Section 20 - called once by OwnershipManager, before this
     * app ever changes the ringer/DND state for the first time. */
    suspend fun recordPreAutomationSnapshot(ringerMode: Int, interruptionFilter: Int) {
        val existing = dao.get()
        dao.upsert(
            (existing ?: AutomationStateEntity(
                id = 1, lastAppliedPolicy = null, lastAppliedStateHash = null,
                automationSessionId = UUID.randomUUID().toString(), generation = 0, lastReconciliation = null
            )).copy(
                preAutomationDndMode = ringerMode.toString(),
                preAutomationNotificationPolicy = interruptionFilter.toString()
            )
        )
    }

    /** Framework Section 22 - called by RestoreManager after a successful restore. */
    suspend fun clearPreAutomationSnapshot() {
        val existing = dao.get() ?: return
        dao.upsert(existing.copy(preAutomationDndMode = null, preAutomationNotificationPolicy = null))
    }
}

@Singleton
class ExecutionLogRepository @Inject constructor(private val dao: ExecutionLogDao) {
    suspend fun insert(entry: ExecutionLogEntity) = dao.insert(entry)
    fun observeRecent(limit: Int = 50): Flow<List<ExecutionLogEntity>> = dao.observeRecent(limit)

    /** Framework Section 46 - called once per boot via BootReceiver's
     * afterReconcile() hook (see LogRetentionManager). */
    suspend fun pruneOlderThan(cutoffEpochMillis: Long) = dao.pruneOlderThan(cutoffEpochMillis)
}
