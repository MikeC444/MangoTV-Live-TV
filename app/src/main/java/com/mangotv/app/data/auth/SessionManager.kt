package com.mangotv.app.data.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_session")

/**
 * Persists the current Session, encrypted at rest via TokenCipher — same
 * DataStore+JSON pattern as every other repository in this app
 * (AddonRepository, MyListRepository, ...), with an encrypt/decrypt step
 * wrapped around the JSON string before it touches disk.
 *
 * [session] is eagerly loaded in init{} for reactive observers (mirroring
 * AddonRepository's own eager-construction rationale), but the auth gate
 * screen — which needs an authoritative answer at cold start, not
 * whatever this StateFlow's default happens to be before that load
 * finishes — calls [current] directly instead of racing it.
 */
class SessionManager(context: Context) {
    private val appContext = context.applicationContext

    // Lazy, not constructed inline: TokenCipher's init does real Android
    // Keystore/Tink work synchronously, and every actual use of `cipher`
    // below already runs inside withContext(Dispatchers.IO) — deferring
    // construction to first use means that work happens on that
    // background dispatcher too, never on whatever thread happens to
    // construct SessionManager itself (AppContainer, on the main thread,
    // during Application.onCreate()).
    private val cipher by lazy { TokenCipher(appContext) }
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    init {
        scope.launch { _session.value = readPersisted() }
    }

    suspend fun save(session: Session) = withContext(Dispatchers.IO) {
        val plaintext = json.encodeToString(session).toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.encrypt(plaintext)
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        appContext.sessionDataStore.edit { it[SESSION_KEY] = encoded }
        _session.value = session
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        appContext.sessionDataStore.edit { it.remove(SESSION_KEY) }
        _session.value = null
    }

    /** The authoritative current session, read fresh from disk every call. */
    suspend fun current(): Session? = withContext(Dispatchers.IO) { readPersisted() }

    private suspend fun readPersisted(): Session? {
        val encoded = appContext.sessionDataStore.data.first()[SESSION_KEY] ?: return null
        return runCatching {
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            val plaintext = cipher.decrypt(ciphertext)
            json.decodeFromString<Session>(plaintext.toString(Charsets.UTF_8))
        }.getOrNull()
    }

    companion object {
        private val SESSION_KEY = stringPreferencesKey("session_json_encrypted")
    }
}
