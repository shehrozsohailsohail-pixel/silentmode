package com.smartsilent.scheduler.util

import com.smartsilent.scheduler.time.LocalTimeResolver
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

object IdGenerator {
    fun newSessionId(): String = UUID.randomUUID().toString()
}

/** General-purpose SHA-256 hex, used by backup checksums (BackupValidator).
 * Not the same as SystemStateHasher, which is specifically shaped around
 * SystemState rather than an arbitrary string. */
object HashUtils {
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object DateTimeUtils {
    private val displayFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    private val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun formatForDisplay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        displayFormatter.format(instant.atZone(zone))

    /** Epoch-millis convenience for UI code that stores times as Long
     * (TemporaryOverrideEntity.expiresAt etc.) rather than java.time types. */
    fun formatTimeOnly(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeOnlyFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}

/** Shared field checks used by the rule-editor ViewModels before a save is
 * allowed to reach RuleRepository - the same checks RuleValidator applies
 * again defensively at expansion time. */
object ValidationUtils {
    fun isValidTimeOfDay(value: String): Boolean = LocalTimeResolver.parseOrNull(value) != null
    fun isNonBlank(value: String): Boolean = value.isNotBlank()
}

/** Thin org.json helpers (no extra serialization dependency needed) for
 * BackupExporter/BackupImporter. */
object JsonUtils {
    fun optString(json: JSONObject, key: String): String? =
        if (json.has(key) && !json.isNull(key)) json.getString(key) else null

    fun optLong(json: JSONObject, key: String): Long? =
        if (json.has(key) && !json.isNull(key)) json.getLong(key) else null
}
