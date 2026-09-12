package com.mangotv.app.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.BuildConfig
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.update.ApkDownloader
import com.mangotv.app.data.update.ApkInstaller
import com.mangotv.app.data.update.AppUpdate
import com.mangotv.app.data.update.VersionUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateUiState(
    val update: AppUpdate? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float? = null,
    val downloadedApkPath: String? = null,
    val showBanner: Boolean = false,
    val showUnknownSourcesDialog: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Checks GitHub's own public releases API for this repo on launch (this
 * repo is public, so no token is needed) and offers to download+install a
 * newer release -- see ApkInstaller's own doc for why that still needs one
 * tap through the system installer no matter what, and TrailerPlayerScreen's
 * own kdoc / InAppYouTubeExtractor's for the general shape of "this is a
 * sideload-only app, so it can do things a Play Store listing couldn't."
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updateRepository = (application as MangoTvApplication).container.updateRepository
    private val updatePreferences = (application as MangoTvApplication).container.updatePreferencesRepository
    private val apkDownloader = ApkDownloader()

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        if (!BuildConfig.DEBUG) {
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            val dismissedTag = updatePreferences.getIgnoredTag()
            updateRepository.getLatestUpdate()
                .onSuccess { update ->
                    val remoteNewer = VersionUtils.isRemoteNewer(update.tag, BuildConfig.VERSION_NAME)
                    _uiState.update {
                        it.copy(
                            update = update.takeIf { remoteNewer },
                            showBanner = remoteNewer && dismissedTag != update.tag
                        )
                    }
                }
                .onFailure {
                    // A failed check (offline, GitHub rate limit, no releases
                    // published with an APK yet) just means no banner shows --
                    // never worth surfacing to the user, and never a reason
                    // to fail anything else in the app.
                }
        }
    }

    fun dismissBanner() {
        val tag = _uiState.value.update?.tag
        _uiState.update { it.copy(showBanner = false, showUnknownSourcesDialog = false) }
        if (tag != null) {
            viewModelScope.launch { updatePreferences.setIgnoredTag(tag) }
        }
    }

    fun dismissUnknownSourcesDialog() {
        _uiState.update { it.copy(showUnknownSourcesDialog = false) }
    }

    fun downloadUpdate() {
        val update = _uiState.value.update ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null) }

            // update.tag comes from GitHub, not something this app controls --
            // sanitized before it becomes part of a file path.
            val safeTag = update.tag.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destination = File(File(getApplication<Application>().cacheDir, "updates"), "mangotv-$safeTag.apk")
            val result = withContext(Dispatchers.IO) {
                apkDownloader.download(update.assetUrl, destination) { downloaded, total ->
                    val progress = if (total != null && total > 0) {
                        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
            }

            result
                .onSuccess { file ->
                    _uiState.update {
                        it.copy(isDownloading = false, downloadProgress = null, downloadedApkPath = file.absolutePath)
                    }
                    installUpdateOrRequestPermission()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = null,
                            downloadedApkPath = null,
                            errorMessage = error.message ?: "Download failed",
                            showBanner = true
                        )
                    }
                }
        }
    }

    fun installUpdateOrRequestPermission() {
        val context = getApplication<Application>()
        val apkPath = _uiState.value.downloadedApkPath ?: return
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            _uiState.update { it.copy(errorMessage = "The downloaded update is missing.", showBanner = true) }
            return
        }

        if (!ApkInstaller.canRequestPackageInstalls(context)) {
            _uiState.update { it.copy(showUnknownSourcesDialog = true, showBanner = true) }
            return
        }

        _uiState.update { it.copy(showUnknownSourcesDialog = false) }
        ApkInstaller.launchInstall(context, apkFile)
    }

    fun openUnknownSourcesSettings() {
        val context = getApplication<Application>()
        val primaryIntent = ApkInstaller.buildUnknownSourcesSettingsIntent(context)
        val launchedPrimary = primaryIntent != null && runCatching { context.startActivity(primaryIntent) }.isSuccess
        if (!launchedPrimary) {
            runCatching { context.startActivity(ApkInstaller.buildAppDetailsSettingsIntent(context)) }
        }
    }
}
