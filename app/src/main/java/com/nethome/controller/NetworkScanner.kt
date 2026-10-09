package com.nethome.controller

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.net.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure local-network scanner: no cloud, no external server. Everything below talks
 * directly to devices on the same subnet using ping/TCP probes, ARP, SSDP and WS-Discovery.
 */
class NetworkScanner(private val context: Context) {

    // Ports checked per host to decide "online" + guess device type. Kept short/cheap.
    private val probePorts = intArrayOf(80, 443, 8080, 554, 8554, 9100, 22, 23, 5000, 1900, 8008, 8009)

    private val connectTimeoutMs = 300

    suspend fun scan(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Device> = withContext(Dispatchers.IO) {
        val hosts = localSubnetHosts()
        val devices = ConcurrentHashMap<String, Device>()
        var done = 0

        val semaphoreLimit = 48
        val chunks = hosts.chunked(semaphoreLimit)
        for (chunk in chunks) {
            val jobs = chunk.map { ip ->
                async {
                    val device = probeHost(ip)
                    if (device != null) devices[ip] = device
                    synchronized(this@NetworkScanner) {
                        done++
                        onProgress(done, hosts.size)
                    }
                }
            }
            jobs.awaitAll()
        }

        // Enrich with MAC/vendor from the kernel ARP cache (populated by the probes above).
        readArpTable().forEach { (ip, mac) ->
            devices[ip]?.let {
                it.mac = mac
                it.vendor = it.vendor ?: OuiVendors.lookup(mac)
            }
        }

        // Extra discovery passes that can surface devices even if the raw port probe missed them,
        // and that provide friendly names / camera hints.
        runCatching { ssdpDiscover(2000) }.getOrNull()?.forEach { (ip, name) ->
            val d = devices.getOrPut(ip) { Device(ip = ip) }
            d.friendlyName = d.friendlyName ?: name
        }
        runCatching { onvifDiscover(2000) }.getOrNull()?.forEach { ip ->
            devices.getOrPut(ip) { Device(ip = ip) }.apply {
                type = DeviceType.CAMERA
                if (!openPorts.contains(554)) openPorts.add(554)
            }
        }

        devices.values.map { classify(it) }.sortedBy { ipSortKey(it.ip) }
    }

    private fun ipSortKey(ip: String): Int =
        ip.split(".").getOrNull(3)?.toIntOrNull() ?: 0

    // ---------------------------------------------------------------------
    // Subnet enumeration
    // ---------------------------------------------------------------------

    private fun localSubnetHosts(): List<String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return emptyList()
        val linkProps = cm.getLinkProperties(network) ?: return emptyList()

        val ipv4 = linkProps.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return emptyList()
        val prefixLength = ipv4.prefixLength.coerceIn(24, 30) // cap sweep size for /8, /16 etc.
        val baseBytes = ipv4.address.address // 4 bytes

        val hostBits = 32 - prefixLength
        val hostCount = (1 shl hostBits) - 2 // exclude network + broadcast
        if (hostCount <= 0 || hostCount > 4094) return emptyList()

        val baseInt = bytesToInt(baseBytes)
        val mask = -1 shl hostBits
        val networkInt = baseInt and mask

        val result = ArrayList<String>(hostCount)
        for (i in 1..hostCount) {
            result.add(intToIp(networkInt + i))
        }
        return result
    }

    private fun bytesToInt(b: ByteArray): Int =
        ((b[0].toInt() and 0xFF) shl 24) or
        ((b[1].toInt() and 0xFF) shl 16) or
        ((b[2].toInt() and 0xFF) shl 8) or
        (b[3].toInt() and 0xFF)

    private fun intToIp(i: Int): String =
        "${(i shr 24) and 0xFF}.${(i shr 16) and 0xFF}.${(i shr 8) and 0xFF}.${i and 0xFF}"

    // ---------------------------------------------------------------------
    // Per-host probing
    // ---------------------------------------------------------------------

