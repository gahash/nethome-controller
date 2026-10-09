package com.nethome.controller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nethome.controller.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DeviceAdapter
    private lateinit var scanner: NetworkScanner
    private var multicastLock: WifiManager.MulticastLock? = null

    private val permissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        scanner = NetworkScanner(applicationContext)
        adapter = DeviceAdapter { device ->
            val intent = Intent(this, DeviceDetailActivity::class.java)
            intent.putExtra(DeviceDetailActivity.EXTRA_DEVICE, device)
            startActivity(intent)
        }
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        binding.fabScan.setOnClickListener { requestPermissionsAndScan() }
        binding.swipeRefresh.setOnRefreshListener { requestPermissionsAndScan() }

        acquireMulticastLock()
        requestPermissionsAndScan()
    }

    private fun acquireMulticastLock() {
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("nethome-mlock").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    override fun onDestroy() {
        multicastLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun requestPermissionsAndScan() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), permissionRequestCode)
        }
        runScan()
    }

    private fun runScan() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.emptyView.visibility = android.view.View.GONE

        lifecycleScope.launch {
            val devices = scanner.scan { done, total ->
                runOnUiThread {
                    supportActionBar?.subtitle = "Scansione $done/$total"
                }
            }
            binding.progressBar.visibility = android.view.View.GONE
            binding.swipeRefresh.isRefreshing = false
            supportActionBar?.subtitle = null
            adapter.submitList(devices)
            binding.emptyView.visibility =
                if (devices.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
}
