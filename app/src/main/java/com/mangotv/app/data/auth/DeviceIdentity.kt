package com.mangotv.app.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

private val Context.deviceIdentityDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_device_identity")

/**
 * A stable, locally-generated identifier for this install — not a
 * hardware serial/ANDROID_ID/MAC address (the project's own "no invasive
 * hardware fingerprinting" requirement), and not a secret, so unlike
 * SessionManager this is plain, unencrypted DataStore. Generated once and
 * reused for the lifetime of the install; a fresh generation only happens
 * if the app's storage is cleared, which the backend already treats as a
 * legitimate "new device" case (see devices' schema).
 */
class DeviceIdentity(context: Context) {
    private val appContext = context.applicationContext

    suspend fun getOrCreate(): String = withContext(Dispatchers.IO) {
        val existing = appContext.deviceIdentityDataStore.data.first()[DEVICE_ID_KEY]
        if (existing != null) return@withContext existing

        val generated = UUID.randomUUID().toString()
        appContext.deviceIdentityDataStore.edit { it[DEVICE_ID_KEY] = generated }
        generated
    }

    companion object {
        private val DEVICE_ID_KEY = stringPreferencesKey("device_identifier")
    }
}
