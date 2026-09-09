package com.mangotv.app.data.auth

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Encrypts the session blob before SessionManager writes it to disk. The
 * DataStore file itself is unencrypted, same as every other DataStore in
 * this app, but its ciphertext is meaningless without this device's
 * Android Keystore key — which matters concretely here because the
 * manifest sets `allowBackup="true"` (for ordinary user convenience, e.g.
 * restoring other app data on device replacement): without this, an
 * `adb backup` extraction would hand over a live bearer token in plain
 * text with no root required. A rooted-device file read is defended the
 * same way.
 *
 * Deliberately not androidx.security.crypto's EncryptedSharedPreferences
 * — that wrapper was deprecated in 2025. This talks to Tink directly,
 * which is still the actively maintained library underneath it, backed
 * by the same Android Keystore master key.
 */
class TokenCipher(context: Context) {
    private val aead: Aead

    init {
        AeadConfig.register()
        val keysetManager = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
        aead = keysetManager.keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(plaintext: ByteArray): ByteArray = aead.encrypt(plaintext, ASSOCIATED_DATA)

    fun decrypt(ciphertext: ByteArray): ByteArray = aead.decrypt(ciphertext, ASSOCIATED_DATA)

    companion object {
        private const val KEYSET_NAME = "mangotv_session_keyset"
        private const val PREF_FILE_NAME = "mangotv_session_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://mangotv_session_master_key"
        private val ASSOCIATED_DATA = ByteArray(0)
    }
}
