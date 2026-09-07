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
}
