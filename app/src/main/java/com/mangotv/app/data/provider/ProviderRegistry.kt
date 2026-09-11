package com.mangotv.app.data.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Runtime registry of installed catalog providers/addons. The Settings >
 * Addons screen reads and mutates this registry as the user installs,
 * removes, enables or disables addons. It starts empty — Home shows its
 * empty state (with a prompt to install an addon) until the user adds one;
 * there's no built-in placeholder content.
 *
 * [providers] is a StateFlow rather than a plain getter so screens like Home
 * automatically pick up newly installed addons without needing to be told
 * to refresh.
 */
object ProviderRegistry {
    private val _providers = MutableStateFlow<List<CatalogProvider>>(emptyList())
    val providers: StateFlow<List<CatalogProvider>> = _providers.asStateFlow()

    fun activeProviders(): List<CatalogProvider> = _providers.value

    fun register(provider: CatalogProvider) {
        _providers.update { current ->
            if (current.any { it.id == provider.id }) {
                current.map { if (it.id == provider.id) provider else it }
            } else {
                current + provider
            }
        }
    }

    fun unregister(providerId: String) {
        _providers.update { current -> current.filterNot { it.id == providerId } }
    }

    // Replaces the entire set of registered providers in one shot -- a
    // single emission on [providers] instead of one per provider added or
    // removed. register()/unregister() above stay as they are for the
    // genuinely one-at-a-time case (a user installing or removing a single
    // addon from Settings), where one provider-list change is exactly the
    // correct trigger for one downstream re-fetch. This is for callers
    // reconciling several addons at once (cold-boot restore, a server sync
    // pull, wiping everything on sign-out): looping register()/unregister()
    // for each one there used to emit once per addon, and every one of
    // those emissions independently re-triggers Home's own catalog fetch --
    // for N addons that's N-1 extra fetches, each visible as the whole
    // Home screen flashing back to its loading state and reloading shortly
    // after the previous one finished.
    //
    // Skips the update entirely when the set of provider ids is unchanged
    // from what's already registered. CatalogProvider implementations (e.g.
    // StremioAddonProvider) are plain classes, not data classes, so a fresh
    // instance built from the exact same manifest never equals() the one
    // it's replacing -- without this check, every caller here still counts
    // as "different" to this StateFlow and re-emits even when nothing
    // actually changed, which was happening on every single cold boot for
    // a signed-in user: restoreFromDisk() registers the locally-cached
    // addon list, and moments later the account addon sync pull calls
    // applyRemote() again with a server-confirmed list that's usually
    // identical -- re-emitting anyway re-triggered Home's catalog fetch a
    // second time right after the first one had already finished, visible
    // as the whole screen dropping back to its loading state and reloading
    // for no reason a user could see.
    fun replaceAll(providers: List<CatalogProvider>) {
        _providers.update { current ->
            if (current.map { it.id }.toSet() == providers.map { it.id }.toSet()) {
                current
            } else {
                providers
            }
        }
    }
}
