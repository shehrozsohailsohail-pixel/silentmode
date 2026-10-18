package com.smartsilent.scheduler.util

import com.smartsilent.scheduler.data.local.AppSettingEntity
import com.smartsilent.scheduler.data.local.AppSettingsDao
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

object SettingsKeys {
    /** Framework Section 47 - "on" unless the user has explicitly turned it off. */
    const val GLOBAL_AUTOMATION_ENABLED = "global_automation_enabled"
}

interface KeyValueSettings {
    suspend fun getBoolean(key: String, default: Boolean): Boolean
    suspend fun setBoolean(key: String, value: Boolean)
    fun observeBoolean(key: String, default: Boolean): kotlinx.coroutines.flow.Flow<Boolean>
}

@Singleton
class RoomKeyValueSettings @Inject constructor(private val dao: AppSettingsDao) : KeyValueSettings {
    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        dao.get(key)?.toBooleanStrictOrNull() ?: default

    override suspend fun setBoolean(key: String, value: Boolean) {
        dao.set(AppSettingEntity(key, value.toString()))
    }

    override fun observeBoolean(key: String, default: Boolean) =
        dao.observe(key).map { it?.toBooleanStrictOrNull() ?: default }
}
