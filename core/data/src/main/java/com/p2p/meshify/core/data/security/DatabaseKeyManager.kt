package com.p2p.meshify.core.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.p2p.meshify.core.util.Logger
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator

/**
 * Manages the SQLCipher database encryption key.
 *
 * Generates a random 256-bit AES key on first launch and stores it
 * in EncryptedSharedPreferences (backed by Android Keystore).
 *
 * Security properties:
 * - Key is generated using Android's secure random number generator
 * - Key is stored in EncryptedSharedPreferences (AES-256-GCM for values, AES-256-SIV for keys)
 * - MasterKey is backed by Android Keystore (hardware-backed on supported devices)
 * - Key never leaves the device; no network transmission
 * - Passphrase is cached in memory after first retrieval to avoid repeated SharedPreferences reads
 *
 * Known limitation: The SQLCipher SupportOpenHelperFactory holds the passphrase as a final
 * byte[] field that is never zeroed. On rooted devices with memory dump capability, the key
 * could potentially be extracted from heap. This is a library-level constraint, not a design flaw.
 */
class DatabaseKeyManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences by lazy { createEncryptedSharedPreferences() }

    /** Cached passphrase — avoids repeated SharedPreferences reads */
    private val passphrase: ByteArray by lazy { retrievePassphrase() ?: initializePassphrase() }

    /** Cached factory instance — single allocation for app lifetime */
    private val factory: SupportOpenHelperFactory by lazy {
        SupportOpenHelperFactory(passphrase)
    }

    /**
     * Returns the SQLCipher SupportOpenHelperFactory for Room database encryption.
     *
     * If no key exists yet, generates and stores one automatically.
     *
     * @throws DatabaseEncryptionException if key generation or storage fails
     */
    fun getSupportFactory(): SupportOpenHelperFactory = factory

    /**
     * Creates EncryptedSharedPreferences with Android Keystore-backed MasterKey.
     *
     * @throws DatabaseEncryptionException if Keystore is unavailable or corrupted
     */
    private fun createEncryptedSharedPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SHARED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            Logger.e("Failed to initialize EncryptedSharedPreferences — Keystore unavailable", e, TAG)
            throw DatabaseEncryptionException("Android Keystore unavailable", e)
        } catch (e: java.io.IOException) {
            Logger.e("Failed to access SharedPreferences storage", e, TAG)
            throw DatabaseEncryptionException("SharedPreferences storage unavailable", e)
        }
    }

    /**
     * Retrieves the stored passphrase from EncryptedSharedPreferences.
     * Returns null if no key has been generated yet (first launch).
     */
    private fun retrievePassphrase(): ByteArray? {
        val passphraseString = sharedPreferences.getString(PREFS_KEY_PASSPHRASE, null)
        return passphraseString?.toByteArray(Charsets.ISO_8859_1)
    }

    /**
     * Generates a new 256-bit AES passphrase and stores it securely.
     * Returns the generated passphrase as a ByteArray.
     *
     * @throws DatabaseEncryptionException if key generation fails
     */
    private fun initializePassphrase(): ByteArray {
        return try {
            val passphrase = generatePassphrase()
            // Use apply() (async) instead of commit() (sync) — key is returned immediately,
            // write persists for next app launch. Safe because we return the key before DB opens.
            sharedPreferences.edit {
                putString(PREFS_KEY_PASSPHRASE, passphrase.toString(Charsets.ISO_8859_1))
            }
            passphrase
        } catch (e: Exception) {
            Logger.e("Failed to generate and persist database encryption key", e, TAG)
            throw DatabaseEncryptionException("Failed to initialize database encryption key", e)
        }
    }

    /**
     * Generates a cryptographically secure random 256-bit key using Android's KeyGenerator.
     * This key is used as the SQLCipher encryption passphrase.
     *
     * @throws DatabaseEncryptionException if KeyGenerator is unavailable
     */
    private fun generatePassphrase(): ByteArray {
        return try {
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM_AES)
            keyGenerator.init(KEY_SIZE)
            keyGenerator.generateKey().encoded
                ?: throw DatabaseEncryptionException("KeyGenerator produced null key")
        } catch (e: java.security.NoSuchAlgorithmException) {
            Logger.e("AES KeyGenerator not available — broken ROM?", e, TAG)
            throw DatabaseEncryptionException("AES algorithm not available", e)
        } catch (e: java.security.InvalidParameterException) {
            Logger.e("Invalid key size for AES KeyGenerator", e, TAG)
            throw DatabaseEncryptionException("Invalid AES key size", e)
        }
    }

    companion object {
        private const val TAG = "DatabaseKeyManager"
        private const val SHARED_PREFS_NAME = "com.p2p.meshify.db_crypto"
        private const val PREFS_KEY_PASSPHRASE = "db_passphrase"
        private const val ALGORITHM_AES = "AES"
        private const val KEY_SIZE = 256
    }
}

/**
 * Exception thrown when database encryption initialization fails.
 * Wraps underlying security exceptions with user-meaningful messages.
 */
class DatabaseEncryptionException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
