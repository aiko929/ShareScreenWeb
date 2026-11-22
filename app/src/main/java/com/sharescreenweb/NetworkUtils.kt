package com.sharescreenweb

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    /** Interface name prefixes of cellular / VPN links that are not reachable from the LAN. */
    private val excludedPrefixes = listOf("rmnet", "ccmni", "pdp", "tun", "ppp", "dummy", "lo")

    /** Wi-Fi client, hotspot or ethernet interfaces - preferred. */
    private val preferredPrefixes = listOf("wlan", "swlan", "ap", "softap", "eth")

    /** Returns the device's IPv4 address in the local network (Wi-Fi / hotspot), or null. */
    fun localIpv4Address(): String? {
        val candidates = mutableListOf<Pair<String, String>>() // interface name to address
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                if (excludedPrefixes.any { name.startsWith(it) }) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val host = addr.hostAddress ?: continue
                        candidates += name to host
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        return candidates.firstOrNull { (n, _) -> preferredPrefixes.any { n.startsWith(it) } }?.second
            ?: candidates.firstOrNull()?.second
    }
}
