package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.InstalledAddon
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddonsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MangoTvApplication).container.addonRepository

    val installedAddons: StateFlow<List<InstalledAddon>> = repository.installedAddons

    fun setEnabled(manifestUrl: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(manifestUrl, enabled) }
    }

    fun remove(manifestUrl: String) {
        viewModelScope.launch { repository.removeAddon(manifestUrl) }
    }
}
