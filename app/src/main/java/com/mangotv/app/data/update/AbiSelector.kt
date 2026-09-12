package com.mangotv.app.data.update

import android.os.Build

/**
 * Picks the right APK asset off a GitHub release for this device, in case a
 * release ever ships more than one APK (e.g. one per ABI, plus a universal
 * fallback) -- a no-op that just returns the only asset when there's just one.
 */
internal object AbiSelector {

    private val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun chooseBestApkAsset(assets: List<GitHubAssetDto>): GitHubAssetDto? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null
        if (apkAssets.size == 1) return apkAssets.first()

        val supported = Build.SUPPORTED_ABIS?.toList().orEmpty()

        // Prefer an exact ABI match, in the device's own preference order.
        for (abi in supported) {
            apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }

        // Fall back to a universal APK if one is present.
        apkAssets.firstOrNull {
            val n = it.name.lowercase()
            n.contains("universal") || n.contains("all")
        }?.let { return it }

        // Otherwise prefer an asset that doesn't name a *different* ABI over one that does.
        return apkAssets.firstOrNull { asset -> knownAbis.none { asset.name.contains(it, ignoreCase = true) } }
            ?: apkAssets.first()
    }
}
