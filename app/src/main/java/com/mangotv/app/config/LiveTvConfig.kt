package com.mangotv.app.config

import com.mangotv.app.BuildConfig
import java.net.URLEncoder

/**
 * Every externally-configurable Live TV endpoint in one place, so nothing
 * else in the app hard-codes a URL. Values come from BuildConfig fields set
 * in app/build.gradle.kts, which read local.properties first (gitignored,
 * per-deployment) and fall back to public defaults -- see
 * gradle.properties and README.md.
 *
 * [apiBaseUrl] and [checkoutBaseUrl] default to empty: this app ships with
 * no real payment backend, and pretending otherwise would mean faking
 * entitlement checks locally, which the whole point of this architecture is
 * to avoid. Empty means "not configured yet" and is treated as its own
 * distinct state (see EntitlementState.NotConfigured) rather than silently
 * falling back to some other behavior.
 */
object LiveTvConfig {

    val iptvPlaylistUrl: String get() = BuildConfig.IPTV_PLAYLIST_URL.trim()
    val epgUrl: String get() = BuildConfig.EPG_URL.trim()
    val apiBaseUrl: String get() = BuildConfig.API_BASE_URL.trim().trimEnd('/')
    val checkoutBaseUrl: String get() = BuildConfig.PREMIUM_CHECKOUT_URL.trim()

    val isEpgConfigured: Boolean get() = epgUrl.isNotBlank()
    val isBackendConfigured: Boolean get() = apiBaseUrl.isNotBlank()
    val isCheckoutConfigured: Boolean get() = checkoutBaseUrl.isNotBlank()

    /**
     * Optional legal allow-list: a comma-separated list of M3U group-title
     * values (case-insensitive). Empty (the default) means every group in
     * the configured playlist is shown -- whoever deploys this app is
     * responsible for either pointing IPTV_PLAYLIST_URL at a source they
     * have the rights to redistribute in full, or narrowing it with this
     * list to only the categories they've cleared. This app must never
     * assume a public aggregate playlist like iptv-org's is safe to expose
     * wholesale in a commercial product.
     */
    val allowedGroups: Set<String> by lazy {
        BuildConfig.LIVE_TV_ALLOWED_GROUPS.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun isGroupAllowed(groupTitle: String?): Boolean {
        if (allowedGroups.isEmpty()) return true
        return groupTitle?.trim()?.lowercase() in allowedGroups
    }

    /**
     * The URL encoded into the premium screen's QR code: the operator's own
     * checkout page, tagged with this device's id and human-readable
     * pairing code so a successful payment on the phone can be associated
     * back with this TV. Returns null when no checkout page is configured,
     * so the UI can say so plainly instead of showing a QR code that leads
     * nowhere.
     */
    fun checkoutUrl(deviceId: String, pairingCode: String): String? {
        if (!isCheckoutConfigured) return null
        val separator = if (checkoutBaseUrl.contains("?")) "&" else "?"
        val encodedDevice = URLEncoder.encode(deviceId, "UTF-8")
        val encodedCode = URLEncoder.encode(pairingCode, "UTF-8")
        return "$checkoutBaseUrl${separator}device_id=$encodedDevice&pairing_code=$encodedCode"
    }
}
