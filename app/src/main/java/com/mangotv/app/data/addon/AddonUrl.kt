package com.mangotv.app.data.addon

/**
 * Normalizes the many shapes an addon URL is shared in ("stremio://" deep
 * links, a bare host, a URL missing the trailing manifest.json) into a
 * fetchable https(s) manifest URL.
 */
object AddonUrl {

    fun normalizeManifestUrl(input: String): String {
        var url = input.trim()
        if (url.startsWith("stremio://")) {
            url = "https://" + url.removePrefix("stremio://")
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (!url.substringAfterLast('/').equals("manifest.json", ignoreCase = true)) {
            url = url.trimEnd('/') + "/manifest.json"
        }
        return url
    }

    /** The addon's resource base URL, i.e. the manifest URL with `manifest.json` stripped. */
    fun resourceBase(manifestUrl: String): String =
        manifestUrl.removeSuffix("manifest.json").trimEnd('/')
}
