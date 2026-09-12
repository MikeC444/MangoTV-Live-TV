package com.mangotv.app.data.update

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** Downloads a release APK to local storage, reporting progress as it goes. */
class ApkDownloader {

    // A dedicated client, not AccountApiHttpClient (see its own doc) -- a
    // ~200MB download is a different traffic shape than short account-API
    // calls, and needs a read timeout generous enough to not mistake a slow
    // connection that's still making progress for a dead one.
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        url: String,
        destinationFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): Result<File> {
        return runCatching {
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) destinationFile.delete()

            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: error("Empty download body")
                val total = body.contentLength().takeIf { it > 0 }

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
            }
            destinationFile
        }
    }
}
