package com.mangotv.app.ui.loading

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.mangotv.app.MangoTvApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Backs the branded cold-boot loading screen (see LoadingScreen) -- its
 * only job is deciding when Home has something real AND ready-to-look-at
 * to show, so MangoNavHost can swap straight to a fully-populated Home
 * with no visible pop-in, instead of the loading screen handing off to
 * Home's own (separate, already-existing) loading skeleton.
 *
 * Deliberately doesn't reuse HomeViewModel's own state machine: sharing one
 * ViewModel instance across two different nav destinations would need
 * either a shared parent nav-graph scope or passing a full catalog through
 * nav arguments, both meaningfully more machinery than this needs. Reading
 * HomeCacheRepository twice (once here, once again when HomeViewModel
 * itself starts) is a second cheap disk read, not a second network fetch,
 * so the duplication this trades for is negligible.
 */
class LoadingViewModel(application: Application) : AndroidViewModel(application) {

    private val homeCacheRepository = (application as MangoTvApplication).container.homeCacheRepository
    private val imageLoader = application.imageLoader

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            val cached = homeCacheRepository.read()
            if (cached != null) {
                val (hero, sections) = cached
                val urlsToPreload = buildList {
                    hero.firstOrNull()?.let { first ->
                        first.backdropUrl?.let(::add)
                        first.logoUrl?.let(::add)
                    }
                    sections.firstOrNull()?.items?.take(PRELOAD_CARD_COUNT)?.forEach { item ->
                        item.posterUrl?.let(::add)
                    }
                }.distinct()

                // Bounded rather than open-ended: a slow or unreachable
                // poster host still shouldn't be able to hold the loading
                // screen up forever -- worst case here is no worse than
                // today, since anything not preloaded in time just loads in
                // normally once Home is shown, same as before this screen
                // existed. Deliberately generous rather than tight: the
                // whole point of this screen is to give the initial images
                // real room to finish before reveal, so a short ceiling
                // that cuts them off early just reintroduces the pop-in
                // this screen exists to avoid.
                withTimeoutOrNull(PRELOAD_TIMEOUT_MS) {
                    coroutineScope {
                        urlsToPreload.map { url ->
                            async { runCatching { imageLoader.execute(ImageRequest.Builder(application).data(url).build()) } }
                        }.awaitAll()
                    }
                }
            }
            // No cache (first-ever launch, or one older than
            // HomeCacheRepository's own staleness cap) means there's
            // nothing to preload against -- Home's existing loading
            // skeleton already handles that case correctly, so this screen
            // just hands off immediately rather than blocking on nothing.
            _isReady.value = true
        }
    }

    private companion object {
        const val PRELOAD_CARD_COUNT = 6
        const val PRELOAD_TIMEOUT_MS = 15000L
    }
}
