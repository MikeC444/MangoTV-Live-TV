package com.mangotv.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.firstSyncDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_first_sync_state")

/**
 * A device-scoped (not account-scoped) flag: has this device ever
 * resolved the Milestone 11 first-login migration decision (SYNC/START
 * FRESH, or one of its no-prompt-needed auto-resolutions)? Once true,
 * FirstLoginMigrationCoordinator never asks again on this device, and
 * every domain's ordinary pull goes back to the plain "cloud is the
 * source of truth" rule every other sync repository already follows —
 * including Addons, whose pre-Milestone-11 pullFromServer() applied a
 * narrower version of this same "seed from local if the cloud is empty"
 * idea on *every* pull, not just the first one (see
 * AddonSyncRepository's own kdoc for why that was a real, if narrow, bug:
 * deliberately clearing every addon on one device could get silently
 * resurrected by a second already-signed-in device's next ordinary
 * launch).
 *
 * Deliberately device-scoped rather than per-account: this app's
 * sign-out (Milestone 5) doesn't clear local caches yet (that's
 * Milestone 12's job), so a second, different account signing in on the
 * same device would otherwise risk being asked to "sync" the *previous*
 * account's leftover local data into its own cloud account -- a real
 * cross-account leak. Once this flag is set, a second account's sign-in
 * just pulls and overwrites local state as normal (safe: the previous
 * account's residual data is discarded locally, never pushed anywhere),
 * rather than re-running the decision against data that doesn't actually
 * belong to the account now signing in. Milestone 12 will need to decide
 * whether logging out should reset this flag once it actually clears
 * local caches on logout -- at that point a genuinely different second
 * account would start from an empty local state anyway, making the
 * question largely moot either way.
 */
class FirstSyncState(context: Context) {
    private val appContext = context.applicationContext

    suspend fun isDone(): Boolean = appContext.firstSyncDataStore.data.first()[DONE_KEY] ?: false

    suspend fun markDone() {
        appContext.firstSyncDataStore.edit { it[DONE_KEY] = true }
    }

    /** Milestone 12's account switching: called on sign-out now that local caches are actually wiped too, so a genuinely different account's own first sign-in on this device gets evaluated fresh rather than skipped as "already resolved" for a question that a *previous* account, not this one, resolved. */
    suspend fun reset() {
        appContext.firstSyncDataStore.edit { it.remove(DONE_KEY) }
    }

    companion object {
        private val DONE_KEY = booleanPreferencesKey("first_sync_done")
    }
}
