package com.mangotv.app.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.updatePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_update_preferences")

/** Remembers which release tag the user has already dismissed, so a dismissed update doesn't reappear every launch -- a newer release still will. */
class UpdatePreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val ignoredTagKey = stringPreferencesKey("ignored_release_tag")

    suspend fun getIgnoredTag(): String? {
        return appContext.updatePreferencesDataStore.data.first()[ignoredTagKey]
    }

    suspend fun setIgnoredTag(tag: String) {
        appContext.updatePreferencesDataStore.edit { it[ignoredTagKey] = tag }
    }
}
