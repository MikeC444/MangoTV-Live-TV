package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.addon.AddonPairingServer
import com.mangotv.app.data.model.InstalledAddon
import com.mangotv.app.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface AddAddonUiState {
    data object Idle : AddAddonUiState
    data class Installing(val url: String) : AddAddonUiState
    data class Success(val addon: InstalledAddon) : AddAddonUiState
    data class Error(val message: String) : AddAddonUiState
}

class AddAddonViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MangoTvApplication).container.addonRepository

    private val _uiState = MutableStateFlow<AddAddonUiState>(AddAddonUiState.Idle)
    val uiState: StateFlow<AddAddonUiState> = _uiState.asStateFlow()

    private val _pairingUrl = MutableStateFlow<String?>(null)
    val pairingUrl: StateFlow<String?> = _pairingUrl.asStateFlow()

    private var server: AddonPairingServer? = null
    private val incomingUrls = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        viewModelScope.launch {
            incomingUrls.collect { url -> installAddon(url) }
        }
    }

    fun startPairingServer() {
        if (server != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val ip = NetworkUtils.getLocalIpAddress()
            var boundPort: Int? = null
            for (candidatePort in PORT_RANGE) {
                try {
                    val candidateServer = AddonPairingServer(candidatePort) { url -> incomingUrls.tryEmit(url) }
                    candidateServer.start(SOCKET_READ_TIMEOUT_MS, false)
                    server = candidateServer
                    boundPort = candidatePort
                    break
                } catch (e: IOException) {
                    // Port already in use — try the next candidate.
                }
            }
            _pairingUrl.value = if (ip != null && boundPort != null) "http://$ip:$boundPort/add" else null
        }
    }

    fun stopPairingServer() {
        server?.stop()
        server = null
        _pairingUrl.value = null
    }

    fun installAddon(rawUrl: String) {
        viewModelScope.launch {
            _uiState.value = AddAddonUiState.Installing(rawUrl)
            repository.installAddon(rawUrl)
                .onSuccess { installed -> _uiState.value = AddAddonUiState.Success(installed) }
                .onFailure { error -> _uiState.value = AddAddonUiState.Error(error.message ?: "Couldn't install that addon.") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPairingServer()
    }

    companion object {
        private val PORT_RANGE = 8988..8992
        private const val SOCKET_READ_TIMEOUT_MS = 5000
    }
}
