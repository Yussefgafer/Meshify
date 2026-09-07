package com.p2p.meshify.core.crypto

import android.content.Context
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KeysetManager
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.p2p.meshify.core.util.Logger
import java.io.ByteArrayOutputStream
import java.util.Base64

class DeviceKeysetManager(private val context: Context) {

    companion object {
        private const val KEYSET_PREF_NAME = "meshify_device_keyset"
        private const val KEYSET_PREF_KEY = "device_keyset"
        private const val MASTER_KEY_URI = "android-keystore://meshify_keyset_master_key"

        init {
            HybridConfig.register()
        }
    }

    /**
     * Produces the Android-backed keyset manager on real devices.
     * Returns null when the Android Keystore / SharedPreferences path is unavailable
     * (e.g. JVM unit tests), in which case [getKeysetHandle] falls back to an
     * in-memory [KeysetHandle].
     */
    private val keysetManager: AndroidKeysetManager? by lazy {
        try {
            AndroidKeysetManager.Builder()
                // withSharedPref(context, keysetName, prefFileName): the keyset is
                // stored under the key `keysetName` inside the SharedPreferences file
                // named `prefFileName`. Verified against tink-android-1.23.0.jar.
                .withSharedPref(context, KEYSET_PREF_NAME, KEYSET_PREF_KEY)
                .withKeyTemplate(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
        } catch (e: Exception) {
            Logger.e("DeviceKeysetManager → Keystore init failed, falling back to ephemeral keyset. " +
                "This device's identity will change on next cold start.", e, tag = "Crypto")
            null
        }
    }

    /** Consistent fallback keyset for test environments */
    private val defaultKeysetHandle: KeysetHandle = KeysetHandle.generateNew(
        HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM
    )

    /**
     * Returns a keyset handle suitable for encryption/decryption.
     * On real devices this is backed by AndroidKeystore via SharedPreferences.
     * In test environments it falls back to an in-memory keyset.
     */
    private fun getKeysetHandle(): KeysetHandle {
        return keysetManager?.keysetHandle ?: defaultKeysetHandle
    }

    /** Private keyset handle for local decryption */
    fun getPrivateKeysetHandle(): KeysetHandle = getKeysetHandle()

    /** Public keyset handle for sharing with peers */
    fun getPublicKeysetHandle(): KeysetHandle = getPrivateKeysetHandle().publicKeysetHandle

    /** Exports our public key as a Base64 encoded JSON string to put in Handshake */
    fun getPublicKeyBase64(): String {
        val outputStream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(
            getPublicKeysetHandle(),
            JsonKeysetWriter.withOutputStream(outputStream)
        )
        return Base64.getEncoder().encodeToString(outputStream.toByteArray())
    }
}
