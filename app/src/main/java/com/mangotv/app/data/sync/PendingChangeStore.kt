package com.mangotv.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A small durable "hasn't successfully synced yet" outbox, one instance
 * per sync domain (Settings/Watchlist/ContinueWatching/Addons), each with
 * its own uniquely-named backing DataStore file (Milestone 10). Keyed by
 * that domain's own natural key (a manifestUrl, a content natural key, or
 * a fixed constant for a single-document domain like Settings) so a later
 * failure for the same key simply replaces the earlier pending entry --
 * this always holds each key's *latest* intended state, never a growing
 * log of superseded attempts.
 *
 * Built as a small generic utility rather than four hand-copied
 * DataStore+JSON blocks: with four real, already-shipped sync domains in
 * hand (Milestones 6-9), generalizing now reflects proven, not
 * speculative, shared shape -- exactly the bar every one of those
 * milestones' own changelogs set before building a shared abstraction.
 *
 * Uses PreferenceDataStoreFactory.create(...) directly rather than this
 * codebase's usual `by preferencesDataStore(name = ...)` property-delegate
 * sugar -- that delegate is meant for one static, file-scoped property
 * per store name; this class is instead constructed once per domain (as
 * a field inside that domain's own sync repository, itself an
 * AppContainer singleton), so calling the lower-level factory directly
 * in the constructor is the correct, supported way to get a
 * dynamically-named store without violating DataStore's "one live
 * instance per file path per process" rule.
 */
class PendingChangeStore<T>(
    context: Context,
    storeName: String,
    private val valueSerializer: KSerializer<T>
) {
    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile(storeName) }
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), valueSerializer)

    /** Records (or replaces) the latest pending payload for [naturalKey]. Read-modify-write happens inside DataStore's own edit{} transform, so concurrent put()/remove() calls for different keys can't race each other into a lost update. */
    suspend fun put(naturalKey: String, value: T) {
        dataStore.edit { prefs ->
            val current = decode(prefs[ENTRIES_KEY])
            prefs[ENTRIES_KEY] = encode(current + (naturalKey to value))
        }
    }

    /** Clears [naturalKey]'s pending entry -- call after a successful push. A no-op if it wasn't pending. */
    suspend fun remove(naturalKey: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[ENTRIES_KEY])
            if (naturalKey in current) {
                prefs[ENTRIES_KEY] = encode(current - naturalKey)
            }
        }
    }

    /** Every currently-pending entry, natural key to its latest payload -- what retryPending() iterates. */
    suspend fun all(): Map<String, T> = decode(dataStore.data.first()[ENTRIES_KEY])

    private fun decode(raw: String?): Map<String, T> {
        if (raw == null) return emptyMap()
        return runCatching { json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())
    }

    private fun encode(map: Map<String, T>): String = json.encodeToString(mapSerializer, map)

    companion object {
        private val ENTRIES_KEY = stringPreferencesKey("pending_entries_json")
    }
}