    private fun probeHost(ip: String): Device? {
        val openPorts = mutableListOf<Int>()
        var bestRtt = -1L

        for (port in probePorts) {
            val start = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), connectTimeoutMs)
                    openPorts.add(port)
                    val rtt = System.currentTimeMillis() - start
                    if (bestRtt < 0 || rtt < bestRtt) bestRtt = rtt
                }
            } catch (_: Exception) {
                // closed/filtered port, ignore
            }
        }

        if (openPorts.isEmpty()) {
            // Last resort: ICMP-ish reachability check (works occasionally without root on some OEMs).
            val reachable = try { InetAddress.getByName(ip).isReachable(200) } catch (_: Exception) { false }
            if (!reachable) return null
        }

        val hostname = try {
            val addr = InetAddress.getByName(ip)
            val canonical = addr.canonicalHostName
            if (canonical != ip) canonical else null
        } catch (_: Exception) { null }

        return Device(
            ip = ip,
            hostname = hostname,
            openPorts = openPorts,
            rttMs = bestRtt
        )
    }

    private fun classify(d: Device): Device {
        when {
            d.openPorts.contains(554) || d.openPorts.contains(8554) -> d.type = DeviceType.CAMERA
            d.openPorts.contains(9100) -> d.type = DeviceType.PRINTER
            d.openPorts.contains(8008) || d.openPorts.contains(8009) -> d.type = DeviceType.MEDIA
            d.ip.endsWith(".1") || d.ip.endsWith(".254") -> d.type = DeviceType.ROUTER
            d.openPorts.contains(22) || d.openPorts.contains(23) -> {
                if (d.type == DeviceType.UNKNOWN) d.type = DeviceType.COMPUTER
            }
        }
        if (d.vendor?.contains("Hikvision", true) == true || d.vendor?.contains("Dahua", true) == true) {
            d.type = DeviceType.CAMERA
        }
        return d
    }

    // ---------------------------------------------------------------------
    // ARP table (best-effort; readable without root on many Android versions for local traffic)
    // ---------------------------------------------------------------------

    private fun readArpTable(): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            val file = File("/proc/net/arp")
            if (!file.exists()) return map
            file.bufferedReader().use { reader: BufferedReader ->
                reader.readLine() // header
                reader.forEachLine { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (mac != "00:00:00:00:00:00") {
                            map[ip] = mac.uppercase()
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return map
    }

    // ---------------------------------------------------------------------
    // SSDP discovery (UPnP: smart TVs, media servers, routers, Roku, etc.)
    // ---------------------------------------------------------------------

    private fun ssdpDiscover(timeoutMs: Int): Map<String, String> {
        val results = HashMap<String, String>()
        val request = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: ssdp:all\r\n\r\n"

        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val group = InetAddress.getByName("239.255.255.250")
            val packet = DatagramPacket(request.toByteArray(), request.length, group, 1900)
            socket.send(packet)

            val buf = ByteArray(2048)
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end) {
                try {
                    val resp = DatagramPacket(buf, buf.size)
                    socket.receive(resp)
                    val text = String(resp.data, 0, resp.length)
                    val ip = resp.address.hostAddress ?: continue
                    val server = Regex("SERVER:\\s*(.+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()
                    if (server != null) results[ip] = server
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: Exception) { }
            }
        }
        return results
    }

    // ---------------------------------------------------------------------
    // ONVIF WS-Discovery (IP cameras)
    // ---------------------------------------------------------------------

    private fun onvifDiscover(timeoutMs: Int): List<String> {
        val cameras = mutableListOf<String>()
        val probe = """<?xml version="1.0" encoding="UTF-8"?>
            <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
                xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
                xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
                <e:Header>
                    <w:MessageID>uuid:${java.util.UUID.randomUUID()}</w:MessageID>
                    <w:To e:mustUnderstand="1">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
                    <w:Action e:mustUnderstand="1">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
                </e:Header>
                <e:Body>
                    <d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe>
                </e:Body>
            </e:Envelope>""".trimIndent()

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val group = InetAddress.getByName("239.255.255.250")
                val packet = DatagramPacket(probe.toByteArray(), probe.length, group, 3702)
                socket.send(packet)

                val buf = ByteArray(4096)
                val end = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < end) {
                    try {
                        val resp = DatagramPacket(buf, buf.size)
                        socket.receive(resp)
                        resp.address.hostAddress?.let { cameras.add(it) }
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
        return cameras
    }
}
