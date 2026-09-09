package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.HomeRowPreferences
import com.mangotv.app.data.provider.ProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

sealed interface HomeRowsUiState {
    data object Loading : HomeRowsUiState
    data object NoAddons : HomeRowsUiState
    data class Loaded(val rows: List<HomeSection>) : HomeRowsUiState
}

/**
 * Backs Settings > Home Rows. Re-fetches every installed addon's rows the
 * same way HomeViewModel does (there's no shared cache between them) so the
 * list here matches what Home would show, titles included — this only runs
 * when the addon set changes, not on every visibility toggle or reorder, so
 * dragging a row doesn't wait on a network round-trip per step. Manual
 * ordering and hidden state are applied to [uiState]'s raw rows separately
 * (see [preferences]) as a cheap in-memory re-sort instead.
 */
class HomeRowsViewModel(application: Application) : AndroidViewModel(application) {

    private val homeRowPreferences = (application as MangoTvApplication).container.homeRowPreferencesRepository

    val preferences: StateFlow<HomeRowPreferences> = homeRowPreferences.preferences

    private val _uiState = MutableStateFlow<HomeRowsUiState>(HomeRowsUiState.Loading)
    val uiState: StateFlow<HomeRowsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ProviderRegistry.providers.collect { providers -> loadRows(providers) }
        }
    }

    private suspend fun loadRows(providers: List<CatalogProvider>) {
        if (providers.isEmpty()) {
            _uiState.value = HomeRowsUiState.NoAddons
            return
        }
        _uiState.value = HomeRowsUiState.Loading
        val rows = mutableListOf<HomeSection>()
        for (provider in providers) {
            // This screen just wants the final, complete row list (there's
            // no progressive reveal here) -- collecting getHomeSections()'s
            // batches to completion and flattening restores that.
            runCatching { provider.getHomeSections().toList().flatten() }.onSuccess { rows += it }
        }
        _uiState.value = HomeRowsUiState.Loaded(rows)
    }

    fun setRowVisible(rowId: String, visible: Boolean) {
        viewModelScope.launch { homeRowPreferences.setRowHidden(rowId, hidden = !visible) }
    }

    /** [displayOrder] is the full list of row ids exactly as Settings is currently rendering them. */
    fun moveRow(displayOrder: List<String>, rowId: String, delta: Int) {
        viewModelScope.launch { homeRowPreferences.moveRow(displayOrder, rowId, delta) }
    }
}
