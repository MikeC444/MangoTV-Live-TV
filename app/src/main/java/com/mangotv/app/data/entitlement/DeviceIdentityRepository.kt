package com.mangotv.app.data.entitlement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import java.util.UUID

private val Context.deviceIdentityDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_live_tv_device_identity")

/**
 * A stable per-install device identity used to associate a purchase made on
 * a paired phone with this specific TV. Neither value generated here is a
 * secret on its own: the pairing code is short, never typed by hand (only
 * scanned inside the checkout QR code), and both only mean anything once the
 * backend has independently confirmed a real payment against them -- see
 * EntitlementRepository and docs/LIVE_TV_BACKEND.md.
 */
class DeviceIdentityRepository(private val context: Context) {

    private val appContext = context.applicationContext

    suspend fun getOrCreateDeviceId(): String = getOrCreate(DEVICE_ID_KEY) { UUID.randomUUID().toString() }

    suspend fun getOrCreatePairingCode(): String = getOrCreate(PAIRING_CODE_KEY) { generatePairingCode() }

    private suspend fun getOrCreate(key: Preferences.Key<String>, generate: () -> String): String {
        val existing = appContext.deviceIdentityDataStore.data.first()[key]
        if (existing != null) return existing
        val generated = generate()
        appContext.deviceIdentityDataStore.edit { it[key] = generated }
        return generated
    }

    private fun generatePairingCode(): String {
        val random = SecureRandom()
        val code = (1..4).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return "MANGO-$code"
    }

    companion object {
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private val PAIRING_CODE_KEY = stringPreferencesKey("pairing_code")

        // Excludes visually ambiguous characters (0/O, 1/I) since this is
        // read off a TV screen from across a room, not typed from a keyboard.
        private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
