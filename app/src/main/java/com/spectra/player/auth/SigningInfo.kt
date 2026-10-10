package com.spectra.player.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** The values Spotify's dashboard must match for this build. Shown on the login screen. */
object SigningInfo {

    /** SHA-1 of the certificate this APK is signed with, as "AA:BB:..." (null if unavailable). */
    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures")
    fun sha1(context: Context): String? = try {
        val pm = context.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val s = info.signingInfo
            (if (s?.hasMultipleSigners() == true) s.apkContentsSigners else s?.signingCertificateHistory)
                ?.firstOrNull()?.toByteArray()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()?.toByteArray()
        }
        bytes?.let { fingerprint(it) }
    } catch (e: Exception) {
        null
    }

    internal fun fingerprint(cert: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(cert)
            .joinToString(":") { "%02X".format(it) }
}
