package com.nethome.controller

import java.io.Serializable

enum class DeviceType {
    UNKNOWN, CAMERA, COMPUTER, ROUTER, PRINTER, SMART_PLUG, MEDIA, PHONE_OR_TABLET
}

data class Device(
    val ip: String,
    var mac: String? = null,
    var hostname: String? = null,
    var vendor: String? = null,
    var type: DeviceType = DeviceType.UNKNOWN,
    var openPorts: MutableList<Int> = mutableListOf(),
    var rttMs: Long = -1,
    var isOnline: Boolean = true,
    var friendlyName: String? = null
) : Serializable {

    val displayName: String
        get() = friendlyName ?: hostname ?: vendor ?: ip

    val subtitle: String
        get() {
            val parts = mutableListOf<String>()
            vendor?.let { parts.add(it) }
            mac?.let { parts.add(it) }
            if (rttMs >= 0) parts.add("${rttMs} ms")
            return if (parts.isEmpty()) ip else parts.joinToString("  •  ")
        }
}
