package com.mangotv.app.ui.mylist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.provider.SavedListItem
import com.mangotv.app.ui.browse.RowsBrowseUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** My List's own filter, independent of RowsBrowseContent's generic filter bar plumbing. ALL mixes manually-added and watched titles together (the default) -- WATCHED narrows to only titles the player has marked watched. */
enum class MyListFilter(val label: String) {
    ALL("All"),
    WATCHED("Watched")
}

/**
 * Reuses RowsBrowseUiState/RowsBrowseContent (the same shell Movies, TV
 * Shows and Genre Results already use) rather than a bespoke screen --
 * My List is just one more "stack of ContentRows" source, with the
 * distinction that its single row comes straight from MyListRepository
 * instead of a network fetch, so an Error state is never emitted here.
 */
class MyListViewModel(application: Application) : AndroidViewModel(application) {

    private val myListRepository = (application as MangoTvApplication).container.myListRepository

    private val _selectedFilter = MutableStateFlow(MyListFilter.ALL)
    val selectedFilter: StateFlow<MyListFilter> = _selectedFilter.asStateFlow()

    val uiState: StateFlow<RowsBrowseUiState> = combine(myListRepository.items, _selectedFilter) { items, filter ->
        val filtered = if (filter == MyListFilter.WATCHED) items.filter { it.watched } else items
        RowsBrowseUiState.Loaded(sections = filtered.toSections())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RowsBrowseUiState.Loading)

    fun selectFilter(filter: MyListFilter) {
        _selectedFilter.value = filter
    }

    private fun List<SavedListItem>.toSections(): List<HomeSection> {
        if (isEmpty()) return emptyList()
        return listOf(
            HomeSection(
                id = "my_list",
                title = "My List",
                // Newest-added first. myListRepository.items itself is
                // maintained oldest-first throughout its whole lifecycle --
                // toggle()/markWatched()/toggleWatched() append a genuinely
                // new item to the end and never reorder an existing one on
                // update, and a server pull replaces the list wholesale with
                // the server's own `added_at ASC` order (see
                // WatchlistSyncRepository.pullFromServer/reconcile) -- so
                // reversing here is a pure display concern: every other
                // reader of myListRepository.items (savedIds, isInMyList,
                // the sync repository itself) still sees the untouched
                // oldest-first list.
                items = reversed().map { it.toContent() }
            )
        )
    }

    private fun SavedListItem.toContent(): Content = Content(
        id = id,
        type = type,
        title = title,
        description = "",
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        rating = rating,
        providerId = providerId,
        watched = watched
    )
}
