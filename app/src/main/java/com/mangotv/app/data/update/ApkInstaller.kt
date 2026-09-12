package com.mangotv.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.mangotv.app.BuildConfig
import java.io.File

/** Hands a downloaded APK file to the system installer. */
object ApkInstaller {

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }
    }

    // Fallback for buildUnknownSourcesSettingsIntent: API < 26 (no per-app
    // unknown-sources screen exists pre-Oreo), or a device/OS build (a
    // customized Fire OS release, say) that doesn't resolve that intent the
    // same way stock Android does. ACTION_APPLICATION_DETAILS_SETTINGS has
    // existed since API 9 and is about as universally implemented as any
    // settings intent gets -- the per-app install-unknown-apps toggle is
    // still reachable from the App info screen it opens, just one more tap
    // away instead of a direct deep link.
    fun buildAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun launchInstall(context: Context, apkFile: File) {
        val authority = "${BuildConfig.APPLICATION_ID}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }
}
