package com.nethome.controller

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nethome.controller.databinding.ActivityDeviceDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class DeviceDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEVICE = "extra_device"
    }

    private lateinit var binding: ActivityDeviceDetailBinding
    private lateinit var device: Device

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        device = intent.getSerializableExtra(EXTRA_DEVICE) as Device

        binding.detailIp.text = device.displayName
        binding.detailInfo.text = buildString {
            append(device.ip)
            device.mac?.let { append("  •  $it") }
            append("  •  Porte aperte: ${device.openPorts.joinToString(", ").ifEmpty { "nessuna" }}")
        }

        binding.btnOpenWeb.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://${device.ip}")))
        }

        binding.btnWol.setOnClickListener {
            val mac = device.mac
            if (mac == null) {
                showResult("MAC address sconosciuto: impossibile inviare Wake-on-LAN.")
            } else {
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { WakeOnLan.send(mac) }
                    showResult(if (ok) "Pacchetto Wake-on-LAN inviato a $mac" else "Invio fallito")
                }
            }
        }

        binding.btnToggleTasmota.setOnClickListener { callHttp("http://${device.ip}/cm?cmnd=Power%20Toggle") }
        binding.btnToggleShelly.setOnClickListener { callHttp("http://${device.ip}/relay/0?turn=toggle") }

        binding.btnOpenStream.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("rtsp://${device.ip}:554/")))
            } catch (_: Exception) {
                showResult("Nessuna app per RTSP installata. Installa VLC per aprire lo stream.")
            }
        }
    }

    private fun callHttp(url: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 2500
                    conn.readTimeout = 2500
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    val body = conn.inputStream.bufferedReader().readText().take(300)
                    conn.disconnect()
                    "HTTP $code: $body"
                } catch (e: Exception) {
                    "Errore: ${e.message}"
                }
            }
            showResult(result)
        }
    }

    private fun showResult(text: String) {
        binding.resultText.text = text
    }
}
