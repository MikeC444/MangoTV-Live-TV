package com.mangotv.app.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /** Best-effort local LAN IPv4 address, so a phone on the same Wi-Fi can reach this device. */
    fun getLocalIpAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress }
            .firstOrNull()
            ?.hostAddress
    }.getOrNull()
}
