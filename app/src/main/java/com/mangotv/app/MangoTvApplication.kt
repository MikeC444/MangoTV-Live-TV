package com.mangotv.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class MangoTvApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // Home's poster grid got a lot denser recently -- smaller poster cards
    // mean more tiles visible per row, and catalog rows are now fanned out
    // by genre into many more rows, so the total set of distinct on-screen
    // images grew well past what Coil's default ~20%-of-heap memory cache
    // comfortably holds on Fire TV Stick hardware. Once that cache starts
    // thrashing, images that already loaded a moment ago get evicted and
    // re-decoded as the user keeps scrolling, which read as stutter. A
    // larger cache plus a short crossfade (so anything that does arrive a
    // beat late fades in instead of popping) both target that directly.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(150)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.35)
                .build()
        }
        // A poster that scrolled out of the memory cache -- or wasn't seen
        // in a previous session -- decodes from disk instead of a full
        // network re-fetch on revisit. Bounded rather than a bare
        // percentage of free space, since that can be tiny on an entry-
        // level Fire TV Stick's storage or needlessly large on a Cube.
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizePercent(0.03)
                .minimumMaxSizeBytes(50L * 1024 * 1024)
                .maximumMaxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .build()
}
