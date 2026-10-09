package com.nethome.controller

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {

    /** Sends a Wake-on-LAN magic packet to the broadcast address. Requires the device's MAC. */
    fun send(mac: String, broadcast: String = "255.255.255.255"): Boolean {
        return try {
            val macBytes = mac.split(":", "-").map { it.toInt(16).toByte() }.toByteArray()
            if (macBytes.size != 6) return false

            val packet = ByteArray(6 + 16 * 6)
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
            }

            DatagramSocket().use { socket ->
                val address = InetAddress.getByName(broadcast)
                socket.send(DatagramPacket(packet, packet.size, address, 9))
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
