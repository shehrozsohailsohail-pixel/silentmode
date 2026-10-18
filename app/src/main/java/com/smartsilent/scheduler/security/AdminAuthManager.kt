package com.smartsilent.scheduler.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Framework Section 42 - PBKDF2-HMAC-SHA256, salted and iterated. The
 * plaintext password is never stored or compared directly, only this
 * derived hash. */
object PasswordVerifier {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    fun hash(password: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
    fun saltToHex(salt: ByteArray): String = salt.joinToString("") { "%02x".format(it) }
    fun saltFromHex(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

/**
 * Framework Sections 41-42 (Admin Panel + Admin Authentication).
 *
 * Threat model, spelled out per v7.0 Section 67: this password gates only
 * SmartSilentScheduler's own UI. It cannot, and the UI that presents it must
 * not imply that it can, stop someone from opening Android's native
 * Settings or volume/DND quick-toggle and changing ringer mode directly -
 * no third-party app can lock that on stock Android. Section 21's manual-
 * intervention handling is the correct and only response to that reality,
 * not a bypass of this feature.
 */
@Singleton
class AdminAuthManager @Inject constructor(private val secureStorage: SecureStorage) {

    companion object {
        private const val KEY_HASH = "admin_password_hash"
        private const val KEY_SALT = "admin_password_salt"
    }

    fun isPasswordSet(): Boolean = secureStorage.getString(KEY_HASH) != null

    fun setPassword(password: String) {
        val salt = PasswordVerifier.newSalt()
        secureStorage.putString(KEY_HASH, PasswordVerifier.hash(password.toCharArray(), salt))
        secureStorage.putString(KEY_SALT, PasswordVerifier.saltToHex(salt))
    }

    fun verifyPassword(password: String): Boolean {
        val storedHash = secureStorage.getString(KEY_HASH) ?: return false
        val storedSaltHex = secureStorage.getString(KEY_SALT) ?: return false
        val candidateHash = PasswordVerifier.hash(password.toCharArray(), PasswordVerifier.saltFromHex(storedSaltHex))
        // MessageDigest.isEqual is the standard timing-attack-resistant
        // comparison for secret-derived strings - a plain == would leak
        // timing information about how many leading characters matched.
        return MessageDigest.isEqual(storedHash.toByteArray(), candidateHash.toByteArray())
    }

    fun clearPassword() {
        secureStorage.remove(KEY_HASH)
        secureStorage.remove(KEY_SALT)
    }
}
