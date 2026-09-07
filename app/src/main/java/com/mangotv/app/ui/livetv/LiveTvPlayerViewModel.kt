package com.mangotv.app.ui.livetv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.data.livetv.NowNext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder

sealed interface LiveTvPlayerUiState {
    data object Loading : LiveTvPlayerUiState
    data class Ready(val channel: Channel) : LiveTvPlayerUiState
    data class Error(val message: String) : LiveTvPlayerUiState
}

/**
 * Looks the requested channel up in LiveTvRepository's already-loaded
 * in-memory catalog rather than re-fetching/re-parsing the playlist — the
 * only way to reach this screen is by selecting a channel that was already
 * showing on the browse screen, so the catalog is guaranteed to be loaded.
 */
class LiveTvPlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val liveTvRepository = (application as MangoTvApplication).container.liveTvRepository
    private val epgRepository = application.container.epgRepository

    private val channelId: String = URLDecoder.decode(savedStateHandle.get<String>("channelId").orEmpty(), "UTF-8")

    private val _uiState = MutableStateFlow<LiveTvPlayerUiState>(LiveTvPlayerUiState.Loading)
    val uiState: StateFlow<LiveTvPlayerUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        val channel = liveTvRepository.channelById(channelId)
        _uiState.value = if (channel != null) {
            LiveTvPlayerUiState.Ready(channel)
        } else {
            LiveTvPlayerUiState.Error("This channel is no longer available.")
        }
    }

    fun nowAndNext(channel: Channel): NowNext = epgRepository.nowAndNext(channel.tvgId)
}
