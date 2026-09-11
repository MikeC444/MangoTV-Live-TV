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
    fun replaceAll(providers: List<CatalogProvider>) {
        _providers.value = providers
    }
}
