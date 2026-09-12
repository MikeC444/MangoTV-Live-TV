package com.mangotv.app.data.update

/** A GitHub release that's a candidate to offer as an in-app update. */
data class AppUpdate(
    val tag: String,
    val notes: String,
    val assetUrl: String,
    val assetSizeBytes: Long?
)
