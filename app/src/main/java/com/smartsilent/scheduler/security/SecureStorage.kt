package com.smartsilent.scheduler.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val SECURE_PREFS_FILE_NAME = "smartsilent_secure_prefs"

/** Wraps the Android Keystore-backed MasterKey used to encrypt admin-auth
 * storage - framework Section 42. */
@Singleton
class KeyStoreManager @Inject constructor(@ApplicationContext private val context: Context) {
    fun getOrCreateMasterKey(): MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
}

/**
 * Framework Section 42 - the admin password hash+salt live here, in an
 * EncryptedSharedPreferences file, never in the Room database. This also
 * makes v7.0 Section 64's Auto Backup exclusion a plain file-domain
 * exclusion (see res/xml/data_extraction_rules.xml, domain="sharedpref")
 * rather than something that has to track a specific database table/path.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStoreManager: KeyStoreManager
) {
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE_NAME,
            keyStoreManager.getOrCreateMasterKey(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getString(key: String): String? = prefs.getString(key, null)
    fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun remove(key: String) { prefs.edit().remove(key).apply() }
}
