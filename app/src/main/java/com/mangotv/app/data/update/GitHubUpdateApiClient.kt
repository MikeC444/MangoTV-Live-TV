package com.mangotv.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Talks to GitHub's own public releases API directly -- this repo is public,
 * so no token is needed to read it (an unauthenticated request is subject to
 * GitHub's normal per-IP rate limit, which an occasional "check once on
 * launch" pattern stays comfortably under). A dedicated client rather than
 * AccountApiHttpClient (see its own doc): different host, different traffic
 * shape entirely.
 */
class GitHubUpdateApiClient(private val owner: String, private val repo: String) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLatestRelease(): GitHubReleaseDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("GitHub API error: HTTP ${response.code}")
            }
            json.decodeFromString(GitHubReleaseDto.serializer(), bodyString)
        }
    }
}
