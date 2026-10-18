package com.smartsilent.scheduler.di

import com.smartsilent.scheduler.android.capability.AndroidCapabilityManager
import com.smartsilent.scheduler.android.capability.AndroidStateReader
import com.smartsilent.scheduler.android.capability.CapabilityManager
import com.smartsilent.scheduler.android.ringerdnd.AndroidRingerDndManager
import com.smartsilent.scheduler.android.ringerdnd.RingerDndManager
import com.smartsilent.scheduler.calendar.CalendarManager
import com.smartsilent.scheduler.core.reconciliation.CalendarRuleResolver
import com.smartsilent.scheduler.scheduling.AlarmScheduler
import com.smartsilent.scheduler.scheduling.AndroidAlarmScheduler
import com.smartsilent.scheduler.time.SystemTimeManager
import com.smartsilent.scheduler.time.TimeManager
import com.smartsilent.scheduler.util.KeyValueSettings
import com.smartsilent.scheduler.util.RoomKeyValueSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Every impl class here has its own @Inject constructor, so this module only
 * has to say which concrete type answers which interface. AndroidCapabilityManager
 * is bound twice (CapabilityManager and AndroidStateReader) - both bindings
 * resolve to the same @Singleton-scoped instance, since @Binds aliases the
 * underlying concrete-type provision rather than creating a new one.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds @Singleton abstract fun bindTimeManager(impl: SystemTimeManager): TimeManager

    @Binds @Singleton abstract fun bindCapabilityManager(impl: AndroidCapabilityManager): CapabilityManager

    @Binds @Singleton abstract fun bindAndroidStateReader(impl: AndroidCapabilityManager): AndroidStateReader

    @Binds @Singleton abstract fun bindRingerDndManager(impl: AndroidRingerDndManager): RingerDndManager

    @Binds @Singleton abstract fun bindAlarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler

    @Binds @Singleton abstract fun bindCalendarRuleResolver(impl: CalendarManager): CalendarRuleResolver

    @Binds @Singleton abstract fun bindKeyValueSettings(impl: RoomKeyValueSettings): KeyValueSettings
}
