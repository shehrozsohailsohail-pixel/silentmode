package com.smartsilent.scheduler.core.overrides

import com.smartsilent.scheduler.core.policy.TargetRingerMode
import com.smartsilent.scheduler.data.local.TemporaryOverrideEntity
import com.smartsilent.scheduler.data.repository.OverrideRepository
import com.smartsilent.scheduler.time.TimeManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Framework Section 3.3 (Temporary Override) + v7.0 Section 66 (at most one
 * override is ever active - creating a new one supersedes, never stacks
 * with, an existing one). The actual "disable old, insert new" atomicity
 * lives in OverrideRepository/TemporaryOverrideDao as a Room @Transaction;
 * this class only computes the business values (createdAt/expiresAt) from
 * TimeManager and delegates.
 */
@Singleton
class TemporaryOverrideManager @Inject constructor(
    private val timeManager: TimeManager,
    private val overrideRepository: OverrideRepository
) {
    suspend fun createOverride(mode: TargetRingerMode, durationMinutes: Long): TemporaryOverrideEntity {
        require(durationMinutes > 0) { "Override duration must be positive" }
        val now = timeManager.nowInstant()
        val entity = TemporaryOverrideEntity(
            mode = mode.name,
            createdAt = now.toEpochMilli(),
            expiresAt = now.plusSeconds(durationMinutes * 60).toEpochMilli(),
            enabled = true,
            revision = 1,
            creationSource = "MANUAL"
        )
        overrideRepository.replaceActiveOverride(entity)
        return entity
    }

    suspend fun cancelActiveOverride() {
        overrideRepository.disableActiveOverride()
    }

    suspend fun getActiveOverride(): TemporaryOverrideEntity? = overrideRepository.getActiveOverride()
}
