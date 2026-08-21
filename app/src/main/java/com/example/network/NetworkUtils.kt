package com.example.network

import com.example.model.ControlMode
import com.example.model.InterfaceType
import com.example.model.NetworkInterfaceInfo
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    /**
     * Extracts the IPv4 subnet prefix (e.g. "10.169.192." or "192.168.43." or "192.168.42.")
     * for any active interface matching the predicate.
     */
    fun getInterfacePrefix(intfNamePredicate: (String) -> Boolean): String? {
        return try {
            val nis = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in Collections.list(nis)) {
                if (!ni.isUp) continue
                val name = ni.name.lowercase()
                if (!intfNamePredicate(name)) continue
                val addr = ni.interfaceAddresses.firstOrNull { it.address is Inet4Address } ?: continue
                val mask = addr.networkPrefixLength.toInt()
                val prefixBytes = if (mask > 0) (mask / 8).coerceIn(1, 3) else 3
                val ip = addr.address.hostAddress ?: continue
                val parts = ip.split(".")
                if (parts.size >= prefixBytes) {
                    parts.take(prefixBytes).joinToString(".") + "."
                } else {
                    null
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun hotspotPrefix(): String? = getInterfacePrefix {
        it.startsWith("ap") || it.startsWith("swlan") || it.startsWith("softap") || it.startsWith("wlan1") || it.contains("tether")
    }

    fun usbPrefix(): String? = getInterfacePrefix {
        it.startsWith("rndis") || it.startsWith("usb") || it.startsWith("ncm")
    }

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
                            name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan") || name.startsWith("wlan1") || name.contains("tether") -> InterfaceType.WIFI_HOTSPOT
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
                            InterfaceType.LOOPBACK -> "Local Loopback (${intf.name})"
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
                    displayName = "Local Loopback (lo)",
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
            bytes >= 1_024 -> String.format("%d KB", bytes / 1_024)
            else -> "$bytes B"
        }
    }

    /**
     * Finds the concrete IP address to bind for the requested ControlMode.
     * USB mode binds only to USB tethering interfaces (rndis/usb/ncm).
     * Hotspot mode binds to AP/Hotspot interfaces.
     */
    fun getTargetBindIp(mode: ControlMode): String? {
        val interfaces = getAvailableNetworkInterfaces()
        return when (mode) {
            ControlMode.USB_SINGLE_USER -> {
                interfaces.firstOrNull { it.type == InterfaceType.USB_TETHERING }?.ipAddress
            }
            ControlMode.HOTSPOT_MULTI_USER -> {
                interfaces.firstOrNull { it.type == InterfaceType.WIFI_HOTSPOT }?.ipAddress
            }
        }
    }
}
