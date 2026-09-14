package com.mangotv.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.watchedBackfillDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_watched_backfill_state")

/**
 * Device-scoped (mirrors FirstSyncState's own reasoning exactly): has this
 * device already replayed this account's server watch history through
 * MyListRepository.markWatched() at least once? See
 * WatchlistSyncRepository.backfillWatchedFromHistoryIfNeeded() for what
 * that replay does and why it's cheap to redo in full if this never got
 * marked done.
 *
 * Reset on sign-out (AccountSwitchCoordinator), same as FirstSyncState: a
 * different account signing in on this device has its own, unrelated
 * watch history and needs its own fresh backfill pass, not to silently
 * skip one because a previous account already ran theirs on this device.
 */
class WatchedBackfillState(context: Context) {
    private val appContext = context.applicationContext

    suspend fun isDone(): Boolean = appContext.watchedBackfillDataStore.data.first()[DONE_KEY] ?: false

    suspend fun markDone() {
        appContext.watchedBackfillDataStore.edit { it[DONE_KEY] = true }
    }

    suspend fun reset() {
        appContext.watchedBackfillDataStore.edit { it.remove(DONE_KEY) }
    }

    companion object {
        private val DONE_KEY = booleanPreferencesKey("watched_backfill_done")
    }
}
