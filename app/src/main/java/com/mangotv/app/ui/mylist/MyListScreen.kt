package com.mangotv.app.ui.mylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.browse.RowsBrowseContent

@Composable
fun MyListScreen(
    onNavigate: (String) -> Unit,
    viewModel: MyListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RowsBrowseContent(
        screenTitle = "My List",
        navLabel = "My List",
        uiState = uiState,
        onNavigate = onNavigate,
        onRetry = {},
        emptyMessage = "Your list is empty. Add titles from a Detail page or Home's featured title to see them here."
    )
}
