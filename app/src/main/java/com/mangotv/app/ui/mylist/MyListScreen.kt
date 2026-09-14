package com.mangotv.app.ui.mylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.browse.RowsBrowseContent
import com.mangotv.app.ui.browse.RowsBrowseLayout

@Composable
fun MyListScreen(
    onNavigate: (String) -> Unit,
    viewModel: MyListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val filters = MyListFilter.entries
    RowsBrowseContent(
        screenTitle = "My List",
        navLabel = "My List",
        uiState = uiState,
        onNavigate = onNavigate,
        onRetry = {},
        layout = RowsBrowseLayout.GRID,
        emptyMessage = if (selectedFilter == MyListFilter.WATCHED) {
            "Nothing watched yet. Titles you finish will show up here."
        } else {
            "Your list is empty. Add titles from a Detail page or Home's featured title to see them here."
        },
        filterOptions = filters.map { it.label },
        selectedFilterIndex = filters.indexOf(selectedFilter),
        onFilterSelected = { index -> viewModel.selectFilter(filters[index]) }
    )
}
