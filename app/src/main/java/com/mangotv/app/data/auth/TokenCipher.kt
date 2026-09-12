package com.mangotv.app.data.auth

import android.content.Context
import android.security.keystore.KeyProperties
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.KeyStore

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
        awaitKeystoreReady()
        val keysetManager = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
        aead = keysetManager.keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(plaintext: ByteArray): ByteArray = aead.encrypt(plaintext, ASSOCIATED_DATA)

    fun decrypt(ciphertext: ByteArray): ByteArray = aead.decrypt(ciphertext, ASSOCIATED_DATA)

    /**
     * Blocks (briefly, and only when actually necessary) until the
     * AndroidKeyStore provider answers a real request, instead of handing
     * AndroidKeysetManager.Builder().build() above a keystore that isn't
     * responsive yet.
     *
     * This app is a LEANBACK_LAUNCHER (see the manifest) — on a Fire TV
     * set as the home screen, that means its process is started by the
     * system among the very first things after boot, which can race the
     * device's own AndroidKeyStore/keymaster service still warming up.
     * That race matters a lot more here than it would for an ordinary
     * app: AndroidKeysetManager reads this device's existing keyset by
     * decrypting it with the AndroidKeyStore master key named by
     * MASTER_KEY_URI, and if that read throws for *any* reason —
     * including the keystore service simply not being up yet, which
     * looks identical from here to the key genuinely not existing — Tink
     * doesn't propagate the failure. It logs a warning and silently
     * generates and persists a brand-new master key in its place. Any
     * session blob SessionManager encrypted with the old key (i.e. every
     * session persisted before this exact boot) is left unrecoverable
     * from that point on: readPersisted()'s decrypt call now runs against
     * the wrong key, throws, and (via its own runCatching) is treated as
     * "no session" — signed out, even though the actual bearer tokens on
     * disk were fine. That reproduces cleanly on every full power cycle
     * if this device's keystore service is consistently still starting
     * up at the exact moment this constructor first runs post-boot, and
     * would look, from a crash or any other unrelated process death that
     * forces a cold app relaunch, exactly like "the app randomly signs me
     * out."
     *
     * A cheap real request (loading the provider) either succeeds fast
     * (the overwhelmingly common case — this loop costs nothing then) or
     * fails fast while the service is still coming up, in which case a
     * few short retries give it a real chance to finish starting before
     * AndroidKeysetManager gets anywhere near the actual keyset.
     */
    private fun awaitKeystoreReady() {
        repeat(KEYSTORE_READY_MAX_ATTEMPTS) { attempt ->
            try {
                KeyStore.getInstance(KeyProperties.KEYSTORE_PROVIDER_ANDROID_KEYSTORE).load(null)
                return
            } catch (e: Exception) {
                Log.w(TAG, "AndroidKeyStore not ready yet (attempt ${attempt + 1}/$KEYSTORE_READY_MAX_ATTEMPTS)", e)
                if (attempt < KEYSTORE_READY_MAX_ATTEMPTS - 1) Thread.sleep(KEYSTORE_READY_RETRY_DELAY_MS)
            }
        }
        // Ran out of attempts -- fall through and let AndroidKeysetManager's
        // own build() surface whatever's actually wrong, same as before this
        // existed. This is just a best-effort head start, not a guarantee.
    }

    companion object {
        private const val TAG = "TokenCipher"
        private const val KEYSET_NAME = "mangotv_session_keyset"
        private const val PREF_FILE_NAME = "mangotv_session_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://mangotv_session_master_key"
        private val ASSOCIATED_DATA = ByteArray(0)
        private const val KEYSTORE_READY_MAX_ATTEMPTS = 5
        private const val KEYSTORE_READY_RETRY_DELAY_MS = 300L
    }
}
