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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Reuses RowsBrowseUiState/RowsBrowseContent (the same shell Movies, TV
 * Shows and Genre Results already use) rather than a bespoke screen --
 * My List is just one more "stack of ContentRows" source, with the
 * distinction that its single row comes straight from MyListRepository
 * instead of a network fetch, so an Error state is never emitted here.
 */
class MyListViewModel(application: Application) : AndroidViewModel(application) {

    private val myListRepository = (application as MangoTvApplication).container.myListRepository

    val uiState: StateFlow<RowsBrowseUiState> = myListRepository.items
        .map { items -> RowsBrowseUiState.Loaded(sections = items.toSections()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RowsBrowseUiState.Loading)

    private fun List<SavedListItem>.toSections(): List<HomeSection> {
        if (isEmpty()) return emptyList()
        return listOf(
            HomeSection(
                id = "my_list",
                title = "My List",
                items = map { it.toContent() }
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
        providerId = providerId
    )
}
