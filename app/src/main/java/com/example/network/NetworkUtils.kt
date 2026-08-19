package com.example.network

import com.example.model.InterfaceType
import com.example.model.NetworkInterfaceInfo
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    fun getAvailableNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val result = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp) continue

                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        val name = intf.name.lowercase()
                        val type = when {
                            name.startsWith("rndis") || name.startsWith("usb") || name.startsWith("ncm") -> InterfaceType.USB_TETHERING
                            name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") || name.contains("tether") || (name.startsWith("wlan") && (ip.startsWith("192.168.43.") || ip.startsWith("192.168.44.") || ip.startsWith("192.168.49."))) -> InterfaceType.WIFI_HOTSPOT
                            name.startsWith("wlan") || name.startsWith("wifi") -> InterfaceType.WIFI
                            name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") -> InterfaceType.MOBILE
                            name.startsWith("eth") -> InterfaceType.ETHERNET
                            addr.isLoopbackAddress || name.startsWith("lo") -> InterfaceType.LOOPBACK
                            else -> InterfaceType.OTHER
                        }

                        val displayName = when (type) {
                            InterfaceType.USB_TETHERING -> "USB Tethering (${intf.name})"
                            InterfaceType.WIFI_HOTSPOT -> "Wi-Fi Hotspot (${intf.name})"
                            InterfaceType.WIFI -> "Wi-Fi (${intf.name})"
                            InterfaceType.MOBILE -> "Mobile Data (${intf.name})"
                            InterfaceType.LOOPBACK -> "Loopback / ADB (${intf.name})"
                            InterfaceType.ETHERNET -> "Ethernet (${intf.name})"
                            InterfaceType.OTHER -> intf.displayName ?: intf.name
                        }

                        result.add(
                            NetworkInterfaceInfo(
                                name = intf.name,
                                displayName = displayName,
                                ipAddress = ip,
                                type = type,
                                isPreferredForWindows = type == InterfaceType.USB_TETHERING || type == InterfaceType.WIFI_HOTSPOT || type == InterfaceType.LOOPBACK
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Ensure 127.0.0.1 is always present if not found
        if (result.none { it.ipAddress == "127.0.0.1" }) {
            result.add(
                NetworkInterfaceInfo(
                    name = "lo",
                    displayName = "Loopback / ADB (lo)",
                    ipAddress = "127.0.0.1",
                    type = InterfaceType.LOOPBACK,
                    isPreferredForWindows = true
                )
            )
        }

        // Sort so USB Tethering & Wi-Fi Hotspot & Wi-Fi & Loopback are prioritized
        return result.sortedWith(
            compareBy(
                { when (it.type) {
                    InterfaceType.USB_TETHERING -> 0
                    InterfaceType.WIFI_HOTSPOT -> 1
                    InterfaceType.WIFI -> 2
                    InterfaceType.LOOPBACK -> 3
                    InterfaceType.ETHERNET -> 4
                    InterfaceType.MOBILE -> 5
                    InterfaceType.OTHER -> 6
                } },
                { it.ipAddress }
            )
        )
    }

    fun formatSpeed(bytesPerSec: Long): String {
        val bitsPerSec = bytesPerSec * 8
        return when {
            bitsPerSec >= 1_000_000_000 -> String.format("%.1f Gbps", bitsPerSec / 1_000_000_000.0)
            bitsPerSec >= 1_000_000 -> String.format("%.1f Mbps", bitsPerSec / 1_000_000.0)
            bitsPerSec >= 1_000 -> String.format("%d kbps", bitsPerSec / 1_000)
            else -> "$bitsPerSec bps"
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1_024 -> String.format("%d kB", bytes / 1_024)
            else -> "$bytes B"
        }
    }
}
