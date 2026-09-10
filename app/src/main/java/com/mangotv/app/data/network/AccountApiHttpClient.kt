package com.mangotv.app.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One shared OkHttpClient for every account-API client (Auth, Settings,
 * Watchlist, PlaybackProgress, AddonSync) — Milestone 15. Each of the five
 * used to build its own `OkHttpClient.Builder()` with the exact same
 * timeouts, which meant five separate connection pools and five separate
 * dispatcher thread pools all talking to the same `API_BASE_URL` host —
 * pure overhead with no upside, and measurable on Fire TV Stick hardware's
 * limited RAM/CPU. Sharing one client here means a keep-alive connection
 * opened for, say, a settings pull can be reused by a watchlist push
 * moments later instead of every domain maintaining its own idle pool to
 * the identical host.
 *
 * Deliberately scoped to just these five: StremioAddonClient (arbitrary,
 * often slower self-hosted addon servers) and PlayerEngine's OkHttpDataSource
 * (streaming media, different timeout needs entirely) stay on their own
 * clients — those are genuinely different traffic with different
 * performance characteristics, not the same kind of duplication this object
 * fixes.
 */
object AccountApiHttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
