package com.nethome.controller

/** Small offline lookup table for the most common OUI (MAC address vendor) prefixes seen on home networks. */
object OuiVendors {

    private val prefixes = mapOf(
        "3C:71:BF" to "Espressif (ESP/Tasmota/Shelly)",
        "AC:67:B2" to "Espressif (ESP/Tasmota/Shelly)",
        "24:6F:28" to "Espressif (ESP/Tasmota/Shelly)",
        "84:CC:A8" to "Espressif (ESP/Tasmota/Shelly)",
        "48:3F:DA" to "Espressif (ESP/Tasmota/Shelly)",
        "B4:E6:2D" to "Espressif (ESP/Tasmota/Shelly)",
        "CC:50:E3" to "Espressif (ESP/Tasmota/Shelly)",
        "34:98:7A" to "Shelly (Allterco)",
        "E8:DB:84" to "Shelly (Allterco)",
        "B0:B2:1C" to "TP-Link",
        "50:C7:BF" to "TP-Link",
        "AC:84:C6" to "TP-Link",
        "F4:F2:6D" to "TP-Link",
        "00:1A:11" to "Google",
        "F4:F5:D8" to "Google",
        "54:60:09" to "Google (Chromecast/Nest)",
        "94:EB:2C" to "Amazon (Echo/Fire)",
        "44:65:0D" to "Amazon (Echo/Fire)",
        "68:37:E9" to "Amazon (Echo/Fire)",
        "B8:27:EB" to "Raspberry Pi Foundation",
        "DC:A6:32" to "Raspberry Pi Foundation",
        "E4:5F:01" to "Raspberry Pi Foundation",
        "00:1B:63" to "Apple",
        "3C:15:C2" to "Apple",
        "A4:83:E7" to "Apple",
        "F0:18:98" to "Apple",
        "00:17:88" to "Philips Hue / Signify",
        "EC:B5:FA" to "Philips Hue / Signify",
        "00:04:20" to "Samsung",
        "5C:0A:5B" to "Samsung",
        "8C:79:F5" to "Samsung",
        "00:0C:29" to "VMware (virtuale)",
        "00:50:56" to "VMware (virtuale)",
        "00:1E:58" to "Hikvision (telecamera)",
        "4C:11:BF" to "Hikvision (telecamera)",
        "BC:AD:28" to "Hikvision (telecamera)",
        "C0:56:E3" to "Dahua (telecamera)",
        "9C:8E:CD" to "Dahua (telecamera)",
        "00:12:17" to "Cisco/Linksys",
        "00:1D:7E" to "Cisco/Linksys",
        "F8:1A:67" to "TP-Link (router)",
        "18:D6:C7" to "TP-Link (router)"
    )

    fun lookup(mac: String?): String? {
        if (mac == null || mac.length < 8) return null
        val prefix = mac.uppercase().substring(0, 8)
        return prefixes[prefix]
    }
}
